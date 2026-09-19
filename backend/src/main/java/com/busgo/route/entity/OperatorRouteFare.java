package com.busgo.route.entity;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** M3 must validate route membership and forward stop order before writing fares. */
@Getter
@Setter
@Entity
@Table(name = "operator_route_fares")
public class OperatorRouteFare extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_route_id", nullable = false)
    private OperatorRoute operatorRoute;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_route_stop_id", nullable = false)
    private RouteStop fromRouteStop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_route_stop_id", nullable = false)
    private RouteStop toRouteStop;

    @Column(name = "price", precision = 12, scale = 2, nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 30, nullable = false)
    private ActiveStatus status;
}
