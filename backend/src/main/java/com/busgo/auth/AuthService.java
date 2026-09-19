package com.busgo.auth;

import com.busgo.auth.AuthDtos.*;
import com.busgo.auth.entity.RefreshToken;
import com.busgo.auth.repository.RefreshTokenRepository;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.*;
import com.busgo.user.entity.*;
import com.busgo.user.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final RefreshTokenRepository refreshTokens;
    private final UserIdentityService identities;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authentication;
    private final JwtService jwt;
    private final Clock clock;
    public AuthService(UserRepository users, RoleRepository roles, UserRoleRepository userRoles,
            RefreshTokenRepository refreshTokens, UserIdentityService identities, PasswordEncoder passwords,
            AuthenticationManager authentication, JwtService jwt, Clock clock) {
        this.users=users; this.roles=roles; this.userRoles=userRoles; this.refreshTokens=refreshTokens;
        this.identities=identities; this.passwords=passwords; this.authentication=authentication; this.jwt=jwt; this.clock=clock;
    }
    @Transactional
    public Registration register(RegisterRequest request) {
        PasswordPolicy.validate(request.password());
        if (users.existsByEmail(request.email())) throw duplicateEmail();
        Role role=roles.findByCode(RoleCode.CUSTOMER).orElseThrow(() -> new IllegalStateException("CUSTOMER role seed missing."));
        User user=new User();
        user.setEmail(request.email()); user.setFullName(request.fullName().strip()); user.setPhone(request.phone().strip());
        user.setPasswordHash(passwords.encode(request.password())); user.setStatus(UserStatus.ACTIVE);
        try { users.saveAndFlush(user); }
        catch (DataIntegrityViolationException ex) {
            Throwable cause=ex;
            while (cause!=null) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && violation.getConstraintName()!=null && violation.getConstraintName().contains("uk_users_email")) throw duplicateEmail();
                cause=cause.getCause();
            }
            throw ex;
        }
        userRoles.save(new UserRole(user,role));
        return new Registration(user.getId(),user.getFullName(),user.getEmail(),user.getPhone(),RoleCode.CUSTOMER);
    }
    @Transactional
    public LoginResponse login(LoginRequest request) {
        var result=authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.email(),request.password()));
        CurrentUser principal=(CurrentUser) result.getPrincipal();
        User user=users.findById(principal.id()).orElseThrow(AuthenticationFailure::credentials);
        Tokens tokens=issue(user,principal);
        return new LoginResponse(tokens.accessToken(),tokens.refreshToken(),tokens.expiresIn(),
                new LoginUser(user.getId(),user.getFullName(),user.getEmail(),principal.roles()));
    }
    @Transactional
    public Tokens refresh(RefreshRequest request) {
        var validated=jwt.validate(request.refreshToken(),"refresh");
        // Lock the user before reading token state: rotation and password changes serialize.
        User user=users.findLockedById(Long.valueOf(validated.getSubject())).orElseThrow(AuthenticationFailure::refresh);
        if (!UserIdentityService.active(user)) throw AuthenticationFailure.refresh();
        RefreshToken saved=refreshTokens.findByTokenHashAndUserId(hash(request.refreshToken()),user.getId())
                .orElseThrow(AuthenticationFailure::refresh);
        if (!saved.getExpiresAt().isAfter(LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC))) throw AuthenticationFailure.refresh();
        refreshTokens.delete(saved);
        return issue(user,identities.identity(user));
    }
    private Tokens issue(User user,CurrentUser principal) {
        String access=jwt.issue(principal,"access");
        String refresh=jwt.issue(principal,"refresh");
        RefreshToken stored=new RefreshToken(); stored.setUser(user); stored.setTokenHash(hash(refresh));
        stored.setExpiresAt(LocalDateTime.ofInstant(jwt.validate(refresh,"refresh").getExpiresAt(),ZoneOffset.UTC));
        refreshTokens.save(stored);
        return new Tokens(access,refresh,jwt.accessLifetimeSeconds());
    }
    @Transactional(readOnly=true)
    public Profile profile(CurrentUser principal) {
        User user=users.findById(principal.id()).orElseThrow(AuthenticationFailure::credentials);
        if (!UserIdentityService.active(user)) throw AuthenticationFailure.credentials();
        return profile(user);
    }
    @Transactional
    public Profile updateProfile(CurrentUser principal,ProfileRequest request) {
        User user=lockedActive(principal);
        if (request.fullName()!=null) user.setFullName(request.fullName().strip());
        if (request.phone()!=null) user.setPhone(request.phone().strip());
        return profile(user);
    }
    @Transactional
    public void changePassword(CurrentUser principal,ChangePasswordRequest request) {
        User user=lockedActive(principal);
        if (!PasswordPolicy.bcryptCompatible(request.currentPassword()) || !passwords.matches(request.currentPassword(),user.getPasswordHash()))
            throw AuthenticationFailure.credentials();
        PasswordPolicy.validate(request.newPassword());
        user.setPasswordHash(passwords.encode(request.newPassword()));
        refreshTokens.revokeAllByUserId(user.getId());
    }
    private User lockedActive(CurrentUser principal) {
        User user=users.findLockedById(principal.id()).orElseThrow(AuthenticationFailure::credentials);
        if (!UserIdentityService.active(user)) throw AuthenticationFailure.credentials();
        return user;
    }
    private Profile profile(User user) {
        return new Profile(user.getId(),user.getFullName(),user.getEmail(),user.getPhone(),identities.identity(user).roles());
    }
    private static BusinessException duplicateEmail() { return new BusinessException("EMAIL_ALREADY_EXISTS","Email is already registered."); }
    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable."); }
    }
}
