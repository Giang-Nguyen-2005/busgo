package com.busgo.operator;

import com.busgo.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class OperatorCommerceGuard {
    private final JdbcTemplate db;
    public OperatorCommerceGuard(JdbcTemplate db) { this.db=db; }
    /** Call only after existing trip/bus locks; never acquire those locks afterward. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireActive(long id) {
        String status=db.queryForObject("SELECT status FROM transport_operators WHERE id=? FOR UPDATE",String.class,id);
        if(!"ACTIVE".equals(status)) throw new BusinessException("OPERATOR_INACTIVE","Nhà xe đang tạm ngưng nhận giao dịch mới.");
    }
}
