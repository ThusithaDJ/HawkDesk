-- V18: Match max_debt_amount column type to Hibernate Double (FLOAT → DOUBLE)
ALTER TABLE customer MODIFY COLUMN max_debt_amount DOUBLE DEFAULT 0.00;
