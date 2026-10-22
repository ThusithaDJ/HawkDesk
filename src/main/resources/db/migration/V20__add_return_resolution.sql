-- V20: Track how a return was resolved and optionally link to a GRN
ALTER TABLE `return`
    ADD COLUMN resolve_action VARCHAR(45) DEFAULT NULL,
    ADD COLUMN linked_grn_no  INT         DEFAULT NULL;
