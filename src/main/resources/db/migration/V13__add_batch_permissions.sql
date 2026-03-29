ALTER TABLE user_permissions
    ADD COLUMN can_view_batch_cost TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN can_override_fifo   TINYINT(1) NOT NULL DEFAULT 0;
