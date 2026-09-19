package com.busgo.operator.repository;

import com.busgo.operator.entity.OperatorStaff;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorStaffRepository extends JpaRepository<OperatorStaff, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select s from OperatorStaff s
            join fetch s.operator o
            where s.user.id = :userId and s.status = com.busgo.common.entity.ActiveStatus.ACTIVE
              and o.status = com.busgo.operator.entity.OperatorStatus.ACTIVE
            """)
    java.util.List<OperatorStaff> findActiveByUserId(
            @org.springframework.data.repository.query.Param("userId") Long userId);
}
