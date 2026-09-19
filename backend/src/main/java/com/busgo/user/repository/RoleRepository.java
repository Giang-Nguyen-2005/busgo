package com.busgo.user.repository;

import com.busgo.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    java.util.Optional<Role> findByCode(com.busgo.user.entity.RoleCode code);
}
