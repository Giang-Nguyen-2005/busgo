-- Visibility is opt-in and independent of code redemption and immutable pricing terms.
ALTER TABLE operator_vouchers ADD customer_visible BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX ix_voucher_discovery ON operator_vouchers(operator_id, customer_visible, state, ends_at);
