-- V15: Enhance return table with item tracking, return date, and employee link
ALTER TABLE `return`
    ADD COLUMN item_id     INT          DEFAULT NULL AFTER stat,
    ADD COLUMN stock_id    INT          DEFAULT NULL AFTER item_id,
    ADD COLUMN item_name   VARCHAR(100) DEFAULT NULL AFTER stock_id,
    ADD COLUMN return_date DATETIME     DEFAULT NULL AFTER item_name;

ALTER TABLE `return`
    ADD CONSTRAINT fk_ret_item  FOREIGN KEY (item_id)  REFERENCES item  (item_id),
    ADD CONSTRAINT fk_ret_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id);
