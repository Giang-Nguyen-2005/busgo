ALTER TABLE operator_staff
    ADD CONSTRAINT uk_staff_operator_code UNIQUE (operator_id, staff_code);
