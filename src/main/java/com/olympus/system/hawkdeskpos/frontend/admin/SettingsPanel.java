package com.olympus.system.hawkdeskpos.frontend.admin;

import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.frontend.components.FontManager;
import com.olympus.system.hawkdeskpos.service.SettingsService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Settings screen.
 * Left sidebar nav + card panel for each section.
 */
public class SettingsPanel extends JPanel {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);

    private final SettingsService settings;

    private JPanel contentArea;
    private static final String[] SECTIONS = {
            "Shop Details", "Sales & Billing", "Receipt & Printing",
            "Stock & Inventory", "Categories & Brands", "UI Appearance", "Backup", "User Management"
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

    // UI appearance
    private int selectedFontSize;

    public SettingsPanel(SettingsService settings) {
        this.settings = settings;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
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

        JPanel navPanel = new JPanel();
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
            case "UI Appearance"     -> buildAppearanceSection();
            case "Categories & Brands" -> { Home.navigate(Home.CARD_CATS);   yield new JPanel(); }
            case "Backup"              -> { Home.navigate(Home.CARD_BACKUP); yield new JPanel(); }
            case "User Management"     -> { Home.navigate(Home.CARD_USERS);  yield new JPanel(); }
            default                  -> buildShopDetailsSection();
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
        JPanel grid = new JPanel(new GridLayout(1, 2, 10, 0));
        grid.setOpaque(false);

        defaultMinLevelField = tf(settings.get("defaultMinLevel", "5"));
        grid.add(labeled("Default Min Level", defaultMinLevelField));

        ((JPanel)c).add(grid, BorderLayout.CENTER);
        p.add(c);
        p.add(Box.createVerticalStrut(12));
        p.add(saveRow(() -> {
            settings.set("defaultMinLevel", defaultMinLevelField.getText().trim());
            showSavedMsg();
        }));
        return p;
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
}
