package com.olympus.system.hawkdeskpos.service;

import com.olympus.system.hawkdeskpos.db.dao.UomPreset;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.Collections;
import java.util.List;

public class UomService {

    private final SessionFactory sf;

    public UomService(SessionFactory sf) {
        this.sf = sf;
    }

    /** All preset names (primary unit names) sorted alphabetically. */
    public List<String> listPresetNames() {
        try (var session = sf.openSession()) {
            return session.createQuery(
                    "SELECT u.name FROM UomPreset u ORDER BY u.name", String.class).list();
        } catch (Exception e) {
            System.err.println("UomService.listPresetNames: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** All presets as entities (needed for table display and deletion). */
    public List<UomPreset> listPresets() {
        try (var session = sf.openSession()) {
            return session.createQuery(
                    "FROM UomPreset u ORDER BY u.name", UomPreset.class).list();
        } catch (Exception e) {
            System.err.println("UomService.listPresets: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Adds a new UoM preset with optional secondary unit.
     * @param primaryName     Primary unit name, e.g. "m" (required)
     * @param secondaryUnit   Secondary unit name, e.g. "cm" (null/blank = none)
     * @param conversionFactor How many secondary units per primary, e.g. 100.0 (ignored when no secondary)
     */
    public void addPreset(String primaryName, String secondaryUnit, double conversionFactor) {
        if (primaryName == null || primaryName.trim().isEmpty()) return;
        String primary   = primaryName.trim();
        String secondary = (secondaryUnit != null && !secondaryUnit.trim().isEmpty()) ? secondaryUnit.trim() : null;
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            Long count = session.createQuery(
                    "SELECT COUNT(u) FROM UomPreset u WHERE lower(u.name) = lower(:n)", Long.class)
                    .setParameter("n", primary).uniqueResult();
            if (count == null || count == 0) {
                UomPreset p = new UomPreset();
                p.setName(primary);
                p.setSecondaryUnit(secondary);
                p.setConversionFactor(secondary != null && conversionFactor > 0 ? conversionFactor : 1.0);
                session.persist(p);
            }
            tx.commit();
        } catch (Exception e) {
            System.err.println("UomService.addPreset: " + e.getMessage());
        }
    }

    /** Updates an existing preset. */
    public void updatePreset(int id, String primaryName, String secondaryUnit, double conversionFactor) {
        if (primaryName == null || primaryName.trim().isEmpty()) return;
        String primary   = primaryName.trim();
        String secondary = (secondaryUnit != null && !secondaryUnit.trim().isEmpty()) ? secondaryUnit.trim() : null;
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            UomPreset p = session.get(UomPreset.class, id);
            if (p != null) {
                p.setName(primary);
                p.setSecondaryUnit(secondary);
                p.setConversionFactor(secondary != null && conversionFactor > 0 ? conversionFactor : 1.0);
                session.merge(p);
            }
            tx.commit();
        } catch (Exception e) {
            System.err.println("UomService.updatePreset: " + e.getMessage());
        }
    }

    /** Deletes a preset by its id. */
    public void deletePreset(int id) {
        try (var session = sf.openSession()) {
            Transaction tx = session.beginTransaction();
            UomPreset p = session.get(UomPreset.class, id);
            if (p != null) session.remove(p);
            tx.commit();
        } catch (Exception e) {
            System.err.println("UomService.deletePreset: " + e.getMessage());
        }
    }
}
