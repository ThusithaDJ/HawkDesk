package com.olympus.system.hawkdeskpos.db.dao;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "app_license")
public class AppLicense implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "license_key", nullable = false, length = 255)
    private String licenseKey;

    @Column(name = "shop_name", length = 255)
    private String shopName;

    @Column(name = "license_server_url", length = 500)
    private String licenseServerUrl;

    @Column(name = "machine_id", length = 500)
    private String machineId;

    @Column(name = "activated_date", nullable = false)
    private LocalDate activatedDate;

    public AppLicense() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLicenseKey() { return licenseKey; }
    public void setLicenseKey(String licenseKey) { this.licenseKey = licenseKey; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public String getLicenseServerUrl() { return licenseServerUrl; }
    public void setLicenseServerUrl(String licenseServerUrl) { this.licenseServerUrl = licenseServerUrl; }
    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }
    public LocalDate getActivatedDate() { return activatedDate; }
    public void setActivatedDate(LocalDate activatedDate) { this.activatedDate = activatedDate; }
}
