package com.nightfall.englishnova.auth.service.impl;

import com.nightfall.englishnova.auth.domain.po.UserPo;
import com.nightfall.englishnova.auth.mapper.UserMapper;
import com.nightfall.englishnova.auth.service.JwtTokenService;
import com.nightfall.englishnova.auth.service.UserAvatarStorageService;
import com.nightfall.englishnova.auth.config.AuthServiceConfig;
import com.nightfall.englishnova.shared.dto.AuthTokenResponse;
import com.nightfall.englishnova.shared.dto.ChangePasswordRequest;
import com.nightfall.englishnova.shared.dto.LoginRequest;
import com.nightfall.englishnova.shared.dto.RegisterRequest;
import com.nightfall.englishnova.shared.enums.QuizOptionStrategy;
import com.nightfall.englishnova.shared.enums.UserStatus;
import com.nightfall.englishnova.shared.exception.ForbiddenException;
import com.nightfall.englishnova.shared.exception.UnauthorizedException;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private UserAvatarStorageService avatarStorageService;

    private PasswordEncoder passwordEncoder;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        authService = new AuthServiceImpl(
                userMapper,
                passwordEncoder,
                new AuthServiceConfig.PasswordSecurityProperties(4),
                jwtTokenService,
                avatarStorageService
        );
    }

    @Test
    void registerStoresBcryptHashInsteadOfPlaintext() {
        RegisterRequest request = new RegisterRequest("alice", "Alice@example.com", "Password123!");
        when(jwtTokenService.issueToken(42L, "alice")).thenReturn("token-42");
        doAnswer(invocation -> {
            UserPo user = invocation.getArgument(0);
            user.setId(42L);
            return 1;
        }).when(userMapper).insert(any(UserPo.class));

        AuthTokenResponse response = authService.register(request);

        ArgumentCaptor<UserPo> userCaptor = ArgumentCaptor.forClass(UserPo.class);
        verify(userMapper).insert(userCaptor.capture());
        UserPo savedUser = userCaptor.getValue();
        assertNotEquals("Password123!", savedUser.getPasswordHash());
        assertTrue(savedUser.getPasswordHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches("Password123!", savedUser.getPasswordHash()));
        assertEquals("token-42", response.accessToken());
        assertEquals(42L, response.user().id());
        assertEquals(QuizOptionStrategy.RANDOM, response.user().quizOptionStrategy());
        verify(userMapper, never()).countByUsername(any());
        verify(userMapper, never()).countByEmail(any());
    }

    @Test
    void registerTranslatesDuplicateUsernameErrors() {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "Password123!");
        when(userMapper.countByUsername("alice")).thenReturn(1);
        doAnswer(invocation -> {
            throw new DuplicateKeyException("duplicate username");
        }).when(userMapper).insert(any(UserPo.class));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> authService.register(request));

        assertEquals("Username already exists", exception.getMessage());
    }

    @Test
    void loginRejectsNonBcryptHashesWithoutCallingMatches() {
        UserPo user = activeUser(7L, "legacy-user", "legacy@example.com", "SYSTEM_EXTERNAL_IMPORT");
        when(userMapper.findByUsername("legacy-user")).thenReturn(user);

        assertThrows(UnauthorizedException.class, () -> authService.login(new LoginRequest("legacy-user", "secret")));

        verify(jwtTokenService, never()).issueToken(anyLong(), any());
    }

    @Test
    void loginAcceptsCorrectPassword() {
        String hash = passwordEncoder.encode("Password123!");
        UserPo user = activeUser(8L, "bob", "bob@example.com", hash);
        when(userMapper.findByUsername("bob")).thenReturn(user);
        when(jwtTokenService.issueToken(8L, "bob")).thenReturn("token-8");

        AuthTokenResponse response = authService.login(new LoginRequest("bob", "Password123!"));

        assertEquals("token-8", response.accessToken());
        assertEquals(8L, response.user().id());
        verify(userMapper, never()).updatePasswordHash(eq(8L), any(String.class));
    }

    @Test
    void loginUsesEmailLookup() {
        String hash = passwordEncoder.encode("Password123!");
        UserPo user = activeUser(12L, "mail-user", "mail@example.com", hash);
        when(userMapper.findByEmail("mail@example.com")).thenReturn(user);
        when(jwtTokenService.issueToken(12L, "mail-user")).thenReturn("token-12");

        AuthTokenResponse response = authService.login(new LoginRequest("mail@example.com", "Password123!"));

        assertEquals("token-12", response.accessToken());
        verify(userMapper).findByEmail("mail@example.com");
    }

    @Test
    void loginRehashesPasswordWhenStoredStrengthDiffers() {
        PasswordEncoder oldStrengthEncoder = new BCryptPasswordEncoder(6);
        String oldHash = oldStrengthEncoder.encode("Password123!");
        UserPo user = activeUser(13L, "rehash", "rehash@example.com", oldHash);
        when(userMapper.findByUsername("rehash")).thenReturn(user);
        when(jwtTokenService.issueToken(13L, "rehash")).thenReturn("token-13");
        doAnswer(invocation -> {
            user.setPasswordHash(invocation.getArgument(1));
            return 1;
        }).when(userMapper).updatePasswordHash(eq(13L), any(String.class));

        AuthTokenResponse response = authService.login(new LoginRequest("rehash", "Password123!"));

        assertEquals("token-13", response.accessToken());
        assertTrue(passwordEncoder.matches("Password123!", user.getPasswordHash()));
        verify(userMapper).updatePasswordHash(eq(13L), any(String.class));
    }

    @Test
    void changePasswordReplacesStoredHashAndInvalidatesOldPassword() {
        String originalHash = passwordEncoder.encode("OldPassword1!");
        UserPo user = activeUser(9L, "carol", "carol@example.com", originalHash);
        when(userMapper.selectById(9L)).thenReturn(user);
        when(userMapper.findByUsername("carol")).thenReturn(user);
        when(jwtTokenService.issueToken(9L, "carol")).thenReturn("token-9");
        doAnswer(invocation -> {
            user.setPasswordHash(invocation.getArgument(1));
            return 1;
        }).when(userMapper).updatePasswordHash(eq(9L), any(String.class));

        authService.changePassword(9L, new ChangePasswordRequest("OldPassword1!", "NewPassword2!"));

        assertThrows(UnauthorizedException.class, () -> authService.login(new LoginRequest("carol", "OldPassword1!")));
        AuthTokenResponse response = authService.login(new LoginRequest("carol", "NewPassword2!"));
        assertEquals("token-9", response.accessToken());
        verify(userMapper).updatePasswordHash(eq(9L), any(String.class));
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        UserPo user = activeUser(10L, "dave", "dave@example.com", passwordEncoder.encode("OldPassword1!"));
        when(userMapper.selectById(10L)).thenReturn(user);

        assertThrows(UnauthorizedException.class,
                () -> authService.changePassword(10L, new ChangePasswordRequest("wrong", "NewPassword2!")));

        verify(userMapper, never()).updatePasswordHash(anyLong(), any(String.class));
    }

    @Test
    void changePasswordRejectsDisabledUsers() {
        UserPo user = new UserPo(11L, "erin", "erin@example.com", null,
                QuizOptionStrategy.RANDOM.name(), passwordEncoder.encode("OldPassword1!"), UserStatus.DISABLED.name());
        when(userMapper.selectById(11L)).thenReturn(user);

        assertThrows(ForbiddenException.class,
                () -> authService.changePassword(11L, new ChangePasswordRequest("OldPassword1!", "NewPassword2!")));
    }

    private UserPo activeUser(long id, String username, String email, String passwordHash) {
        return new UserPo(id, username, email, null,
                QuizOptionStrategy.RANDOM.name(), passwordHash, UserStatus.ACTIVE.name());
    }
}
