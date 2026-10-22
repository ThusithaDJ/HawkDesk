-- V30: Enhance cash_transaction table
--   • Add CREDIT_PAYMENT transaction type
--   • Add nullable FK references to source records

ALTER TABLE cash_transaction
  MODIFY COLUMN type ENUM('SALE','GRN','RETURN_REFUND','INCOME','EXPENSE','CREDIT_PAYMENT') NOT NULL;

ALTER TABLE cash_transaction
  ADD COLUMN invoice_no  VARCHAR(20) DEFAULT NULL AFTER category,
  ADD COLUMN return_id   INT         DEFAULT NULL AFTER invoice_no,
  ADD COLUMN grn_no      INT         DEFAULT NULL AFTER return_id,
  ADD COLUMN income_id   BIGINT      DEFAULT NULL AFTER grn_no,
  ADD COLUMN expense_id  BIGINT      DEFAULT NULL AFTER income_id,
  ADD INDEX idx_ctx_inv (invoice_no),
  ADD INDEX idx_ctx_grn (grn_no);

ALTER TABLE cash_transaction
  ADD CONSTRAINT fk_ctx_invoice  FOREIGN KEY (invoice_no) REFERENCES invoiceinfo(invoice_no) ON DELETE SET NULL,
  ADD CONSTRAINT fk_ctx_return   FOREIGN KEY (return_id)  REFERENCES `return`(return_id)     ON DELETE SET NULL,
  ADD CONSTRAINT fk_ctx_grn      FOREIGN KEY (grn_no)     REFERENCES grninfo(grn_no)         ON DELETE SET NULL,
  ADD CONSTRAINT fk_ctx_income   FOREIGN KEY (income_id)  REFERENCES income(id)              ON DELETE SET NULL,
  ADD CONSTRAINT fk_ctx_expense  FOREIGN KEY (expense_id) REFERENCES expense(id)             ON DELETE SET NULL;
