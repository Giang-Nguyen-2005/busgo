package com.busgo;

import com.busgo.auth.PasswordPolicy;
import com.busgo.common.security.*;
import com.busgo.user.entity.RoleCode;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class AuthenticationTest extends JwtTestSupport {
    private final Instant now=Instant.parse("2026-09-19T00:00:00Z");
    private final CurrentUser user=new CurrentUser(7L,List.of(RoleCode.CUSTOMER));
    private JwtService service(Instant time) {
        return new JwtService(new JwtProperties(SECRET,"busgo",Duration.ofHours(1),Duration.ofDays(7)),Clock.fixed(time,ZoneOffset.UTC));
    }
    @Test void validatesSignatureTypeAndExpiry() {
        var jwt=service(now);
        String access=jwt.issue(user,"access");
        assertThat(jwt.validate(access,"access").getSubject()).isEqualTo("7");
        assertThatThrownBy(()->jwt.validate(access,"refresh")).isInstanceOfSatisfying(AuthenticationFailure.class,e->assertThat(e.getCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
        assertThatThrownBy(()->service(now.plusSeconds(3600)).validate(access,"access")).isInstanceOfSatisfying(AuthenticationFailure.class,e->assertThat(e.getCode()).isEqualTo("ACCESS_TOKEN_EXPIRED"));
        assertThatThrownBy(()->jwt.validate(access.substring(0,access.lastIndexOf('.')+1)+"A".repeat(43),"access")).isInstanceOf(AuthenticationFailure.class);
        assertThatThrownBy(()->service(now.minusSeconds(1)).validate(access,"access")).isInstanceOf(AuthenticationFailure.class);
    }
    @Test void rejectsExpiredRefresh() {
        String refresh=service(now).issue(user,"refresh");
        assertThatThrownBy(()->service(now.plus(Duration.ofDays(7))).validate(refresh,"refresh"))
                .isInstanceOfSatisfying(AuthenticationFailure.class,e->assertThat(e.getCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
    }
    @Test void rejectsWrongIssuerAndMissingExpiryEvenWithValidSignature() throws Exception {
        var otherIssuer=new JwtService(new JwtProperties(SECRET,"other",Duration.ofHours(1),Duration.ofDays(7)),Clock.fixed(now,ZoneOffset.UTC));
        assertThatThrownBy(()->service(now).validate(otherIssuer.issue(user,"access"),"access")).isInstanceOf(AuthenticationFailure.class);
        var claims=new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("busgo").subject("7").issueTime(java.util.Date.from(now))
                .jwtID(java.util.UUID.randomUUID().toString()).claim("userId",7L).claim("tokenUse","access").build();
        var token=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),claims);
        token.sign(new com.nimbusds.jose.crypto.MACSigner(java.util.Base64.getDecoder().decode(SECRET)));
        assertThatThrownBy(()->service(now).validate(token.serialize(),"access")).isInstanceOf(AuthenticationFailure.class);
    }
    @Test void requiresStrongConfiguredSecretAndLongerRefreshLifetime() {
        assertThatThrownBy(()->new JwtService(new JwtProperties("short","busgo",Duration.ofHours(1),Duration.ofDays(7)),Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new JwtService(new JwtProperties(SECRET,"busgo",Duration.ofHours(1),Duration.ofMinutes(1)),Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"       ","1234567"})
    void rejectsInvalidPasswords(String password) { assertThatThrownBy(()->PasswordPolicy.validate(password)).isInstanceOf(com.busgo.common.exception.BusinessException.class); }
    @Test void enforcesUtf8ByteLimitWithoutCompositionRules() {
        PasswordPolicy.validate("abcdefgh"); PasswordPolicy.validate("a".repeat(72)); PasswordPolicy.validate("é".repeat(36));
        assertThatThrownBy(()->PasswordPolicy.validate("a".repeat(73))).isInstanceOf(com.busgo.common.exception.BusinessException.class);
        assertThatThrownBy(()->PasswordPolicy.validate("é".repeat(37))).isInstanceOf(com.busgo.common.exception.BusinessException.class);
    }
}
