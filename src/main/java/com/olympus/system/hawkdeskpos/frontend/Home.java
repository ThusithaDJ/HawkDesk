package com.olympus.system.hawkdeskpos.frontend;

import com.olympus.system.hawkdeskpos.db.util.Controller;
import com.olympus.system.hawkdeskpos.frontend.admin.*;
import com.olympus.system.hawkdeskpos.frontend.components.NavBar;
import com.olympus.system.hawkdeskpos.frontend.components.Refreshable;
import com.olympus.system.hawkdeskpos.frontend.finance.GrnHistoryPanel;
import com.olympus.system.hawkdeskpos.frontend.reports.ReportsPanel;
import com.olympus.system.hawkdeskpos.frontend.sale.*;
import com.olympus.system.hawkdeskpos.frontend.stock.*;
import com.olympus.system.hawkdeskpos.service.*;
import com.olympus.system.hawkdeskpos.session.SessionContext;
import org.hibernate.SessionFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Main application window — undecorated, always maximised, CardLayout host.
 *
 * Backward-compat shims (other compiled classes reference these fields):
 *   public static JDesktopPane HomeDeskpane  (kept empty — do not add to it)
 *   public static JList<String> lstNotifi    (updated by DashboardPanel)
 */
public class Home extends JFrame {

    // ── Card names ────────────────────────────────────────────────────────────
    public static final String CARD_LOGIN     = "LOGIN";
    public static final String CARD_DASH      = "DASHBOARD";
    public static final String CARD_SALE      = "NEW_SALE";
    public static final String CARD_STOCK     = "VIEW_STOCK";
    public static final String CARD_ADD_ITEM  = "ADD_ITEM";
    public static final String CARD_EDIT_ITEM = "EDIT_ITEM";
    public static final String CARD_RECEIVE   = "RECEIVE_STOCK";
    public static final String CARD_LOW_STOCK = "LOW_STOCK";
    public static final String CARD_HIST      = "SALES_HISTORY";
    public static final String CARD_FIND_INV  = "FIND_INVOICE";
    public static final String CARD_RETURNS   = "GOODS_RETURN";
    public static final String CARD_ADJUST    = "STOCK_ADJUSTMENT";
    public static final String CARD_REPORTS   = "REPORTS";
    public static final String CARD_CATS      = "CATEGORIES";
    public static final String CARD_USERS     = "USER_MGMT";
    public static final String CARD_SETTINGS  = "SETTINGS";
    public static final String CARD_BACKUP    = "BACKUP";
    public static final String CARD_GRN_HIST  = "GRN_HISTORY";

    // ── Backward-compat shims (referenced by old compiled classes) ────────────
    /** @deprecated No longer used; kept for compile compatibility only. */
    @Deprecated
    public static final JDesktopPane HomeDeskpane = new JDesktopPane();

    /** Updated by DashboardPanel with low-stock item names. */
    public static JList<String> lstNotifi = new JList<>();

    /** @deprecated No-op shim for legacy panels; kept for compile compatibility only. */
    @Deprecated
    public static void setNotifications() {}

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static Home instance;

    // ── Core layout ───────────────────────────────────────────────────────────
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel     cardHost   = new JPanel(cardLayout);
    private final JPanel     rootPanel  = new JPanel(new BorderLayout());

    // ── Services ──────────────────────────────────────────────────────────────
    private final SessionFactory  sf;
    private final AuditService    auditService;
    private final AuthService     authService;
    private final ItemService     itemService;
    private final SaleService     saleService;
    private final StockService    stockService;
    private final CategoryService categoryService;
    private final UserService     userService;
    private final ReturnService   returnService;
    private final ReportService   reportService;
    private final BackupService   backupService;
    private final SettingsService settingsService;

    private NavBar navBar;

    public Home() {
        instance = this;

        sf              = Controller.getSessionFactory();
        auditService    = new AuditService(sf);
        authService     = new AuthService(sf, auditService);
        itemService     = new ItemService(sf, auditService);
        saleService     = new SaleService(sf, auditService);
        stockService    = new StockService(sf, auditService);
        categoryService = new CategoryService(sf, auditService);
        userService     = new UserService(sf, auditService);
        returnService   = new ReturnService(sf, auditService);
        reportService   = new ReportService(sf);
        settingsService = new SettingsService(sf);
        backupService   = new BackupService(sf, settingsService);

        setUndecorated(true);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1024, 768));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        rootPanel.setBackground(new Color(0xF0, 0xF2, 0xF5));
        cardHost.setBackground(new Color(0xF0, 0xF2, 0xF5));
        rootPanel.add(cardHost, BorderLayout.CENTER);
        setContentPane(rootPanel);

        registerCards();
        cardLayout.show(cardHost, CARD_LOGIN);
    }

    private void registerCards() {
        cardHost.add(new LoginPanel(authService, auditService, this::onLoginSuccess), CARD_LOGIN);
        cardHost.add(new DashboardPanel(itemService, saleService, settingsService), CARD_DASH);
        cardHost.add(new NewSalePanel(itemService, saleService, settingsService), CARD_SALE);
        cardHost.add(new ViewStockPanel(itemService, categoryService), CARD_STOCK);
        cardHost.add(new AddItemPanel(itemService, categoryService), CARD_ADD_ITEM);
        cardHost.add(new EditItemPanel(itemService, categoryService), CARD_EDIT_ITEM);
        cardHost.add(new ReceiveStockPanel(itemService, stockService, settingsService), CARD_RECEIVE);
        cardHost.add(new LowStockPanel(itemService, reportService), CARD_LOW_STOCK);
        cardHost.add(new SalesHistoryPanel(saleService), CARD_HIST);
        cardHost.add(new FindInvoicePanel(saleService), CARD_FIND_INV);
        cardHost.add(new GoodsReturnPanel(saleService, returnService), CARD_RETURNS);
        cardHost.add(new StockAdjustmentPanel(itemService, stockService), CARD_ADJUST);
        cardHost.add(new ReportsPanel(reportService, settingsService), CARD_REPORTS);
        cardHost.add(new ManageCategoriesPanel(categoryService), CARD_CATS);
        cardHost.add(new UserManagementPanel(userService), CARD_USERS);
        cardHost.add(new SettingsPanel(settingsService), CARD_SETTINGS);
        cardHost.add(new BackupPanel(backupService), CARD_BACKUP);
        cardHost.add(new GrnHistoryPanel(stockService), CARD_GRN_HIST);
    }

    private void onLoginSuccess() {
        if (navBar != null) rootPanel.remove(navBar);
        navBar = new NavBar(settingsService.shopName(), this::onLogout);
        wireNavButtons(navBar);
        rootPanel.add(navBar, BorderLayout.NORTH);
        rootPanel.revalidate();
        rootPanel.repaint();
        navigate(CARD_DASH);
    }

    private void wireNavButtons(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof NavBar.NavButton btn) {
                btn.addActionListener(e -> navigate(btn.getCard()));
            } else if (c instanceof Container ct) {
                wireNavButtons(ct);
            }
        }
    }

    private void onLogout() {
        SessionContext.logout();
        if (navBar != null) { rootPanel.remove(navBar); navBar = null; }
        rootPanel.revalidate();
        rootPanel.repaint();
        // Replace login panel with a fresh one
        cardHost.removeAll();
        registerCards();
        cardHost.revalidate();
        cardLayout.show(cardHost, CARD_LOGIN);
    }

    // ── Static navigation API ─────────────────────────────────────────────────

    public static void navigate(String card) {
        if (instance == null) return;
        instance.cardLayout.show(instance.cardHost, card);
        for (Component c : instance.cardHost.getComponents()) {
            if (c.isVisible() && c instanceof Refreshable r) { r.refresh(); break; }
        }
    }

    public static void navigateToEditItem(int itemId) {
        if (instance == null) return;
        for (Component c : instance.cardHost.getComponents()) {
            if (c instanceof EditItemPanel eip) { eip.loadItem(itemId); break; }
        }
        navigate(CARD_EDIT_ITEM);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────
    public static Home          get()        { return instance;       }
    public ItemService     items()           { return itemService;     }
    public SaleService     sales()           { return saleService;     }
    public StockService    stock()           { return stockService;    }
    public CategoryService categories()      { return categoryService; }
    public UserService     users()           { return userService;     }
    public ReturnService   returns()         { return returnService;   }
    public ReportService   reports()         { return reportService;   }
    public BackupService   backup()          { return backupService;   }
    public SettingsService settings()        { return settingsService; }
}
