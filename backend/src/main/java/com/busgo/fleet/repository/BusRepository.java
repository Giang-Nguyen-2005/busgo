package com.busgo.fleet.repository;

import com.busgo.fleet.entity.Bus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusRepository extends JpaRepository<Bus, Long> {
    java.util.Optional<Bus> findByLicensePlateIgnoreCase(String licensePlate);

    @org.springframework.data.jpa.repository.Query(value = """
            select b from Bus b join fetch b.busType bt
            where b.operator.id = :operatorId and b.deletedAt is null
              and (:status is null or b.status = :status)
              and (:busTypeId is null or bt.id = :busTypeId)
              and (:q = '' or lower(b.licensePlate) like lower(concat('%', :q, '%')))
            """, countQuery = """
            select count(b) from Bus b
            where b.operator.id = :operatorId and b.deletedAt is null
              and (:status is null or b.status = :status)
              and (:busTypeId is null or b.busType.id = :busTypeId)
              and (:q = '' or lower(b.licensePlate) like lower(concat('%', :q, '%')))
            """)
    org.springframework.data.domain.Page<Bus> search(
            @org.springframework.data.repository.query.Param("operatorId") Long operatorId,
            @org.springframework.data.repository.query.Param("status") com.busgo.fleet.entity.BusStatus status,
            @org.springframework.data.repository.query.Param("busTypeId") Long busTypeId,
            @org.springframework.data.repository.query.Param("q") String query,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            select b from Bus b join fetch b.busType
            where b.id = :id and b.operator.id = :operatorId and b.deletedAt is null
            """)
    java.util.Optional<Bus> findOwnedById(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("operatorId") Long operatorId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
            select b from Bus b join fetch b.busType
            where b.id = :id and b.operator.id = :operatorId and b.deletedAt is null
            """)
    java.util.Optional<Bus> findOwnedByIdForUpdate(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("operatorId") Long operatorId);

    boolean existsByLicensePlateIgnoreCase(String licensePlate);
    boolean existsByLicensePlateIgnoreCaseAndIdNot(String licensePlate, Long id);
}
