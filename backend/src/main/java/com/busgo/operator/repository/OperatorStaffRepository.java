package com.busgo.operator.repository;

import com.busgo.operator.entity.OperatorStaff;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.user.entity.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperatorStaffRepository extends JpaRepository<OperatorStaff, Long> {
    java.util.List<OperatorStaff> findByUserId(Long userId);

    @org.springframework.data.jpa.repository.Query("""
            select s from OperatorStaff s
            join fetch s.operator o
            where s.user.id = :userId and s.status = com.busgo.common.entity.ActiveStatus.ACTIVE
              and o.status = com.busgo.operator.entity.OperatorStatus.ACTIVE
            """)
    java.util.List<OperatorStaff> findActiveByUserId(
            @org.springframework.data.repository.query.Param("userId") Long userId);

    java.util.Optional<OperatorStaff> findByIdAndOperatorId(Long id, Long operatorId);
    boolean existsByOperatorIdAndStaffCodeIgnoreCase(Long operatorId, String staffCode);
    boolean existsByOperatorIdAndStaffCodeIgnoreCaseAndIdNot(Long operatorId, String staffCode, Long id);

    @Query("""
            select (count(s) > 0) from OperatorStaff s
            where s.user.id = :userId and s.operator.id <> :operatorId
              and s.status = com.busgo.common.entity.ActiveStatus.ACTIVE
            """)
    boolean hasOtherActiveMembership(@Param("userId") Long userId,
            @Param("operatorId") Long operatorId);

    @Query("""
            select count(distinct s.id) from OperatorStaff s
            join UserRole ur on ur.user = s.user
            where s.operator.id = :operatorId
              and s.status = com.busgo.common.entity.ActiveStatus.ACTIVE
              and s.user.status = com.busgo.user.entity.UserStatus.ACTIVE
              and s.user.deletedAt is null
              and ur.role.code = com.busgo.user.entity.RoleCode.OPERATOR_ADMIN
            """)
    long countActiveLoginCapableAdmins(@Param("operatorId") Long operatorId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct s from OperatorStaff s
            join fetch s.user u
            join UserRole ur on ur.user = u
            where s.operator.id = :operatorId
              and s.status = com.busgo.common.entity.ActiveStatus.ACTIVE
              and u.status = com.busgo.user.entity.UserStatus.ACTIVE
              and u.deletedAt is null
              and ur.role.code = com.busgo.user.entity.RoleCode.OPERATOR_ADMIN
            """)
    java.util.List<OperatorStaff> lockActiveLoginCapableAdmins(@Param("operatorId") Long operatorId);
}
