package com.busgo.location.repository;

import com.busgo.location.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {
    java.util.Optional<Location> findFirstByNameAndProvince(String name, String province);

    @org.springframework.data.jpa.repository.Query("""
            select l from Location l
            where l.status = com.busgo.common.entity.ActiveStatus.ACTIVE
              and (:q = '' or lower(l.name) like lower(concat('%', :q, '%'))
                or lower(coalesce(l.province, '')) like lower(concat('%', :q, '%'))
                or lower(coalesce(l.district, '')) like lower(concat('%', :q, '%')))
            order by l.name, l.id
            """)
    java.util.List<Location> searchActive(@org.springframework.data.repository.query.Param("q") String query,
            org.springframework.data.domain.Pageable pageable);

    java.util.Optional<Location> findByIdAndStatus(Long id, com.busgo.common.entity.ActiveStatus status);
}
