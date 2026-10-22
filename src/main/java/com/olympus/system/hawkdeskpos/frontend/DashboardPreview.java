package com.olympus.system.hawkdeskpos.frontend;

import com.olympus.system.hawkdeskpos.dto.InvoiceDto;
import com.olympus.system.hawkdeskpos.dto.StockLevelDto;
import com.olympus.system.hawkdeskpos.service.ItemService;
import com.olympus.system.hawkdeskpos.service.ReportService;
import com.olympus.system.hawkdeskpos.service.SaleService;
import com.olympus.system.hawkdeskpos.service.SettingsService;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Simple preview application for DashboardPanel.
 * Creates dummy services and displays the panel in a JFrame.
 */
public class DashboardPreview {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Create dummy services
            ItemService itemService = new DummyItemService();
            SaleService saleService = new DummySaleService();
            SettingsService settings = new DummySettingsService();
            ReportService reportService = new DummyReportService();

            // Create the dashboard panel
            DashboardPanel dashboard = new DashboardPanel(itemService, saleService, settings, reportService);

            // Create a frame to hold the panel
            JFrame frame = new JFrame("Dashboard Preview");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(dashboard);
            frame.setSize(1200, 800);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    // Dummy implementations
    static class DummyItemService extends ItemService {
        public DummyItemService() {
            super(null, null); // No real dependencies needed for preview
        }

        @Override
        public List<StockLevelDto> listLowStock() {
            return Collections.emptyList(); // Return empty list for preview
        }
    }

    static class DummySaleService extends SaleService {
        public DummySaleService() {
            super(null, null);
        }

        @Override
        public List<InvoiceDto> listOverdueCreditInvoices() {
            return Collections.emptyList(); // Return empty list for preview
        }
    }

    static class DummySettingsService extends SettingsService {
        public DummySettingsService() {
            super(null);
        }
        // No methods overridden as not used
    }

    static class DummyReportService extends ReportService {
        public DummyReportService() {
            super(null);
        }

        @Override
        public PeriodStats getStats(Date from, Date to) {
            return new PeriodStats(0, 0, 0, 0, 0, 0); // Return zero stats for preview
        }
    }
}
