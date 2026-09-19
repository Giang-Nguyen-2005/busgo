package com.busgo.operator.entity;

import com.busgo.common.entity.AuditedEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "transport_operators")
public class TransportOperator extends AuditedEntity {
    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "code", length = 50, nullable = false)
    private String code;

    @Column(name = "phone", length = 20, nullable = true)
    private String phone;

    @Column(name = "email", length = 150, nullable = true)
    private String email;

    @Column(name = "address", length = 255, nullable = true)
    private String address;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "status", length = 30, nullable = false)
    private OperatorStatus status;
}
