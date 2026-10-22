package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.AppLicense;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Enumeration;
import java.util.List;
import java.util.Collections;

/**
 * Handles licence activation, validation against a remote server, and
 * machine-binding to prevent copying the application to another PC.
 *
 * Primary storage: config.cnf (fast startup check).
 * Secondary storage: app_license DB table (audit trail).
 */
public class LicenseService {

    private final SessionFactory sf;
    private final SettingsService settings;

    public LicenseService(SessionFactory sf, SettingsService settings) {
        this.sf = sf;
        this.settings = settings;
    }

    // ── Machine fingerprint ────────────────────────────────────────────────────

    /**
     * Returns a machine identifier composed of hostname and first physical NIC
     * MAC address. Used to bind the licence to the activating machine.
     */
    public static String getMachineId() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append(InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            sb.append("UNKNOWN_HOST");
        }
        try {
            Enumeration<NetworkInterface> raw = NetworkInterface.getNetworkInterfaces();
            if (raw != null) {
                List<NetworkInterface> list = Collections.list(raw);
                // Sort by name so the same interface is always selected regardless
                // of the OS enumeration order (which varies with VPNs, reboots, etc.)
                list.sort(java.util.Comparator.comparing(NetworkInterface::getName));
                for (NetworkInterface ni : list) {
                    if (ni.isLoopback() || !ni.isUp()) continue;
                    byte[] mac = ni.getHardwareAddress();
                    if (mac != null && mac.length > 0) {
                        sb.append(":");
                        for (int i = 0; i < mac.length; i++) {
                            sb.append(String.format("%02X", mac[i]));
                            if (i < mac.length - 1) sb.append("-");
                        }
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
        return sb.toString();
    }

    // ── Licence state ──────────────────────────────────────────────────────────

    /**
     * Returns true when a valid licence exists in config.cnf AND the stored
     * machine ID matches the current machine.
     */
    public boolean isLicensed() {
        String key = settings.licenseKey();
        String storedMachineId = settings.licensedMachineId();
        if (key == null || key.isBlank()) return false;
        if (storedMachineId == null || storedMachineId.isBlank()) return false;
        return storedMachineId.equalsIgnoreCase(getMachineId());
    }

    // ── Activation ─────────────────────────────────────────────────────────────

    /**
     * Sends a POST request to the licence server and, on success, persists the
     * licence to config.cnf and the DB.
     *
     * POST body (JSON):
     * {
     *   "shopName":     "...",
     *   "key":          "...",
     *   "licensingDate":"YYYY-MM-DD",
     *   "machineId":    "HOSTNAME:AA-BB-CC-DD-EE-FF"
     * }
     *
     * Expected server response:
     * {"valid": true,  "message": "Licence activated"} — on success
     * {"valid": false, "message": "Reason"}            — on failure
     *
     * @return The success message from the server.
     * @throws Exception If activation fails (network error or server rejection).
     */
    public String activate(String serverUrl, String licenseKey, String shopName) throws Exception {
        if (serverUrl == null || serverUrl.isBlank()) throw new Exception("Licence server URL is required");
        if (licenseKey  == null || licenseKey.isBlank())  throw new Exception("Licence key is required");

        String machineId = getMachineId();
        String json = "{"
                + "\"shopName\":\"" + escJson(shopName) + "\","
                + "\"key\":\"" + escJson(licenseKey) + "\","
                + "\"licensingDate\":\"" + LocalDate.now() + "\","
                + "\"machineId\":\"" + escJson(machineId) + "\""
                + "}";

        String responseBody = sendPost(serverUrl, json);
        boolean valid = responseBody.contains("\"valid\":true")
                || responseBody.contains("\"valid\": true");
        String message = extractJsonString(responseBody, "message");

        if (!valid) {
            throw new Exception(message.isBlank()
                    ? "Invalid licence key — server rejected the request" : message);
        }

        // Persist to config.cnf first (fast startup check)
        settings.setLicenseServerUrl(serverUrl);
        settings.saveLicense(licenseKey, machineId);

        // Persist to DB (audit record)
        if (sf != null) {
            saveLicenseToDb(serverUrl, licenseKey, shopName, machineId);
        }

        return message.isBlank() ? "Licence activated successfully" : message;
    }

    // ── Connection test ────────────────────────────────────────────────────────

    /**
     * Returns true if the licence server URL is reachable (any HTTP response
     * below 500 counts as reachable).
     */
    public boolean testConnection(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) return false;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private void saveLicenseToDb(String serverUrl, String licenseKey,
                                 String shopName, String machineId) {
        try (Session session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            session.createMutationQuery("DELETE FROM AppLicense").executeUpdate();
            AppLicense lic = new AppLicense();
            lic.setLicenseKey(licenseKey);
            lic.setShopName(shopName);
            lic.setLicenseServerUrl(serverUrl);
            lic.setMachineId(machineId);
            lic.setActivatedDate(LocalDate.now());
            session.persist(lic);
            tx.commit();
        } catch (Exception ignored) {
            // DB save failure is non-critical; config.cnf is the primary store
        }
    }

    private String sendPost(String url, String json) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 500) {
            throw new Exception("Server error: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private String extractJsonString(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return "";
        idx = json.indexOf(":", idx + key.length());
        if (idx < 0) return "";
        idx = json.indexOf("\"", idx);
        if (idx < 0) return "";
        int end = json.indexOf("\"", idx + 1);
        if (end < 0) return "";
        return json.substring(idx + 1, end);
    }

    private static String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
