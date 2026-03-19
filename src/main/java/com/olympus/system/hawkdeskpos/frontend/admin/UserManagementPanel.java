package com.olympus.system.hawkdeskpos.frontend.admin;

import com.olympus.system.hawkdeskpos.dto.EmployeeDto;
import com.olympus.system.hawkdeskpos.dto.PermissionsDto;
import com.olympus.system.hawkdeskpos.frontend.Home;
import com.olympus.system.hawkdeskpos.frontend.components.CardPanel;
import com.olympus.system.hawkdeskpos.service.UserService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * User Management screen (Owner only).
 * Left: staff list. Right: edit panel with PIN reset + permission toggles.
 */
public class UserManagementPanel extends JPanel {

    private static final Color BG    = new Color(0xF0, 0xF2, 0xF5);
    private static final Color TEXT2 = new Color(0x5A, 0x60, 0x70);
    private static final Color NAVY  = new Color(0x1E, 0x3A, 0x5F);
    private static final Color GREEN = new Color(0x2E, 0x7D, 0x32);

    private final UserService userService;

    private JPanel staffList;
    private JPanel editPanel;
    private EmployeeDto selectedEmployee;
    private PermissionsDto selectedPerms;

    // Edit form fields
    private JTextField nameField;
    private JComboBox<String> roleCombo;
    private JToggleButton activeToggle;
    private JPasswordField pinField, pinConfirmField;

    // Permission toggles (parallel to PermissionsDto field order)
    private static final String[] PERM_LABELS = {
            "Make Sale", "View Sales", "Process Returns", "Find Invoice",
            "View Stock", "Add Item", "Edit Item", "Receive Stock",
            "Adjust Stock", "Manage Categories", "Delete Categories",
            "View Reports", "Export Reports", "View GRN",
            "Access Settings", "Manage Users", "Access Backup"
    };
    private JToggleButton[] permToggles;

    public UserManagementPanel(UserService userService) {
        this.userService = userService;
        setBackground(BG);
        setLayout(new BorderLayout());
        buildUI();
    }

    public void refresh() { loadDataAsync(); }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("User Management");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        header.add(title, BorderLayout.WEST);
        JPanel hBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hBtns.setOpaque(false);
        JButton addUser = new JButton("+ Add User");
        addUser.setBackground(NAVY);
        addUser.setForeground(Color.WHITE);
        addUser.setOpaque(true);
        addUser.setBorderPainted(false);
        addUser.addActionListener(e -> addUser());
        JButton back = new JButton("← Back");
        back.addActionListener(e -> Home.navigate(Home.CARD_SETTINGS));
        hBtns.add(addUser);
        hBtns.add(back);
        header.add(hBtns, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Split
        JPanel split = new JPanel(new GridLayout(1, 2, 14, 0));
        split.setOpaque(false);

        // Left: staff list
        CardPanel listCard = new CardPanel(new BorderLayout(0, 0));
        ((JPanel)listCard).setBorder(new EmptyBorder(14, 14, 14, 14));
        JLabel listTitle = new JLabel("STAFF");
        listTitle.setFont(listTitle.getFont().deriveFont(Font.BOLD, 11f));
        listTitle.setForeground(TEXT2);
        listTitle.setBorder(new EmptyBorder(0, 0, 10, 0));
        staffList = new JPanel();
        staffList.setOpaque(false);
        staffList.setLayout(new BoxLayout(staffList, BoxLayout.Y_AXIS));
        JScrollPane listScroll = new JScrollPane(staffList);
        listScroll.setBorder(null);
        listScroll.setOpaque(false);
        listScroll.getViewport().setOpaque(false);
        ((JPanel)listCard).add(listTitle, BorderLayout.NORTH);
        ((JPanel)listCard).add(listScroll, BorderLayout.CENTER);
        split.add(listCard);

        // Right: edit panel
        editPanel = new JPanel();
        editPanel.setOpaque(false);
        editPanel.setLayout(new BoxLayout(editPanel, BoxLayout.Y_AXIS));
        JScrollPane editScroll = new JScrollPane(editPanel);
        editScroll.setBorder(null);
        editScroll.setOpaque(false);
        editScroll.getViewport().setOpaque(false);

        CardPanel editWrap = new CardPanel(new BorderLayout(0, 0));
        ((JPanel)editWrap).add(editScroll, BorderLayout.CENTER);
        split.add(editWrap);

        root.add(split, BorderLayout.CENTER);
        add(root);

        showPlaceholder();
        loadDataAsync();
    }

    private void showPlaceholder() {
        editPanel.removeAll();
        JLabel ph = new JLabel("<html><center>Select a staff member<br>to edit their profile</center></html>");
        ph.setForeground(TEXT2);
        ph.setHorizontalAlignment(SwingConstants.CENTER);
        ph.setAlignmentX(Component.CENTER_ALIGNMENT);
        editPanel.add(Box.createVerticalGlue());
        editPanel.add(ph);
        editPanel.add(Box.createVerticalGlue());
        editPanel.revalidate();
    }

    private void loadDataAsync() {
        new SwingWorker<List<EmployeeDto>, Void>() {
            @Override protected List<EmployeeDto> doInBackground() { return userService.listAll(); }
            @Override protected void done() {
                try {
                    List<EmployeeDto> employees = get();
                    staffList.removeAll();
                    for (EmployeeDto emp : employees) {
                        staffList.add(buildStaffCard(emp));
                        staffList.add(Box.createVerticalStrut(6));
                    }
                    staffList.revalidate();
                    staffList.repaint();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private JPanel buildStaffCard(EmployeeDto emp) {
        CardPanel card = new CardPanel(new BorderLayout(10, 0));
        ((JPanel)card).setBorder(new EmptyBorder(10, 10, 10, 10));
        ((JPanel)card).setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        ((JPanel)card).setAlignmentX(Component.LEFT_ALIGNMENT);

        // Avatar circle with initials
        JPanel avatar = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(NAVY);
                g2.fillOval(0, 0, getWidth()-1, getHeight()-1);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont().deriveFont(Font.BOLD, 16f));
                String initials = emp.name().length() >= 2 ? emp.name().substring(0,2).toUpperCase() : emp.name().toUpperCase();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(initials, (getWidth()-fm.stringWidth(initials))/2, (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        avatar.setPreferredSize(new Dimension(40, 40));
        avatar.setOpaque(false);

        JPanel info = new JPanel(new GridLayout(2, 1, 0, 2));
        info.setOpaque(false);
        JLabel name = new JLabel(emp.name());
        name.setFont(name.getFont().deriveFont(Font.BOLD, 13f));
        JLabel role = new JLabel(emp.role());
        role.setFont(role.getFont().deriveFont(11f));
        role.setForeground(TEXT2);
        info.add(name);
        info.add(role);

        // Active dot
        JPanel dot = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(emp.active() ? GREEN : new Color(0xC6, 0x28, 0x28));
                g2.fillOval(0, 4, 10, 10);
                g2.dispose();
            }
        };
        dot.setPreferredSize(new Dimension(10, 18));
        dot.setOpaque(false);

        ((JPanel)card).add(avatar, BorderLayout.WEST);
        ((JPanel)card).add(info,   BorderLayout.CENTER);
        ((JPanel)card).add(dot,    BorderLayout.EAST);

        ((JPanel)card).setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        ((JPanel)card).addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                loadEmployeeEdit(emp);
            }
        });
        return card;
    }

    private void loadEmployeeEdit(EmployeeDto emp) {
        selectedEmployee = emp;

        new SwingWorker<PermissionsDto, Void>() {
            @Override protected PermissionsDto doInBackground() {
                return userService.loadPermissions(emp.id());
            }
            @Override protected void done() {
                try {
                    selectedPerms = get();
                    buildEditForm();
                } catch (Exception ignored) {}
            }
        }.execute();
    }

    private void buildEditForm() {
        editPanel.removeAll();
        editPanel.setBorder(new EmptyBorder(14, 14, 14, 14));

        // Profile section
        CardPanel profile = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)profile).setBorder(new EmptyBorder(12, 12, 12, 12));
        ((JPanel)profile).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel)profile).setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        JLabel pt = new JLabel("PROFILE");
        pt.setFont(pt.getFont().deriveFont(Font.BOLD, 11f));
        pt.setForeground(TEXT2);
        ((JPanel)profile).add(pt, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(2, 2, 10, 10));
        grid.setOpaque(false);

        nameField = new JTextField(selectedEmployee.name());
        roleCombo = new JComboBox<>(new String[]{"OWNER", "MANAGER", "CASHIER", "STOCK_KEEPER"});
        roleCombo.setSelectedItem(selectedEmployee.role());
        activeToggle = new JToggleButton(selectedEmployee.active() ? "Active" : "Inactive", selectedEmployee.active());
        activeToggle.addActionListener(e -> activeToggle.setText(activeToggle.isSelected() ? "Active" : "Inactive"));

        grid.add(labeled("Name *", nameField));
        grid.add(labeled("Role *", roleCombo));
        grid.add(labeled("Status", activeToggle));
        ((JPanel)profile).add(grid, BorderLayout.CENTER);

        // PIN reset
        CardPanel pinCard = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)pinCard).setBorder(new EmptyBorder(12, 12, 12, 12));
        ((JPanel)pinCard).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel)pinCard).setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JLabel pinTitle = new JLabel("RESET PIN");
        pinTitle.setFont(pinTitle.getFont().deriveFont(Font.BOLD, 11f));
        pinTitle.setForeground(TEXT2);
        ((JPanel)pinCard).add(pinTitle, BorderLayout.NORTH);

        JPanel pinGrid = new JPanel(new GridLayout(1, 2, 10, 0));
        pinGrid.setOpaque(false);
        pinField        = new JPasswordField();
        pinConfirmField = new JPasswordField();
        pinGrid.add(labeled("New PIN (4 digits)", pinField));
        pinGrid.add(labeled("Confirm PIN", pinConfirmField));
        ((JPanel)pinCard).add(pinGrid, BorderLayout.CENTER);

        // Permissions section
        CardPanel permsCard = new CardPanel(new BorderLayout(0, 10));
        ((JPanel)permsCard).setBorder(new EmptyBorder(12, 12, 12, 12));
        ((JPanel)permsCard).setAlignmentX(Component.LEFT_ALIGNMENT);
        ((JPanel)permsCard).setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));

        JLabel permTitle = new JLabel("PERMISSIONS");
        permTitle.setFont(permTitle.getFont().deriveFont(Font.BOLD, 11f));
        permTitle.setForeground(TEXT2);
        ((JPanel)permsCard).add(permTitle, BorderLayout.NORTH);

        JPanel toggleGrid = new JPanel(new GridLayout(0, 2, 8, 8));
        toggleGrid.setOpaque(false);
        permToggles = new JToggleButton[PERM_LABELS.length];
        boolean[] permsArr = selectedPerms != null ? selectedPerms.toArray() : new boolean[PERM_LABELS.length];
        boolean isOwner = "OWNER".equals(selectedEmployee.role());

        for (int i = 0; i < PERM_LABELS.length; i++) {
            permToggles[i] = new JToggleButton(PERM_LABELS[i], isOwner || (i < permsArr.length && permsArr[i]));
            permToggles[i].setEnabled(!isOwner);
            permToggles[i].setFocusPainted(false);
            toggleGrid.add(permToggles[i]);
        }
        ((JPanel)permsCard).add(toggleGrid, BorderLayout.CENTER);

        // Action buttons
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JButton deactivate = new JButton(selectedEmployee.active() ? "Deactivate" : "Activate");
        deactivate.addActionListener(e -> toggleActive());
        JButton save = new JButton("Save Changes");
        save.setBackground(NAVY);
        save.setForeground(Color.WHITE);
        save.setOpaque(true);
        save.setBorderPainted(false);
        save.addActionListener(e -> saveChanges());
        actions.add(deactivate);
        actions.add(save);

        editPanel.add(profile);
        editPanel.add(Box.createVerticalStrut(10));
        editPanel.add(pinCard);
        editPanel.add(Box.createVerticalStrut(10));
        editPanel.add(permsCard);
        editPanel.add(Box.createVerticalStrut(10));
        editPanel.add(actions);

        editPanel.revalidate();
        editPanel.repaint();
    }

    private void saveChanges() {
        String name = nameField.getText().trim();
        String role = (String) roleCombo.getSelectedItem();
        boolean active = activeToggle.isSelected();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name is required.", "Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // PIN reset
        String newPin = null;
        String pinStr = new String(pinField.getPassword()).trim();
        if (!pinStr.isEmpty()) {
            String pinConf = new String(pinConfirmField.getPassword()).trim();
            if (!pinStr.equals(pinConf)) {
                JOptionPane.showMessageDialog(this, "PINs do not match.", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!pinStr.matches("\\d{4}")) {
                JOptionPane.showMessageDialog(this, "PIN must be 4 digits.", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            newPin = pinStr;
        }

        // Build permissions from toggles
        boolean[] permsArr = new boolean[PERM_LABELS.length];
        if (permToggles != null) {
            for (int i = 0; i < permToggles.length; i++) {
                permsArr[i] = permToggles[i].isSelected();
            }
        }
        PermissionsDto perms = PermissionsDto.fromArray(selectedEmployee.id(), permsArr);

        final String fn = name, fr = role, fp = newPin;
        final boolean fa = active;

        Long actorId = com.olympus.system.hawkdeskpos.session.SessionContext.current() != null
                ? com.olympus.system.hawkdeskpos.session.SessionContext.current().getEmployee().id() : null;

        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                userService.updateEmployee(selectedEmployee.id(), fn, fr, fa, actorId);
                if (fp != null) userService.resetPin(selectedEmployee.id(), fp, actorId);
                userService.savePermissions(perms, actorId);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(UserManagementPanel.this,
                            "Changes saved.", "Saved", JOptionPane.INFORMATION_MESSAGE);
                    loadDataAsync();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(UserManagementPanel.this,
                            "Failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void toggleActive() {
        boolean newActive = !selectedEmployee.active();
        Long actorId = com.olympus.system.hawkdeskpos.session.SessionContext.current() != null
                ? com.olympus.system.hawkdeskpos.session.SessionContext.current().getEmployee().id() : null;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                userService.updateEmployee(selectedEmployee.id(), selectedEmployee.name(), selectedEmployee.role(), newActive, actorId);
                return null;
            }
            @Override protected void done() { loadDataAsync(); showPlaceholder(); }
        }.execute();
    }

    private void addUser() {
        JTextField nm = new JTextField();
        JComboBox<String> rl = new JComboBox<>(new String[]{"CASHIER", "STOCK_KEEPER", "MANAGER"});
        JPasswordField pf = new JPasswordField();
        Object[] fields = {"Name:", nm, "Role:", rl, "PIN (4 digits):", pf};
        int res = JOptionPane.showConfirmDialog(this, fields, "Add New User", JOptionPane.OK_CANCEL_OPTION);
        if (res != JOptionPane.OK_OPTION) return;
        String name = nm.getText().trim();
        String role = (String) rl.getSelectedItem();
        String pin  = new String(pf.getPassword()).trim();
        if (name.isEmpty() || !pin.matches("\\d{4}")) {
            JOptionPane.showMessageDialog(this, "Enter a name and a 4-digit PIN.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Long actorId2 = com.olympus.system.hawkdeskpos.session.SessionContext.current() != null
                ? com.olympus.system.hawkdeskpos.session.SessionContext.current().getEmployee().id() : null;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                userService.createEmployee(name, role, pin, actorId2);
                return null;
            }
            @Override protected void done() { loadDataAsync(); }
        }.execute();
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
