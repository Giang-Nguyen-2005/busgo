package com.busgo.user.repository;
import com.busgo.user.entity.UserRole;
import com.busgo.user.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    @org.springframework.data.jpa.repository.Query("select ur.role.code from UserRole ur where ur.user.id = :userId order by ur.role.code")
    java.util.List<com.busgo.user.entity.RoleCode> findRoleCodesByUserId(
            @org.springframework.data.repository.query.Param("userId") Long userId);
}
