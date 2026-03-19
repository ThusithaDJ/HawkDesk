-- V1: Initial schema baseline for hawkdeskpos
-- Creates all original tables from the legacy pharmacy schema

CREATE TABLE IF NOT EXISTS brands (
    brand_id   INT          NOT NULL AUTO_INCREMENT,
    brand_name VARCHAR(45)  DEFAULT NULL,
    PRIMARY KEY (brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS category (
    cat_id        INT         NOT NULL AUTO_INCREMENT,
    category_name VARCHAR(45) DEFAULT NULL,
    stat          VARCHAR(45) DEFAULT NULL,
    colour        VARCHAR(10) DEFAULT '#607D8B',
    PRIMARY KEY (cat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS item (
    item_id   INT         NOT NULL AUTO_INCREMENT,
    item_name VARCHAR(45) DEFAULT NULL,
    sku       VARCHAR(45) DEFAULT NULL,
    min_level INT         DEFAULT 5,
    max_level INT         DEFAULT 100,
    unit      VARCHAR(20) DEFAULT 'pcs',
    stat      VARCHAR(45) DEFAULT 'Active',
    cat_id    INT         DEFAULT NULL,
    brand_id  INT         DEFAULT NULL,
    PRIMARY KEY (item_id),
    UNIQUE KEY uq_item_sku (sku),
    CONSTRAINT fk_item_cat   FOREIGN KEY (cat_id)   REFERENCES category (cat_id),
    CONSTRAINT fk_item_brand FOREIGN KEY (brand_id) REFERENCES brands   (brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS grninfo (
    grn_no    INT          NOT NULL AUTO_INCREMENT,
    date      DATE         DEFAULT NULL,
    sub_total DOUBLE       DEFAULT NULL,
    supplier  VARCHAR(128) DEFAULT NULL,
    reference VARCHAR(64)  DEFAULT NULL,
    PRIMARY KEY (grn_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS stock (
    stock_id    INT          NOT NULL AUTO_INCREMENT,
    batch       VARCHAR(45)  DEFAULT NULL,
    expire_date DATE         DEFAULT NULL,
    qty         INT          DEFAULT 0,
    cost        DOUBLE       DEFAULT NULL,
    price       DOUBLE       DEFAULT NULL,
    stat        VARCHAR(45)  DEFAULT 'Active',
    item_id     INT          DEFAULT NULL,
    grn_no      INT          DEFAULT NULL,
    PRIMARY KEY (stock_id),
    CONSTRAINT fk_stock_item FOREIGN KEY (item_id) REFERENCES item    (item_id),
    CONSTRAINT fk_stock_grn  FOREIGN KEY (grn_no)  REFERENCES grninfo (grn_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS grn (
    no          INT    NOT NULL AUTO_INCREMENT,
    expire_date DATE   DEFAULT NULL,
    item_qty    INT    DEFAULT NULL,
    item_cost   DOUBLE DEFAULT NULL,
    item_price  DOUBLE DEFAULT NULL,
    item_id     INT    DEFAULT NULL,
    grn_no      INT    DEFAULT NULL,
    PRIMARY KEY (no),
    CONSTRAINT fk_grn_item FOREIGN KEY (item_id) REFERENCES item    (item_id),
    CONSTRAINT fk_grn_info FOREIGN KEY (grn_no)  REFERENCES grninfo (grn_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS invoiceinfo (
    invoice_no     VARCHAR(20) NOT NULL,
    date           DATE        DEFAULT NULL,
    total          DOUBLE      DEFAULT NULL,
    stat           VARCHAR(45) DEFAULT 'Paid',
    paid           DOUBLE      DEFAULT NULL,
    discount       DOUBLE      DEFAULT 0,
    payment_method VARCHAR(20) DEFAULT 'Cash',
    PRIMARY KEY (invoice_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS invoice (
    id         INT         NOT NULL AUTO_INCREMENT,
    batch      VARCHAR(45) DEFAULT NULL,
    date_time  DATETIME    DEFAULT NULL,
    qty        INT         DEFAULT NULL,
    sub_total  DOUBLE      DEFAULT NULL,
    invoice_no VARCHAR(20) DEFAULT NULL,
    item_id    INT         DEFAULT NULL,
    stock_id   INT         DEFAULT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_inv_info  FOREIGN KEY (invoice_no) REFERENCES invoiceinfo (invoice_no),
    CONSTRAINT fk_inv_item  FOREIGN KEY (item_id)    REFERENCES item        (item_id),
    CONSTRAINT fk_inv_stock FOREIGN KEY (stock_id)   REFERENCES stock       (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `return` (
    return_id  INT         NOT NULL AUTO_INCREMENT,
    qty        INT         DEFAULT NULL,
    reason     VARCHAR(128) DEFAULT NULL,
    return_to  VARCHAR(45) DEFAULT NULL,
    stat       VARCHAR(45) DEFAULT NULL,
    invoice_no VARCHAR(20) DEFAULT NULL,
    PRIMARY KEY (return_id),
    CONSTRAINT fk_ret_inv FOREIGN KEY (invoice_no) REFERENCES invoiceinfo (invoice_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
