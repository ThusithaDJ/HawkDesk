-- =============================================================================
--  HawkDeskPOS — Fresh Install Script
--  Database: hawkdeskpos
--
--  Use this script when setting up the application on a new machine.
--  This is NOT a Flyway migration. Run it once manually via MySQL Workbench,
--  DBeaver, or the MySQL CLI before launching the application.
--
--  After running this script the application will start normally and
--  Flyway will detect that all migrations are already applied (it reads
--  its own flyway_schema_history table which this script also seeds).
--
--  Default login:  Name → Admin   PIN → 1234
--  Change the PIN immediately after first login.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS hawkdeskpos
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE hawkdeskpos;

-- -----------------------------------------------------------------------------
--  REFERENCE / LOOKUP TABLES (no FK dependencies)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS brands (
    brand_id   INT         NOT NULL AUTO_INCREMENT,
    brand_name VARCHAR(45) DEFAULT NULL,
    PRIMARY KEY (brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS category (
    cat_id        INT         NOT NULL AUTO_INCREMENT,
    category_name VARCHAR(45) DEFAULT NULL,
    stat          VARCHAR(45) DEFAULT NULL,
    colour        VARCHAR(10) DEFAULT '#607D8B',
    PRIMARY KEY (cat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
--  EMPLOYEES (needed by most FK chains)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS employees (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(128) NOT NULL,
    role            ENUM('OWNER','MANAGER','CASHIER','STOCK_KEEPER') NOT NULL DEFAULT 'CASHIER',
    pin_hash        VARCHAR(60)  NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login      DATETIME     DEFAULT NULL,
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    DATETIME     DEFAULT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
--  ITEM (depends on category + brands)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS item (
    item_id           INT         NOT NULL AUTO_INCREMENT,
    item_name         VARCHAR(45) DEFAULT NULL,
    sku               VARCHAR(45) DEFAULT NULL,
    min_level         INT         DEFAULT 5,
    max_level         INT         DEFAULT 100,
    unit              VARCHAR(20) DEFAULT 'pcs',
    sell_unit         VARCHAR(20) DEFAULT NULL,
    conversion_factor DOUBLE      DEFAULT 1.0,
    stat              VARCHAR(45) DEFAULT 'Active',
    cat_id            INT         DEFAULT NULL,
    brand_id          INT         DEFAULT NULL,
    PRIMARY KEY (item_id),
    UNIQUE KEY uq_item_sku (sku),
    CONSTRAINT fk_item_cat   FOREIGN KEY (cat_id)   REFERENCES category (cat_id),
    CONSTRAINT fk_item_brand FOREIGN KEY (brand_id) REFERENCES brands   (brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
--  ITEM VARIANT (depends on item)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS item_variant (
    variant_id INT         NOT NULL AUTO_INCREMENT,
    item_id    INT         NOT NULL,
    sku        VARCHAR(45) NOT NULL,
    stat       VARCHAR(45) DEFAULT 'Active',
    PRIMARY KEY (variant_id),
    UNIQUE KEY uq_item_variant_sku (sku),
    CONSTRAINT fk_item_variant_item FOREIGN KEY (item_id) REFERENCES item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
--  GRN HEADER  (no FK dependencies at this level)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS grninfo (
    grn_no    INT          NOT NULL AUTO_INCREMENT,
    date      DATE         DEFAULT NULL,
    sub_total DOUBLE       DEFAULT NULL,
    supplier  VARCHAR(128) DEFAULT NULL,
    reference VARCHAR(64)  DEFAULT NULL,
    PRIMARY KEY (grn_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
--  ITEM BATCH  (depends on item, grninfo, employees)
--  cost_price is DOUBLE — V14 changed it from DECIMAL(12,2)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS item_batch (
    batch_id      INT          NOT NULL AUTO_INCREMENT,
    item_id       INT          NOT NULL,
    grn_no        INT          NULL,
    batch_number  VARCHAR(20)  NOT NULL,
    batch_label   VARCHAR(100) NULL,
    qty_received  INT          NOT NULL DEFAULT 0,
    qty_remaining INT          NOT NULL DEFAULT 0,
    cost_price    DOUBLE       NOT NULL DEFAULT 0,
    expiry_date   DATE         NULL,
    status        VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    BIGINT       NULL,
    PRIMARY KEY (batch_id),
    UNIQUE KEY uq_item_batch_number (batch_number),
    CONSTRAINT fk_item_batch_item FOREIGN KEY (item_id)    REFERENCES item      (item_id),
    CONSTRAINT fk_item_batch_grn  FOREIGN KEY (grn_no)     REFERENCES grninfo   (grn_no),
    CONSTRAINT fk_item_batch_emp  FOREIGN KEY (created_by) REFERENCES employees (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
--  STOCK  (depends on item, grninfo, item_variant, item_batch, employees)
--  Column order reflects the final state after V7, V8, V9, V3 ALTERs
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS stock (
    stock_id    INT         NOT NULL AUTO_INCREMENT,
    batch       VARCHAR(45) DEFAULT NULL,
    sku         VARCHAR(45) NULL,
    variant_id  INT         NULL,
    batch_id    INT         NULL,
    expire_date DATE        DEFAULT NULL,
    qty         INT         DEFAULT 0,
    cost        DOUBLE      DEFAULT NULL,
    price       DOUBLE      DEFAULT NULL,
    stat        VARCHAR(45) DEFAULT 'Active',
    item_id     INT         DEFAULT NULL,
    grn_no      INT         DEFAULT NULL,
    employee_id BIGINT      DEFAULT NULL,
    PRIMARY KEY (stock_id),
    CONSTRAINT fk_stock_item    FOREIGN KEY (item_id)    REFERENCES item         (item_id),
    CONSTRAINT fk_stock_grn     FOREIGN KEY (grn_no)     REFERENCES grninfo      (grn_no),
    CONSTRAINT fk_stock_emp     FOREIGN KEY (employee_id)REFERENCES employees    (id)    ON DELETE SET NULL,
    CONSTRAINT fk_stock_variant FOREIGN KEY (variant_id) REFERENCES item_variant (variant_id),
    CONSTRAINT fk_stock_batch   FOREIGN KEY (batch_id)   REFERENCES item_batch   (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
--  GRN LINE ITEMS  (depends on item, grninfo, item_batch)
--  Column order reflects the final state after V11 ALTER (batch_id AFTER no)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS grn (
    no          INT    NOT NULL AUTO_INCREMENT,
    batch_id    INT    NULL,
    expire_date DATE   DEFAULT NULL,
    item_qty    INT    DEFAULT NULL,
    item_cost   DOUBLE DEFAULT NULL,
    item_price  DOUBLE DEFAULT NULL,
    item_id     INT    DEFAULT NULL,
    grn_no      INT    DEFAULT NULL,
    PRIMARY KEY (no),
    CONSTRAINT fk_grn_item  FOREIGN KEY (item_id)  REFERENCES item       (item_id),
    CONSTRAINT fk_grn_info  FOREIGN KEY (grn_no)   REFERENCES grninfo    (grn_no),
    CONSTRAINT fk_grn_batch FOREIGN KEY (batch_id) REFERENCES item_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
--  INVOICE HEADER  (depends on employees)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS invoiceinfo (
    invoice_no     VARCHAR(20) NOT NULL,
    date           DATE        DEFAULT NULL,
    total          DOUBLE      DEFAULT NULL,
    stat           VARCHAR(45) DEFAULT 'Paid',
    paid           DOUBLE      DEFAULT NULL,
    discount       DOUBLE      DEFAULT 0,
    payment_method VARCHAR(20) DEFAULT 'Cash',
    employee_id    BIGINT      DEFAULT NULL,
    PRIMARY KEY (invoice_no),
    CONSTRAINT fk_invinfo_emp FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
--  INVOICE LINE ITEMS  (depends on invoiceinfo, item, stock, item_batch, employees)
--  Column order reflects the final state after V10 ALTER (batch_id AFTER batch)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS invoice (
    id          INT         NOT NULL AUTO_INCREMENT,
    batch       VARCHAR(45) DEFAULT NULL,
    batch_id    INT         NULL,
    date_time   DATETIME    DEFAULT NULL,
    qty         INT         DEFAULT NULL,
    sub_total   DOUBLE      DEFAULT NULL,
    invoice_no  VARCHAR(20) DEFAULT NULL,
    item_id     INT         DEFAULT NULL,
    stock_id    INT         DEFAULT NULL,
    employee_id BIGINT      DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_inv_info    FOREIGN KEY (invoice_no)  REFERENCES invoiceinfo (invoice_no),
    CONSTRAINT fk_inv_item    FOREIGN KEY (item_id)     REFERENCES item        (item_id),
    CONSTRAINT fk_inv_stock   FOREIGN KEY (stock_id)    REFERENCES stock       (stock_id),
    CONSTRAINT fk_inv_emp     FOREIGN KEY (employee_id) REFERENCES employees   (id)    ON DELETE SET NULL,
    CONSTRAINT fk_invoice_batch FOREIGN KEY (batch_id)  REFERENCES item_batch  (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
--  RETURNS  (depends on invoiceinfo, item, stock, employees)
--  Column order reflects the final state after V15 ALTER (item_id, stock_id,
--  item_name, return_date inserted AFTER stat) and V3 ALTER (employee_id)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `return` (
    return_id   INT          NOT NULL AUTO_INCREMENT,
    qty         INT          DEFAULT NULL,
    reason      VARCHAR(128) DEFAULT NULL,
    return_to   VARCHAR(45)  DEFAULT NULL,
    stat        VARCHAR(45)  DEFAULT NULL,
    item_id     INT          DEFAULT NULL,
    stock_id    INT          DEFAULT NULL,
    item_name   VARCHAR(100) DEFAULT NULL,
    return_date DATETIME     DEFAULT NULL,
    invoice_no  VARCHAR(20)  DEFAULT NULL,
    employee_id BIGINT       DEFAULT NULL,
    PRIMARY KEY (return_id),
    CONSTRAINT fk_ret_inv   FOREIGN KEY (invoice_no)  REFERENCES invoiceinfo (invoice_no),
    CONSTRAINT fk_ret_emp   FOREIGN KEY (employee_id) REFERENCES employees   (id)    ON DELETE SET NULL,
    CONSTRAINT fk_ret_item  FOREIGN KEY (item_id)     REFERENCES item        (item_id),
    CONSTRAINT fk_ret_stock FOREIGN KEY (stock_id)    REFERENCES stock       (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
--  AUDIT LOG  (depends on employees)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS audit_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    table_name   VARCHAR(64)  NOT NULL,
    record_id    BIGINT       DEFAULT NULL,
    action       ENUM('INSERT','UPDATE','DELETE','LOGIN','LOGOUT','PERMISSION_DENIED') NOT NULL,
    old_value    JSON         DEFAULT NULL,
    new_value    JSON         DEFAULT NULL,
    employee_id  BIGINT       DEFAULT NULL,
    ip_address   VARCHAR(45)  DEFAULT NULL,
    performed_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes        VARCHAR(512) DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_emp FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
--  STOCK ADJUSTMENT  (depends on item, item_batch, employees)
--  Column order reflects the final state after V12 ALTER (batch_id AFTER item_id)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS stock_adjustment (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    item_id         BIGINT        NOT NULL,
    batch_id        INT           NULL,
    adjustment_type ENUM('ADD','REMOVE','SET','WRITEOFF') NOT NULL,
    qty_before      INT           NOT NULL,
    qty_change      INT           NOT NULL,
    qty_after       INT           NOT NULL,
    reason          VARCHAR(128)  NOT NULL,
    notes           VARCHAR(512)  DEFAULT NULL,
    loss_value      DECIMAL(12,2) DEFAULT NULL,
    employee_id     BIGINT        DEFAULT NULL,
    adjusted_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_adj_item  FOREIGN KEY (item_id)    REFERENCES item       (item_id),
    CONSTRAINT fk_adj_emp   FOREIGN KEY (employee_id)REFERENCES employees  (id) ON DELETE SET NULL,
    CONSTRAINT fk_stock_adj_batch FOREIGN KEY (batch_id) REFERENCES item_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
--  USER PERMISSIONS  (depends on employees)
--  Includes V13 columns: can_view_batch_cost, can_override_fifo
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS user_permissions (
    id                    BIGINT     NOT NULL AUTO_INCREMENT,
    employee_id           BIGINT     NOT NULL,
    can_make_sale         BOOLEAN    NOT NULL DEFAULT TRUE,
    can_view_sales        BOOLEAN    NOT NULL DEFAULT TRUE,
    can_process_returns   BOOLEAN    NOT NULL DEFAULT FALSE,
    can_find_invoice      BOOLEAN    NOT NULL DEFAULT TRUE,
    can_view_stock        BOOLEAN    NOT NULL DEFAULT TRUE,
    can_add_item          BOOLEAN    NOT NULL DEFAULT FALSE,
    can_edit_item         BOOLEAN    NOT NULL DEFAULT FALSE,
    can_receive_stock     BOOLEAN    NOT NULL DEFAULT FALSE,
    can_adjust_stock      BOOLEAN    NOT NULL DEFAULT FALSE,
    can_manage_categories BOOLEAN    NOT NULL DEFAULT FALSE,
    can_delete_categories BOOLEAN    NOT NULL DEFAULT FALSE,
    can_view_reports      BOOLEAN    NOT NULL DEFAULT TRUE,
    can_export_reports    BOOLEAN    NOT NULL DEFAULT FALSE,
    can_view_grn          BOOLEAN    NOT NULL DEFAULT FALSE,
    can_access_settings   BOOLEAN    NOT NULL DEFAULT FALSE,
    can_manage_users      BOOLEAN    NOT NULL DEFAULT FALSE,
    can_access_backup     BOOLEAN    NOT NULL DEFAULT FALSE,
    can_view_batch_cost   TINYINT(1) NOT NULL DEFAULT 0,
    can_override_fifo     TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_perm_emp (employee_id),
    CONSTRAINT fk_perm_emp FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =============================================================================
--  DEFAULT DATA
-- =============================================================================

-- Default Admin account  (PIN: 1234 — BCrypt hash, cost 10)
-- IMPORTANT: change the PIN immediately after first login.
INSERT INTO employees (name, role, pin_hash, active)
VALUES ('Admin', 'OWNER', '$2a$10$QTO6QbO8LW/TJlVRQETu0OGa5yj4PrG3NVDghjIdNzkOvAOIzAetq', TRUE);

-- Full permissions for the Admin/Owner
INSERT INTO user_permissions (
    employee_id,
    can_make_sale, can_view_sales, can_process_returns, can_find_invoice,
    can_view_stock, can_add_item, can_edit_item, can_receive_stock, can_adjust_stock,
    can_manage_categories, can_delete_categories,
    can_view_reports, can_export_reports, can_view_grn,
    can_access_settings, can_manage_users, can_access_backup,
    can_view_batch_cost, can_override_fifo
)
SELECT id,
    TRUE,  TRUE,  TRUE,  TRUE,
    TRUE,  TRUE,  TRUE,  TRUE,  TRUE,
    TRUE,  TRUE,
    TRUE,  TRUE,  TRUE,
    TRUE,  TRUE,  TRUE,
    TRUE,  TRUE
FROM employees WHERE role = 'OWNER' LIMIT 1;

-- =============================================================================
--  UOM PRESETS  (V21)
-- =============================================================================

CREATE TABLE IF NOT EXISTS uom_preset (
    id   INT         NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO uom_preset (name) VALUES
    ('pcs'), ('dozen'), ('box'),
    ('kg'), ('g'), ('mg'),
    ('m'), ('cm'), ('mm'),
    ('L'), ('mL');

-- =============================================================================
--  FLYWAY SCHEMA HISTORY
--  Tells Flyway that all migrations are already applied so it does not attempt
--  to re-run them when the application first connects.
-- =============================================================================

CREATE TABLE IF NOT EXISTS flyway_schema_history (
    installed_rank INT            NOT NULL,
    version        VARCHAR(50)    DEFAULT NULL,
    description    VARCHAR(200)   NOT NULL,
    type           VARCHAR(20)    NOT NULL,
    script         VARCHAR(1000)  NOT NULL,
    checksum       INT            DEFAULT NULL,
    installed_by   VARCHAR(100)   NOT NULL,
    installed_on   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time INT            NOT NULL,
    success        TINYINT(1)     NOT NULL,
    PRIMARY KEY (installed_rank)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO flyway_schema_history
    (installed_rank, version, description,                         type, script,                                     checksum, installed_by, execution_time, success)
VALUES
    ( 1, '1',  'initial schema',                  'SQL', 'V1__initial_schema.sql',                  NULL, 'root',  10, 1),
    ( 2, '2',  'create employees',                'SQL', 'V2__create_employees.sql',                NULL, 'root',  10, 1),
    ( 3, '3',  'add employee id columns',         'SQL', 'V3__add_employee_id_columns.sql',         NULL, 'root',  10, 1),
    ( 4, '4',  'create audit log',                'SQL', 'V4__create_audit_log.sql',                NULL, 'root',  10, 1),
    ( 5, '5',  'create stock adjustment',         'SQL', 'V5__create_stock_adjustment.sql',         NULL, 'root',  10, 1),
    ( 6, '6',  'create user permissions',         'SQL', 'V6__create_user_permissions.sql',         NULL, 'root',  10, 1),
    ( 7, '7',  'add stock sku',                   'SQL', 'V7__add_stock_sku.sql',                   NULL, 'root',  10, 1),
    ( 8, '8',  'add item variant',                'SQL', 'V8__add_item_variant.sql',                NULL, 'root',  10, 1),
    ( 9, '9',  'create item batch',               'SQL', 'V9__create_item_batch.sql',               NULL, 'root',  10, 1),
    (10, '10', 'add batch id to invoice',         'SQL', 'V10__add_batch_id_to_invoice.sql',        NULL, 'root',  10, 1),
    (11, '11', 'add batch id to grn',             'SQL', 'V11__add_batch_id_to_grn.sql',            NULL, 'root',  10, 1),
    (12, '12', 'add batch id to stock adjustment','SQL', 'V12__add_batch_id_to_stock_adjustment.sql',NULL,'root',  10, 1),
    (13, '13', 'add batch permissions',           'SQL', 'V13__add_batch_permissions.sql',          NULL, 'root',  10, 1),
    (14, '14', 'fix item batch cost type',        'SQL', 'V14__fix_item_batch_cost_type.sql',       NULL, 'root',  10, 1),
    (15, '15', 'add return enhancements',         'SQL', 'V15__add_return_enhancements.sql',        NULL, 'root',  10, 1),
    (16, '16', 'create customer',                 'SQL', 'V16__create_customer.sql',                 NULL, 'root',  10, 1),
    (17, '17', 'add credit fields to invoiceinfo','SQL', 'V17__add_credit_fields_to_invoiceinfo.sql',NULL, 'root',  10, 1),
    (18, '18', 'fix customer max debt type',      'SQL', 'V18__fix_customer_max_debt_type.sql',      NULL, 'root',  10, 1),
    (19, '19', 'add cost price to invoice',       'SQL', 'V19__add_cost_price_to_invoice.sql',       NULL, 'root',  10, 1),
    (20, '20', 'add return resolution',           'SQL', 'V20__add_return_resolution.sql',           NULL, 'root',  10, 1),
    (21, '21', 'uom support',                     'SQL', 'V21__uom_support.sql',                     NULL, 'root',  10, 1);

-- =============================================================================
--  DONE
--  You can now launch HawkDeskPOS and log in with:
--    Name : Admin
--    PIN  : 1234
-- =============================================================================
