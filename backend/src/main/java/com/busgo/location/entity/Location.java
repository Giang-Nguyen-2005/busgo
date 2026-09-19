package com.busgo.location.entity;

import com.busgo.common.entity.AuditedEntity;
import com.busgo.common.entity.ActiveStatus;
import java.math.BigDecimal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "locations")
public class Location extends AuditedEntity {
    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "province", length = 100, nullable = true)
    private String province;

    @Column(name = "district", length = 100, nullable = true)
    private String district;

    @Column(name = "address", length = 255, nullable = true)
    private String address;

    @Column(name = "latitude", precision = 10, scale = 7, nullable = true)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7, nullable = true)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "status", length = 30, nullable = false)
    private ActiveStatus status;
}
