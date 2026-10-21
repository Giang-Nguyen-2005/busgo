-- Finance references commerce identities logically: finance workers must never acquire
-- implicit FK locks on payment/booking/operator rows after taking the finance mutex.
CREATE TABLE finance_mutex (
 operator_id BIGINT PRIMARY KEY
);
CREATE TABLE commission_policies (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 operator_id BIGINT NOT NULL,
 rate DECIMAL(7,4) NOT NULL,
 valid_from DATETIME(6) NOT NULL,
 valid_to DATETIME(6),
 state VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 created_by BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 activated_at DATETIME(6),
 INDEX ix_commission_window(operator_id,state,valid_from,valid_to),
 CHECK(rate>=0 AND rate<=100),
 CHECK(valid_to IS NULL OR valid_to>valid_from),
 CHECK(state IN ('DRAFT','ACTIVE'))
);
CREATE TABLE financial_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 source_key VARCHAR(80) NOT NULL UNIQUE,
 event_type VARCHAR(20) NOT NULL,
 operator_id BIGINT NOT NULL,
 booking_id BIGINT NOT NULL,
 payment_id BIGINT NOT NULL,
 refund_id BIGINT,
 collection_payment_id BIGINT GENERATED ALWAYS AS (CASE WHEN event_type='COLLECTION' THEN payment_id ELSE NULL END) STORED,
 UNIQUE KEY uq_finance_collection(collection_payment_id),
 UNIQUE KEY uq_finance_refund(refund_id),
 purpose VARCHAR(30) NOT NULL,
 origin VARCHAR(40) NOT NULL,
 channel VARCHAR(50) NOT NULL,
 amount DECIMAL(12,2) NOT NULL,
 policy_id BIGINT,
 rate DECIMAL(7,4),
 commission DECIMAL(12,2),
 operator_payable DECIMAL(12,2),
 platform_receivable DECIMAL(12,2),
 gross_context DECIMAL(12,2),
 discount_context DECIMAL(12,2),
 occurred_at DATETIME(6) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 currency CHAR(3) NOT NULL DEFAULT 'VND',
 verification VARCHAR(30) NOT NULL DEFAULT 'SIMULATED',
 INDEX ix_finance_period(operator_id,occurred_at,id),
 INDEX ix_finance_payment(payment_id,event_type,refund_id),
 CHECK(event_type IN ('COLLECTION','REFUND')),
 CHECK((event_type='COLLECTION' AND refund_id IS NULL AND amount>=0)
    OR (event_type='REFUND' AND refund_id IS NOT NULL AND amount<0)),
 CHECK((policy_id IS NULL AND rate IS NULL AND commission IS NULL)
    OR (policy_id IS NOT NULL AND rate IS NOT NULL AND commission IS NOT NULL)),
 CHECK(channel IN ('PLATFORM_MEDIATED_SIMULATED','OPERATOR_RECORDED_SIMULATED','UNKNOWN_OR_UNVERIFIED')),
 CHECK(currency='VND' AND verification='SIMULATED')
);
CREATE TABLE financial_runs (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 operator_id BIGINT NOT NULL,
 start_date DATE NOT NULL,
 end_date DATE NOT NULL,
 status VARCHAR(30) NOT NULL,
 source_count INT NOT NULL,
 projected_count INT NOT NULL,
 exception_count INT NOT NULL,
 created_by BIGINT NOT NULL,
 started_at DATETIME(6) NOT NULL,
 completed_at DATETIME(6) NOT NULL,
 snapshot LONGTEXT NOT NULL,
 INDEX ix_finance_runs(operator_id,id)
);
CREATE TABLE financial_exceptions (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 run_id BIGINT NOT NULL,
 source_key VARCHAR(80) NOT NULL,
 reason VARCHAR(60) NOT NULL,
 INDEX ix_finance_exceptions(run_id,id),
 FOREIGN KEY(run_id) REFERENCES financial_runs(id)
);
CREATE TABLE financial_statements (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 operator_id BIGINT NOT NULL,
 run_id BIGINT NOT NULL UNIQUE,
 created_by BIGINT NOT NULL,
 issued_at DATETIME(6) NOT NULL,
 snapshot LONGTEXT NOT NULL,
 INDEX ix_finance_statements(operator_id,id),
 FOREIGN KEY(run_id) REFERENCES financial_runs(id)
);
DELIMITER $$
CREATE TRIGGER commission_policy_immutable BEFORE UPDATE ON commission_policies FOR EACH ROW
BEGIN
 IF NOT(OLD.operator_id<=>NEW.operator_id) OR NOT(OLD.rate<=>NEW.rate)
    OR NOT(OLD.valid_from<=>NEW.valid_from) OR NOT(OLD.created_by<=>NEW.created_by)
    OR NOT(OLD.reason<=>NEW.reason) OR NOT(OLD.created_at<=>NEW.created_at)
    OR (OLD.state='ACTIVE' AND (NEW.state<>'ACTIVE' OR NOT(OLD.activated_at<=>NEW.activated_at)))
    OR (NOT(OLD.valid_to<=>NEW.valid_to) AND (NEW.valid_to IS NULL OR NEW.valid_to<=UTC_TIMESTAMP(6)
        OR (OLD.valid_to IS NOT NULL AND NEW.valid_to>OLD.valid_to))) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Commission policy history is immutable';
 END IF;
END$$
CREATE TRIGGER commission_policy_no_delete BEFORE DELETE ON commission_policies FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Commission policy history is immutable'; END$$
CREATE TRIGGER financial_event_no_update BEFORE UPDATE ON financial_events FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Financial events are append only'; END$$
CREATE TRIGGER financial_event_no_delete BEFORE DELETE ON financial_events FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Financial events are append only'; END$$
CREATE TRIGGER financial_statement_no_update BEFORE UPDATE ON financial_statements FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Issued statements are immutable'; END$$
CREATE TRIGGER financial_statement_no_delete BEFORE DELETE ON financial_statements FOR EACH ROW
BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Issued statements are immutable'; END$$
DELIMITER ;
