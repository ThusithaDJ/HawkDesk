-- V6: Per-user screen permission matrix

CREATE TABLE IF NOT EXISTS user_permissions (
    id                    BIGINT  NOT NULL AUTO_INCREMENT,
    employee_id           BIGINT  NOT NULL UNIQUE,
    can_make_sale         BOOLEAN NOT NULL DEFAULT TRUE,
    can_view_sales        BOOLEAN NOT NULL DEFAULT TRUE,
    can_process_returns   BOOLEAN NOT NULL DEFAULT FALSE,
    can_find_invoice      BOOLEAN NOT NULL DEFAULT TRUE,
    can_view_stock        BOOLEAN NOT NULL DEFAULT TRUE,
    can_add_item          BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit_item         BOOLEAN NOT NULL DEFAULT FALSE,
    can_receive_stock     BOOLEAN NOT NULL DEFAULT FALSE,
    can_adjust_stock      BOOLEAN NOT NULL DEFAULT FALSE,
    can_manage_categories BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete_categories BOOLEAN NOT NULL DEFAULT FALSE,
    can_view_reports      BOOLEAN NOT NULL DEFAULT TRUE,
    can_export_reports    BOOLEAN NOT NULL DEFAULT FALSE,
    can_view_grn          BOOLEAN NOT NULL DEFAULT FALSE,
    can_access_settings   BOOLEAN NOT NULL DEFAULT FALSE,
    can_manage_users      BOOLEAN NOT NULL DEFAULT FALSE,
    can_access_backup     BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_perm_emp FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Give the default Admin (Owner) full permissions
INSERT INTO user_permissions (employee_id,
    can_make_sale, can_view_sales, can_process_returns, can_find_invoice,
    can_view_stock, can_add_item, can_edit_item, can_receive_stock, can_adjust_stock,
    can_manage_categories, can_delete_categories,
    can_view_reports, can_export_reports, can_view_grn,
    can_access_settings, can_manage_users, can_access_backup)
SELECT id, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE,
       TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE
FROM employees WHERE role = 'OWNER' LIMIT 1;
