package com.olympus.system.hawkdeskpos.frontend.admin;

import com.olympus.system.hawkdeskpos.db.dao.UomPreset;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.FontManager;
import com.olympus.system.hawkdeskpos.frontend.components.Refreshable;
import com.olympus.system.hawkdeskpos.service.AuthService;
import com.olympus.system.hawkdeskpos.service.SettingsService;
import com.olympus.system.hawkdeskpos.service.UomService;
import com.olympus.system.hawkdeskpos.session.SessionContext;
import org.hibernate.SessionFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings screen.
 * Left sidebar nav + card panel for each section.
 */
public class SettingsPanel extends JPanel implements Refreshable {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);

    private final SettingsService     settings;
    private final AuthService         authService;
    private final SessionFactory      sf;
    private final UomService          uomService;
    private final com.olympus.system.hawkdeskpos.service.CashAccountService cashAccountService;

    private JPanel contentArea;
    private JPanel navPanel;
    private boolean dangerZoneAdded = false;

    // UoM section state
    private javax.swing.table.DefaultTableModel uomTableModel;
    private JTable                              uomTable;
    private java.util.List<UomPreset>           uomPresets = new java.util.ArrayList<>();
    private static final String[] SECTIONS = {
            "Shop Details", "Sales & Billing", "Receipt & Printing",
            "Stock & Inventory", "Units of Measure", "Categories & Brands",
            "Finance & Accounts", "UI Appearance", "Backup", "User Management", "About"
    };

    // Shop details fields
    private JTextField shopNameField, shopAddressField, shopPhoneField, currencyField;

    // Sales fields
    private JCheckBox taxInclusiveCheck;
    private JTextField taxRateField, invoicePrefixField;

    // Returns fields
    private JCheckBox enableReturnsCheck;
    private JSpinner  returnPeriodSpinner;

    // Stock fields
    private JTextField defaultMinLevelField;
    private JTextField batchPatternField;

    // UI appearance
    private int selectedFontSize;

    public SettingsPanel(SettingsService settings, AuthService authService, SessionFactory sf) {
        this(settings, authService, sf, null);
    }

    public SettingsPanel(SettingsService settings, AuthService authService, SessionFactory sf,
                         com.olympus.system.hawkdeskpos.service.CashAccountService cashAccountService) {
        this.settings            = settings;
        this.authService         = authService;
        this.sf                  = sf;
        this.uomService          = sf != null ? new UomService(sf) : null;
        this.cashAccountService  = cashAccountService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    public SettingsPanel(SettingsService settings) {
        this(settings, null, null, null);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_DASH));
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Split: sidebar + content
        JPanel split = new JPanel(new BorderLayout(12, 0));
        split.setOpaque(false);

        // Left sidebar nav
        CardPanel sidebar = new CardPanel(new BorderLayout(0, 0));
        ((JPanel)sidebar).setPreferredSize(new Dimension(200, 0));
        ((JPanel)sidebar).setBorder(new EmptyBorder(0, 0, 0, 0));

        navPanel = new JPanel();
        navPanel.setOpaque(false);
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.Y_AXIS));
        for (String section : SECTIONS) {
            navPanel.add(buildNavItem(section));
        }
        ((JPanel)sidebar).add(navPanel, BorderLayout.NORTH);
        split.add(sidebar, BorderLayout.WEST);

        // Right: content area
        contentArea = new JPanel(new BorderLayout());
        contentArea.setOpaque(false);
        split.add(contentArea, BorderLayout.CENTER);

        root.add(split, BorderLayout.CENTER);
        add(root);

        showSection("Shop Details");
    }

    @Override
    public void refresh() {
        if (!dangerZoneAdded && authService != null) {
            String role = SessionContext.current() != null
                    ? SessionContext.current().getEmployee().role() : "";
            if ("OWNER".equals(role)) {
                navPanel.add(buildDangerZoneNavItem());
                navPanel.revalidate();
                navPanel.repaint();
                dangerZoneAdded = true;
            }
        }
    }

    private JPanel buildNavItem(String section) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE2, 0xE5, 0xEA)),
                new EmptyBorder(12, 16, 12, 16)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel l = new JLabel(section);
        l.setFont(l.getFont().deriveFont(13f));
        l.setForeground(new Color(0x18, 0x5F, 0xA5));
        row.add(l, BorderLayout.WEST);

        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                showSection(section);
            }
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                row.setBackground(new Color(0xF0, 0xF2, 0xF5));
                row.setOpaque(true);
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                row.setOpaque(false);
            }
        });
        return row;
    }

    private void showSection(String section) {
        contentArea.removeAll();
        JPanel content = switch (section) {
            case "Shop Details"      -> buildShopDetailsSection();
            case "Sales & Billing"   -> buildSalesBillingSection();
            case "Stock & Inventory" -> buildStockSection();
            case "Units of Measure"  -> buildUomSection();
            case "Finance & Accounts"-> buildFinanceSection();
            case "UI Appearance"     -> buildAppearanceSection();
            case "Categories & Brands" -> { Home.navigate(Home.CARD_CATS);   yield new JPanel(); }
            case "Backup"              -> { Home.navigate(Home.CARD_BACKUP); yield new JPanel(); }
            case "User Management"     -> { Home.navigate(Home.CARD_USERS);  yield new JPanel(); }
            case "About"               -> buildAboutSection();
            case "Danger Zone"         -> buildDangerZoneSection();
            default                    -> buildShopDetailsSection();
        };
        contentArea.add(content, BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
    }

    private JPanel buildShopDetailsSection() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        CardPanel c = sectionCard("SHOP DETAILS");
        JPanel grid = new JPanel(new GridLayout(2, 2, 10, 10));
        grid.setOpaque(false);

        shopNameField    = tf(settings.shopName());
        shopAddressField = tf(settings.shopAddress());
        shopPhoneField   = tf(settings.shopPhone());
        currencyField    = tf(settings.currency());

        grid.add(labeled("Shop Name",    shopNameField));
        grid.add(labeled("Address",      shopAddressField));
        grid.add(labeled("Phone",        shopPhoneField));
        grid.add(labeled("Currency Code",currencyField));

        ((JPanel)c).add(grid, BorderLayout.CENTER);
        p.add(c);
        p.add(Box.createVerticalStrut(12));
        p.add(saveRow(() -> {
            String name = shopNameField.getText().trim();
            settings.set("ShopName",    name);
            settings.set("ShopAddress", shopAddressField.getText().trim());
            settings.set("ShopPhone",   shopPhoneField.getText().trim());
            settings.set("Currency",    currencyField.getText().trim());
            Home.refreshNavBarShopName(name);
            showSavedMsg();
        }));
        return p;
    }

    private JPanel buildSalesBillingSection() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // ── Sales & Billing card ──────────────────────────────────────────────
        CardPanel c = sectionCard("SALES & BILLING");
        JPanel grid = new JPanel(new GridLayout(2, 2, 10, 10));
        grid.setOpaque(false);

        taxRateField      = tf(settings.get("taxRate", "0"));
        invoicePrefixField= tf(settings.get("invoicePrefix", "INV"));
        taxInclusiveCheck = new JCheckBox("Tax Inclusive Pricing", "true".equals(settings.get("taxInclusive", "false")));

        grid.add(labeled("Default Tax Rate (%)", taxRateField));
        grid.add(labeled("Invoice Prefix",       invoicePrefixField));
        grid.add(labeled("",                     taxInclusiveCheck));

        ((JPanel)c).add(grid, BorderLayout.CENTER);
        p.add(c);
        p.add(Box.createVerticalStrut(12));

        // ── Returns card ──────────────────────────────────────────────────────
        CardPanel rc = sectionCard("RETURNS");
        JPanel rgrid = new JPanel(new GridLayout(1, 3, 10, 0));
        rgrid.setOpaque(false);

        enableReturnsCheck = new JCheckBox("Enable Returns", settings.returnsEnabled());
        enableReturnsCheck.setFont(enableReturnsCheck.getFont().deriveFont(13f));

        returnPeriodSpinner = new JSpinner(new SpinnerNumberModel(
                settings.returnPeriodDays(), 1, 365, 1));
        returnPeriodSpinner.setPreferredSize(new Dimension(80, 28));

        rgrid.add(labeled("", enableReturnsCheck));
        rgrid.add(labeled("Return Period (days)", returnPeriodSpinner));
        rgrid.add(new JPanel()); // spacer

        ((JPanel)rc).add(rgrid, BorderLayout.CENTER);
        p.add(rc);
        p.add(Box.createVerticalStrut(12));

        p.add(saveRow(() -> {
            settings.set("taxRate",          taxRateField.getText().trim());
            settings.set("invoicePrefix",    invoicePrefixField.getText().trim());
            settings.set("taxInclusive",     String.valueOf(taxInclusiveCheck.isSelected()));
            settings.set("ReturnsEnabled",   String.valueOf(enableReturnsCheck.isSelected()));
            settings.set("ReturnPeriodDays", String.valueOf(returnPeriodSpinner.getValue()));
            showSavedMsg();
        }));
        return p;
    }

    private JPanel buildStockSection() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        CardPanel c = sectionCard("STOCK & INVENTORY");
        JPanel grid = new JPanel(new GridLayout(1, 4, 10, 0));
        grid.setOpaque(false);

        defaultMinLevelField = tf(settings.get("defaultMinLevel", "5"));
        batchPatternField    = tf(settings.batchNumberPattern());
        JLabel patternHint = new JLabel(
                "<html><small>Placeholders: {YYYY} = year, {NNN} = sequence</small></html>");
        patternHint.setForeground(new Color(0x5A, 0x60, 0x70));
        grid.add(labeled("Default Min Level",      defaultMinLevelField));
        grid.add(labeled("Batch Number Pattern",   batchPatternField));

        ((JPanel)c).add(grid, BorderLayout.CENTER);
        ((JPanel)c).add(patternHint, BorderLayout.SOUTH);
        p.add(c);
        p.add(Box.createVerticalStrut(12));
        p.add(saveRow(() -> {
            settings.set("defaultMinLevel",    defaultMinLevelField.getText().trim());
            settings.set("BatchNumberPattern", batchPatternField.getText().trim());
            showSavedMsg();
        }));
        return p;
    }

    private JPanel buildFinanceSection() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        CardPanel c = sectionCard("DEFAULT ACCOUNT ROUTING");

        JPanel hint = new JPanel(new BorderLayout());
        hint.setOpaque(false);
        JLabel hintLbl = new JLabel(
                "<html><small>Configure which cash account receives income from Sales " +
                "or is debited for GRN stock purchases.<br>" +
                "Leave as 'Cash Drawer' to use the default behaviour.</small></html>");
        hintLbl.setForeground(TEXT2);
        hintLbl.setBorder(new EmptyBorder(0, 0, 8, 0));
        hint.add(hintLbl, BorderLayout.CENTER);
        ((JPanel)c).add(hint, BorderLayout.NORTH);

        // Load accounts synchronously (list is small)
        java.util.List<com.olympus.system.hawkdeskpos.db.dao.CashAccount> accounts =
                cashAccountService != null ? cashAccountService.listAccounts()
                        : java.util.Collections.emptyList();

        String[] accountNames = new String[accounts.size() + 1];
        Long[]   accountIds   = new Long[accounts.size() + 1];
        accountNames[0] = "Cash Drawer (default)";
        accountIds[0]   = null;
        for (int i = 0; i < accounts.size(); i++) {
            accountNames[i + 1] = accounts.get(i).getName() + " (" + accounts.get(i).typeLabel() + ")";
            accountIds[i + 1]   = accounts.get(i).getId();
        }

        JComboBox<String> saleCombo = new JComboBox<>(accountNames);
        JComboBox<String> grnCombo  = new JComboBox<>(accountNames);

        // Pre-select current settings
        Long curSaleId = settings.defaultSaleAccountId();
        Long curGrnId  = settings.defaultGrnAccountId();
        for (int i = 1; i < accountIds.length; i++) {
            if (accountIds[i] != null && accountIds[i].equals(curSaleId)) saleCombo.setSelectedIndex(i);
            if (accountIds[i] != null && accountIds[i].equals(curGrnId))  grnCombo.setSelectedIndex(i);
        }

        JPanel grid = new JPanel(new GridLayout(2, 2, 10, 10));
        grid.setOpaque(false);
        grid.add(labeled("Sales Income Account", saleCombo));
        grid.add(labeled("GRN Payment Account",  grnCombo));
        ((JPanel)c).add(grid, BorderLayout.CENTER);
        p.add(c);
        p.add(Box.createVerticalStrut(12));
        p.add(saveRow(() -> {
            int si = saleCombo.getSelectedIndex();
            int gi = grnCombo.getSelectedIndex();
            settings.setDefaultSaleAccountId(si >= 0 && si < accountIds.length ? accountIds[si] : null);
            settings.setDefaultGrnAccountId(gi >= 0 && gi < accountIds.length  ? accountIds[gi] : null);
            showSavedMsg();
        }));

        JPanel linkRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        linkRow.setOpaque(false);
        JButton manageBtn = new JButton("Manage Cash Accounts →");
        manageBtn.setForeground(new Color(0x18, 0x5F, 0xA5));
        manageBtn.setBorderPainted(false); manageBtn.setContentAreaFilled(false);
        manageBtn.setFocusPainted(false);
        manageBtn.addActionListener(e -> Home.navigate(Home.CARD_CASH_ACCOUNTS));
        linkRow.add(manageBtn);
        p.add(linkRow);
        return p;
    }

    private JPanel buildUomSection() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel)c).setMaximumSize(new Dimension(Integer.MAX_VALUE, 520));
        ((JPanel)c).setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel t = new JLabel("UNITS OF MEASURE");
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        ((JPanel)c).add(t, BorderLayout.NORTH);

        JLabel hint = new JLabel(
                "<html><small>Register primary units and their optional secondary units with conversion rates.</small></html>");
        hint.setForeground(TEXT2);
        hint.setBorder(new EmptyBorder(0, 0, 6, 0));

        // Table: Primary Unit | Secondary Unit | Conversion
        uomTableModel = new javax.swing.table.DefaultTableModel(
                new String[]{"Primary Unit", "Secondary Unit", "1 Primary = ? Secondary"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        uomTable = new JTable(uomTableModel);
        uomTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        uomTable.setRowHeight(26);
        uomTable.getTableHeader().setReorderingAllowed(false);
        uomTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        uomTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        uomTable.getColumnModel().getColumn(2).setPreferredWidth(160);
        JScrollPane scroll = new JScrollPane(uomTable);
        scroll.setPreferredSize(new Dimension(0, 130));

        // Input form: 3 fields
        JTextField primaryField   = new JTextField();
        primaryField.setToolTipText("Primary unit name, e.g. M");
        JTextField secondaryField = new JTextField();
        secondaryField.setToolTipText("Optional secondary unit, e.g. CM (leave blank for none)");
        JTextField factorField    = new JTextField("1.0");
        factorField.setToolTipText("How many secondary units equal one primary unit, e.g. 100");

        JPanel inputGrid = new JPanel(new GridLayout(1, 3, 8, 0));
        inputGrid.setOpaque(false);
        inputGrid.add(labeled("Primary Unit *",            primaryField));
        inputGrid.add(labeled("Secondary Unit (optional)", secondaryField));
        inputGrid.add(labeled("1 Primary = ? Secondary",   factorField));

        JButton addBtn = new JButton("+ Add");
        addBtn.setBackground(NAVY);
        addBtn.setForeground(Color.WHITE);
        addBtn.setOpaque(true);
        addBtn.setBorderPainted(false);
        addBtn.addActionListener(e -> {
            if (uomService == null) return;
            String primary = primaryField.getText().trim();
            if (primary.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter a primary unit name.", "Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String secondary = secondaryField.getText().trim();
            double factor = 1.0;
            try { factor = Double.parseDouble(factorField.getText().trim()); } catch (NumberFormatException ignored) {}
            final double ff = Math.max(0.001, factor);
            final String fs = secondary.isEmpty() ? null : secondary;
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() { uomService.addPreset(primary, fs, ff); return null; }
                @Override protected void done() {
                    primaryField.setText(""); secondaryField.setText(""); factorField.setText("1.0");
                    reloadUomList();
                }
            }.execute();
        });

        JButton removeBtn = new JButton("Remove Selected");
        removeBtn.addActionListener(e -> {
            if (uomService == null) return;
            int sel = uomTable.getSelectedRow();
            if (sel < 0 || sel >= uomPresets.size()) {
                JOptionPane.showMessageDialog(this, "Select a unit to remove.", "No selection", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int id = uomPresets.get(sel).getId();
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() { uomService.deletePreset(id); return null; }
                @Override protected void done() { reloadUomList(); }
            }.execute();
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnRow.setOpaque(false);
        btnRow.add(removeBtn);
        btnRow.add(addBtn);

        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(inputGrid, BorderLayout.CENTER);
        south.add(btnRow, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(hint,   BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);
        center.add(south,  BorderLayout.SOUTH);

        ((JPanel)c).add(center, BorderLayout.CENTER);
        p.add(c);

        reloadUomList();
        return p;
    }

    private void reloadUomList() {
        if (uomService == null || uomTableModel == null) return;
        new SwingWorker<java.util.List<UomPreset>, Void>() {
            @Override protected java.util.List<UomPreset> doInBackground() { return uomService.listPresets(); }
            @Override protected void done() {
                try {
                    uomPresets = get();
                    uomTableModel.setRowCount(0);
                    for (UomPreset u : uomPresets) {
                        String sec    = u.getSecondaryUnit() != null ? u.getSecondaryUnit() : "\u2014";
                        String factor = u.getSecondaryUnit() != null
                                ? String.valueOf(u.getConversionFactor() == Math.floor(u.getConversionFactor())
                                    ? (long) u.getConversionFactor().doubleValue()
                                    : u.getConversionFactor())
                                : "\u2014";
                        uomTableModel.addRow(new Object[]{u.getName(), sec, factor});
                    }
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private JPanel buildAppearanceSection() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        CardPanel c = sectionCard("UI APPEARANCE");
        ((JPanel)c).setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel fontLabel = new JLabel("Font Size");
        fontLabel.setFont(fontLabel.getFont().deriveFont(12f));
        fontLabel.setForeground(TEXT2);

        JPanel fontBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fontBtns.setOpaque(false);

        selectedFontSize = settings.fontSizePx();
        ButtonGroup bg = new ButtonGroup();
        int[] sizes = {12, 14, 16, 18};
        String[] labels = {"Small", "Medium", "Large", "XL"};
        for (int i = 0; i < sizes.length; i++) {
            int sz = sizes[i];
            JToggleButton btn = new JToggleButton(labels[i]);
            btn.setSelected(sz == selectedFontSize);
            btn.addActionListener(e -> {
                selectedFontSize = sz;
                FontManager.apply(sz);
            });
            bg.add(btn);
            fontBtns.add(btn);
        }

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.add(fontLabel);
        inner.add(Box.createVerticalStrut(6));
        inner.add(fontBtns);

        ((JPanel)c).add(inner, BorderLayout.CENTER);
        p.add(c);
        p.add(Box.createVerticalStrut(12));
        p.add(saveRow(() -> {
            settings.set("UIFontSize", String.valueOf(selectedFontSize));
            showSavedMsg();
        }));
        return p;
    }

    private JPanel buildAboutSection() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // ── App info card ─────────────────────────────────────────────────────
        CardPanel info = sectionCard("ABOUT HAWKPOS");
        JPanel infoGrid = new JPanel(new GridLayout(5, 1, 0, 6));
        infoGrid.setOpaque(false);

        infoGrid.add(infoRow("Application", "HawkPOS — Point of Sale System"));
        infoGrid.add(infoRow("Version",     "1.0"));
        infoGrid.add(infoRow("Description", "Retail POS software for managing sales, stock, and reporting"));
        infoGrid.add(infoRow("Developer",   "Olympus Systems"));
        infoGrid.add(infoRow("Contact",     "support@olympussystems.lk"));

        ((JPanel)info).add(infoGrid, BorderLayout.CENTER);
        p.add(info);
        p.add(Box.createVerticalStrut(12));

        // ── Trial status card ─────────────────────────────────────────────────
        CardPanel trial = sectionCard("LICENCE & TRIAL STATUS");
        JPanel trialGrid = new JPanel(new GridLayout(5, 1, 0, 6));
        trialGrid.setOpaque(false);

        boolean expired   = settings.isTrialExpired();
        long    elapsed   = settings.trialDaysElapsed();
        long    remaining = settings.trialDaysRemaining();
        int     total     = settings.trialTotalDays();
        String  startDate = settings.trialStartDate().toString();

        trialGrid.add(infoRow("Trial Start Date",  startDate));
        trialGrid.add(infoRow("Trial Period",       total + " days"));
        trialGrid.add(infoRow("Days Elapsed",       elapsed + " days"));
        trialGrid.add(infoRow("Days Remaining",     remaining + " days"));

        JPanel statusRow = new JPanel(new BorderLayout(8, 0));
        statusRow.setOpaque(false);
        JLabel statusLbl = new JLabel("Status");
        statusLbl.setFont(statusLbl.getFont().deriveFont(Font.BOLD, 12f));
        statusLbl.setForeground(TEXT2);
        JLabel statusVal = new JLabel(expired ? "EXPIRED" : "ACTIVE");
        statusVal.setFont(statusVal.getFont().deriveFont(Font.BOLD, 12f));
        statusVal.setForeground(expired ? new Color(0xC6, 0x28, 0x28) : new Color(0x2E, 0x7D, 0x32));
        statusRow.add(statusLbl, BorderLayout.WEST);
        statusRow.add(statusVal, BorderLayout.CENTER);
        trialGrid.add(statusRow);

        ((JPanel)trial).add(trialGrid, BorderLayout.CENTER);
        p.add(trial);

        return p;
    }

    private JPanel infoRow(String label, String value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12f));
        lbl.setForeground(TEXT2);
        lbl.setPreferredSize(new Dimension(140, 0));
        JLabel val = new JLabel(value);
        val.setFont(val.getFont().deriveFont(12f));
        row.add(lbl, BorderLayout.WEST);
        row.add(val, BorderLayout.CENTER);
        return row;
    }

    private JPanel saveRow(Runnable onSave) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JButton save = new JButton("Save");
        save.setBackground(NAVY);
        save.setForeground(Color.WHITE);
        save.setOpaque(true);
        save.setBorderPainted(false);
        save.addActionListener(e -> onSave.run());
        row.add(save);
        return row;
    }

    private void showSavedMsg() {
        JOptionPane.showMessageDialog(this, "Settings saved.", "Saved", JOptionPane.INFORMATION_MESSAGE);
    }

    private CardPanel sectionCard(String title) {
        CardPanel c = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)c).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel)c).setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        ((JPanel)c).setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        t.setForeground(TEXT2);
        ((JPanel)c).add(t, BorderLayout.NORTH);
        return c;
    }

    private JTextField tf(String val) {
        JTextField f = new JTextField(val != null ? val : "");
        return f;
    }

    private JPanel labeled(String lbl, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel l = new JLabel(lbl);
        l.setFont(l.getFont().deriveFont(12f));
        l.setForeground(TEXT2);
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildDangerZoneNavItem() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE2, 0xE5, 0xEA)),
                new EmptyBorder(12, 16, 12, 16)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel l = new JLabel("\u26A0 Danger Zone");
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        l.setForeground(new Color(0xC6, 0x28, 0x28));
        row.add(l, BorderLayout.WEST);

        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (verifyAdminPinDialog("Enter your PIN to access Danger Zone")) {
                    showSection("Danger Zone");
                }
            }
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                row.setBackground(new Color(0xFF, 0xEB, 0xEE));
                row.setOpaque(true);
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                row.setOpaque(false);
            }
        });
        return row;
    }

    private boolean verifyAdminPinDialog(String prompt) {
        if (authService == null || SessionContext.current() == null) {
            JOptionPane.showMessageDialog(this, "Cannot verify PIN: service unavailable.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        JPasswordField pinField = new JPasswordField(10);
        int result = JOptionPane.showConfirmDialog(this,
                new Object[]{ prompt, pinField }, "PIN Verification",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return false;
        String rawPin = new String(pinField.getPassword());
        long empId = SessionContext.current().getEmployee().id();
        if (!authService.verifyPin(empId, rawPin)) {
            JOptionPane.showMessageDialog(this, "Incorrect PIN.", "Access Denied",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    private JPanel buildDangerZoneSection() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        // Warning card
        CardPanel warn = new CardPanel(new BorderLayout(0, 6));
        ((JPanel)warn).setBorder(new EmptyBorder(14, 14, 14, 14));
        ((JPanel)warn).setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        ((JPanel)warn).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel)warn).setBackground(new Color(0xFF, 0xEB, 0xEE));

        JLabel warnTitle = new JLabel("⚠ DANGER ZONE");
        warnTitle.setFont(warnTitle.getFont().deriveFont(Font.BOLD, 14f));
        warnTitle.setForeground(new Color(0xC6, 0x28, 0x28));
        JLabel warnDesc = new JLabel("<html>Permanently delete data from the database. This action CANNOT be undone.<br>" +
                "Make sure you have a backup before proceeding.</html>");
        warnDesc.setFont(warnDesc.getFont().deriveFont(12f));
        warnDesc.setForeground(new Color(0x7B, 0x1F, 0x1F));
        ((JPanel)warn).add(warnTitle, BorderLayout.NORTH);
        ((JPanel)warn).add(warnDesc, BorderLayout.CENTER);
        p.add(warn);
        p.add(Box.createVerticalStrut(12));

        // Checkboxes card
        String[][] groups = {
            { "Sales History & Invoices",          "Clears invoiceinfo, invoice and invoice_history tables (all sales records)" },
            { "Returns",                            "Clears the return table" },
            { "Stock & GRN Records",               "Clears stock, grn, grninfo, item_batch, stock_adjustment tables" },
            { "Customer Records",                  "Clears the customer table" },
            { "Audit Log",                         "Clears the audit_log table" },
            { "Incomes, Expenses & Cash Accounts", "Clears income, expense, cash_transaction and cash_account tables" },
            { "Items (Permanent Delete)",          "PERMANENTLY removes all items, variants and categories/brands links — clear Stock & GRN first" }
        };

        JCheckBox[] checks = new JCheckBox[groups.length];
        CardPanel checksCard = new CardPanel(new BorderLayout(0, 8));
        (checksCard).setBorder(new EmptyBorder(14, 14, 14, 14));
        (checksCard).setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
        (checksCard).setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel checksTitle = new JLabel("SELECT DATA TO DELETE");
        checksTitle.setFont(checksTitle.getFont().deriveFont(Font.BOLD, 11f));
        checksTitle.setForeground(TEXT2);

        JPanel checksList = new JPanel(new GridLayout(groups.length, 1, 0, 6));
        checksList.setOpaque(false);
        for (int i = 0; i < groups.length; i++) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            checks[i] = new JCheckBox(groups[i][0]);
            checks[i].setFont(checks[i].getFont().deriveFont(Font.BOLD, 12f));
            checks[i].setForeground(new Color(0xC6, 0x28, 0x28));
            checks[i].setOpaque(false);
            JLabel desc = new JLabel("<html><i>" + groups[i][1] + "</i></html>");
            desc.setFont(desc.getFont().deriveFont(11f));
            desc.setForeground(TEXT2);
            row.add(checks[i], BorderLayout.WEST);
            row.add(desc, BorderLayout.CENTER);
            checksList.add(row);
        }

        ((JPanel)checksCard).add(checksTitle, BorderLayout.NORTH);
        ((JPanel)checksCard).add(checksList, BorderLayout.CENTER);
        p.add(checksCard);
        p.add(Box.createVerticalStrut(12));

        // Delete button
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JButton deleteBtn = new JButton("Clear Selected Data");
        deleteBtn.setBackground(new Color(0xC6, 0x28, 0x28));
        deleteBtn.setForeground(Color.WHITE);
        deleteBtn.setOpaque(true);
        deleteBtn.setBorderPainted(false);
        deleteBtn.setFocusPainted(false);
        deleteBtn.setFont(deleteBtn.getFont().deriveFont(Font.BOLD, 13f));
        deleteBtn.addActionListener(e -> {
            boolean anySelected = false;
            for (JCheckBox cb : checks) if (cb.isSelected()) { anySelected = true; break; }
            if (!anySelected) {
                JOptionPane.showMessageDialog(this, "No data selected.", "Nothing to clear",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this,
                    "This will PERMANENTLY DELETE the selected data.\nThis action cannot be undone.\n\nAre you sure?",
                    "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
            if (!verifyAdminPinDialog("Enter your PIN to confirm deletion")) return;

            performDeletion(checks, groups);
        });
        btnRow.add(deleteBtn);
        p.add(btnRow);
        return p;
    }

    private void performDeletion(JCheckBox[] checks, String[][] groups) {
        if (sf == null) {
            JOptionPane.showMessageDialog(this, "Database connection unavailable.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        List<String> deleted = new ArrayList<>();
        try (var session = sf.openSession()) {
            var tx = session.beginTransaction();
            // Disable FK checks temporarily
            session.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0", Void.class).executeUpdate();

            for (int i = 0; i < checks.length; i++) {
                if (!checks[i].isSelected()) continue;
                switch (i) {
                    case 0 -> { // Sales History & Invoices
                        session.createNativeQuery("DELETE FROM invoice", Void.class).executeUpdate();
                        session.createNativeQuery("DELETE FROM invoice_history", Void.class).executeUpdate();
                        session.createNativeQuery("DELETE FROM invoiceinfo", Void.class).executeUpdate();
                        deleted.add("Sales History & Invoices");
                    }
                    case 1 -> { // Returns
                        session.createNativeQuery("DELETE FROM `return`", Void.class).executeUpdate();
                        deleted.add("Returns");
                    }
                    case 2 -> { // Stock & GRN
                        session.createNativeQuery("DELETE FROM stock_adjustment", Void.class).executeUpdate();
                        session.createNativeQuery("DELETE FROM grn", Void.class).executeUpdate();
                        session.createNativeQuery("DELETE FROM item_batch", Void.class).executeUpdate();
                        session.createNativeQuery("DELETE FROM stock", Void.class).executeUpdate();
                        session.createNativeQuery("DELETE FROM grninfo", Void.class).executeUpdate();
                        deleted.add("Stock & GRN Records");
                    }
                    case 3 -> { // Customers
                        session.createNativeQuery("DELETE FROM customer", Void.class).executeUpdate();
                        deleted.add("Customer Records");
                    }
                    case 4 -> { // Audit log
                        session.createNativeQuery("DELETE FROM audit_log", Void.class).executeUpdate();
                        deleted.add("Audit Log");
                    }
                    case 5 -> { // Incomes, Expenses & Cash Accounts
                        session.createNativeQuery("DELETE FROM cash_transaction", Void.class).executeUpdate();
                        session.createNativeQuery("DELETE FROM income", Void.class).executeUpdate();
                        session.createNativeQuery("DELETE FROM expense", Void.class).executeUpdate();
                        session.createNativeQuery("DELETE FROM cash_account", Void.class).executeUpdate();
                        deleted.add("Incomes, Expenses & Cash Accounts");
                    }
                    case 6 -> { // Items permanent delete
                        session.createNativeQuery("DELETE FROM item_batch", Void.class).executeUpdate();
                        session.createNativeQuery("DELETE FROM stock_adjustment", Void.class).executeUpdate();
                        session.createNativeQuery("DELETE FROM stock", Void.class).executeUpdate();
                        session.createNativeQuery("DELETE FROM item_variant", Void.class).executeUpdate();
                        session.createNativeQuery("DELETE FROM item", Void.class).executeUpdate();
                        deleted.add("Items (Permanent Delete)");
                    }
                }
            }
            session.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1", Void.class).executeUpdate();
            tx.commit();
            JOptionPane.showMessageDialog(this,
                    "Cleared: " + String.join(", ", deleted),
                    "Done", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error during deletion: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
