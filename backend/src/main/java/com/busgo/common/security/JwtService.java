package com.busgo.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtProperties properties;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        byte[] secret;
        try { secret = Base64.getDecoder().decode(properties.secret()); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("JWT_SECRET must be Base64 encoded."); }
        if (secret.length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 random bytes.");
        if (properties.issuer() == null || properties.issuer().isBlank()
                || properties.accessTtl() == null || properties.refreshTtl() == null
                || properties.accessTtl().getSeconds() < 1
                || properties.refreshTtl().compareTo(properties.accessTtl()) <= 0) {
            throw new IllegalArgumentException("JWT lifetimes must be positive and refresh lifetime must exceed access lifetime.");
        }
        var key = new SecretKeySpec(secret, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // Signature/algorithm verification stays in Nimbus. Validate mandatory claims below
        // using one injected clock, so expiry has a precise API error and deterministic tests.
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
    }

    public String issue(CurrentUser user, String type) {
        if (!type.equals("access") && !type.equals("refresh")) throw new IllegalArgumentException("Invalid token type.");
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(user.getName())
                .issuedAt(now).expiresAt(now.plus(type.equals("access") ? properties.accessTtl() : properties.refreshTtl()))
                .id(UUID.randomUUID().toString()).claim("tokenUse", type).claim("userId", user.id());
        if (type.equals("access")) claims.claim("roles", user.roles().stream().map(Enum::name).toList());
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims.build())).getTokenValue();
    }

    public Jwt validate(String token, String expectedType) {
        try {
            if (token == null || token.length() > 8192) throw invalid(expectedType);
            Jwt jwt = decoder.decode(token);
            Instant now = clock.instant();
            Long userId = Long.valueOf(jwt.getSubject());
            if (userId <= 0 || !userId.equals(Long.valueOf(jwt.getClaim("userId").toString()))
                    || !properties.issuer().equals(jwt.getClaimAsString("iss"))
                    || !expectedType.equals(jwt.getClaimAsString("tokenUse"))
                    || jwt.getId() == null || jwt.getId().isBlank()
                    || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null
                    || jwt.getIssuedAt().isAfter(now)
                    || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    || (jwt.getNotBefore() != null && jwt.getNotBefore().isAfter(now))) throw invalid(expectedType);
            if (!jwt.getExpiresAt().isAfter(now)) {
                if (expectedType.equals("refresh")) throw AuthenticationFailure.refresh();
                throw new AuthenticationFailure("ACCESS_TOKEN_EXPIRED", "Access token has expired.");
            }
            return jwt;
        } catch (AuthenticationFailure ex) { throw ex; }
        catch (RuntimeException ex) { throw invalid(expectedType); }
    }

    private AuthenticationFailure invalid(String type) {
        return type.equals("refresh") ? AuthenticationFailure.refresh() : AuthenticationFailure.credentials();
    }

    public long accessLifetimeSeconds() { return properties.accessTtl().getSeconds(); }
}
