package com.olympus.system.hawkdeskpos.frontend.reports;

import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.ReportService;
import com.olympus.system.hawkdeskpos.service.SettingsService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Reports screen.
 * Period selector + stat bar + top-items bar chart + PDF export.
 */
public class ReportsPanel extends JPanel implements com.olympus.system.hawkdeskpos.frontend.components.Refreshable {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);

    private final ReportService reportService;

    private JLabel revenueVal, txVal, avgSaleVal, returnsVal;
    private JPanel chartsArea;
    private String currentPeriod = "TODAY";

    public ReportsPanel(ReportService reportService, SettingsService settingsService) {
        this.reportService = reportService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    public void refresh() { loadDataAsync(); }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        JLabel title = new JLabel("Reports & Analytics");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        topBar.add(title, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        JButton exportBtn = new JButton("Export PDF");
        exportBtn.setBackground(NAVY);
        exportBtn.setForeground(Color.WHITE);
        exportBtn.setOpaque(true);
        exportBtn.setBorderPainted(false);
        exportBtn.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "PDF export requires JasperReports integration.", "Export", JOptionPane.INFORMATION_MESSAGE));
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        btns.add(exportBtn);
        btns.add(back);
        topBar.add(btns, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(buildPeriodSelector());
        content.add(Box.createVerticalStrut(10));
        content.add(buildStatBar());
        content.add(Box.createVerticalStrut(10));

        chartsArea = new JPanel(new GridLayout(1, 2, 12, 0));
        chartsArea.setOpaque(false);
        chartsArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
        chartsArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(chartsArea);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        root.add(scroll, BorderLayout.CENTER);
        add(root);
        loadDataAsync();
    }

    private JPanel buildPeriodSelector() {
        CardPanel c = new CardPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        ((JPanel)c).setBorder(new EmptyBorder(4, 10, 4, 10));
        ((JPanel)c).setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        ((JPanel)c).setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel("Period:");
        lbl.setForeground(TEXT2);

        ButtonGroup bg = new ButtonGroup();
        String[] periods = {"TODAY", "THIS WEEK", "THIS MONTH"};
        for (String p : periods) {
            JToggleButton btn = new JToggleButton(p);
            btn.setFocusPainted(false);
            if ("TODAY".equals(p)) btn.setSelected(true);
            btn.addActionListener(e -> { currentPeriod = p; loadDataAsync(); });
            bg.add(btn);
            ((JPanel)c).add(btn);
        }
        ((JPanel)c).add(lbl, 0);
        return c;
    }

    private JPanel buildStatBar() {
        JPanel bar = new JPanel(new GridLayout(1, 4, 10, 0));
        bar.setOpaque(false);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);

        bar.add(statCard("Revenue",      "Rs. 0.00", GREEN));
        revenueVal = valueLabel(bar);
        bar.add(statCard("Transactions", "0",         NAVY));
        txVal      = valueLabel(bar);
        bar.add(statCard("Avg. Sale",    "Rs. 0.00",  new Color(0x4A, 0x14, 0x8C)));
        avgSaleVal = valueLabel(bar);
        bar.add(statCard("Returns",      "0",         new Color(0xC6, 0x28, 0x28)));
        returnsVal = valueLabel(bar);
        return bar;
    }

    private Date[] periodDates() {
        LocalDate today = LocalDate.now();
        return switch (currentPeriod) {
            case "THIS WEEK"  -> new Date[]{ toDate(today.minusDays(today.getDayOfWeek().getValue()-1)), toDate(today) };
            case "THIS MONTH" -> new Date[]{ toDate(today.withDayOfMonth(1)), toDate(today) };
            default           -> new Date[]{ toDate(today), toDate(today) }; // TODAY
        };
    }

    private Date toDate(LocalDate ld) {
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private void loadDataAsync() {
        Date[] range = periodDates();
        new SwingWorker<ReportService.PeriodStats, Void>() {
            List<ReportService.TopItem> topItems;
            @Override protected ReportService.PeriodStats doInBackground() {
                topItems = reportService.getTopItems(range[0], range[1], 8);
                return reportService.getStats(range[0], range[1]);
            }
            @Override protected void done() {
                try {
                    ReportService.PeriodStats stats = get();
                    revenueVal.setText(String.format("Rs. %.2f", stats.revenue()));
                    txVal.setText(String.valueOf(stats.transactions()));
                    avgSaleVal.setText(String.format("Rs. %.2f", stats.avgSale()));
                    returnsVal.setText(String.valueOf(stats.returns()));
                    buildCharts(topItems);
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void buildCharts(List<ReportService.TopItem> topItems) {
        chartsArea.removeAll();
        chartsArea.add(buildTopItemsChart(topItems));
        chartsArea.add(buildInfoCard());
        chartsArea.revalidate();
        chartsArea.repaint();
    }

    private JPanel buildTopItemsChart(List<ReportService.TopItem> topItems) {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));

        JLabel title = new JLabel("TOP SELLING ITEMS");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        ((JPanel)c).add(title, BorderLayout.NORTH);

        JPanel bars = new JPanel();
        bars.setOpaque(false);
        bars.setLayout(new BoxLayout(bars, BoxLayout.Y_AXIS));

        if (topItems != null && !topItems.isEmpty()) {
            int max = topItems.stream().mapToInt(ReportService.TopItem::qtySold).max().orElse(1);
            for (ReportService.TopItem item : topItems) {
                bars.add(buildBarRow(item.name(), item.qtySold(), max));
                bars.add(Box.createVerticalStrut(6));
            }
        } else {
            bars.add(new JLabel("  No sales data for selected period."));
        }

        ((JPanel)c).add(bars, BorderLayout.CENTER);
        return c;
    }

    private JPanel buildBarRow(String name, int value, int max) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel nameLbl = new JLabel(name);
        nameLbl.setPreferredSize(new Dimension(140, 0));
        nameLbl.setFont(nameLbl.getFont().deriveFont(12f));
        JProgressBar bar = new JProgressBar(0, max);
        bar.setValue(value);
        bar.setStringPainted(true);
        bar.setString(String.valueOf(value));
        bar.setForeground(NAVY);
        bar.setPreferredSize(new Dimension(0, 20));
        row.add(nameLbl, BorderLayout.WEST);
        row.add(bar,     BorderLayout.CENTER);
        return row;
    }

    private JPanel buildInfoCard() {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("QUICK ACTIONS");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setForeground(TEXT2);
        ((JPanel)c).add(title, BorderLayout.NORTH);

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        JButton viewGrn = new JButton("View GRN History");
        viewGrn.setAlignmentX(Component.LEFT_ALIGNMENT);
        viewGrn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        viewGrn.addActionListener(e -> Home.navigate(Home.CARD_GRN_HIST));

        JButton viewSales = new JButton("View Sales History");
        viewSales.setAlignmentX(Component.LEFT_ALIGNMENT);
        viewSales.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        viewSales.addActionListener(e -> Home.navigate(Home.CARD_HIST));

        inner.add(viewGrn);
        inner.add(Box.createVerticalStrut(8));
        inner.add(viewSales);

        ((JPanel)c).add(inner, BorderLayout.CENTER);
        return c;
    }

    private CardPanel statCard(String label, String value, Color valueColor) {
        CardPanel c = new CardPanel(new BorderLayout(0, 4));
        c.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(12f));
        l.setForeground(TEXT2);
        JLabel v = new JLabel(value);
        v.setFont(v.getFont().deriveFont(Font.BOLD, 18f));
        v.setForeground(valueColor);
        c.add(l, BorderLayout.NORTH);
        c.add(v, BorderLayout.CENTER);
        return c;
    }

    private JLabel valueLabel(JPanel parent) {
        CardPanel card = (CardPanel) parent.getComponent(parent.getComponentCount() - 1);
        return (JLabel) card.getComponent(1);
    }
}
