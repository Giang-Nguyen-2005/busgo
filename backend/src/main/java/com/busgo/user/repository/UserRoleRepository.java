package com.busgo.user.repository;
import com.busgo.user.entity.UserRole;
import com.busgo.user.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {}
