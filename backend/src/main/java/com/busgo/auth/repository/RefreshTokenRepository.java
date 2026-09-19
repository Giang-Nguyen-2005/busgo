package com.busgo.auth.repository;

import com.busgo.auth.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHashAndUserId(String tokenHash, Long userId);

    @Modifying
    @Query("delete from RefreshToken r where r.user.id = :userId")
    void revokeAllByUserId(@Param("userId") Long userId);
}
