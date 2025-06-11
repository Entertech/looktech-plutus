-- Remove unique constraint
ALTER TABLE credit_transaction_logs DROP INDEX UK_838o3mvk8paib2kh46n7rgnok;

-- Add non-unique index
CREATE INDEX idx_transaction_id ON credit_transaction_logs(transaction_id); 