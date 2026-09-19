package com.busgo.user.entity;

import com.busgo.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "code", length = 50, nullable = false)
    private RoleCode code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;
}
