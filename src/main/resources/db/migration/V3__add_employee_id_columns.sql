-- V3: Add employee_id foreign key to transaction tables for audit trail

ALTER TABLE invoiceinfo
    ADD COLUMN employee_id BIGINT DEFAULT NULL,
    ADD CONSTRAINT fk_invinfo_emp FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL;

ALTER TABLE invoice
    ADD COLUMN employee_id BIGINT DEFAULT NULL,
    ADD CONSTRAINT fk_inv_emp FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL;

ALTER TABLE stock
    ADD COLUMN employee_id BIGINT DEFAULT NULL,
    ADD CONSTRAINT fk_stock_emp FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL;

ALTER TABLE `return`
    ADD COLUMN employee_id BIGINT DEFAULT NULL,
    ADD CONSTRAINT fk_ret_emp FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL;
