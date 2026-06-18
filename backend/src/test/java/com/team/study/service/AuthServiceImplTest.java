package com.team.study.service;

import com.team.study.dto.request.LoginRequest;
import com.team.study.dto.request.RegisterRequest;
import com.team.study.dto.response.LoginResponse;
import com.team.study.entity.User;
import com.team.study.repository.UserRepository;
import com.team.study.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest registerRequest(String username, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest("alice", "pw123456")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void registerEncodesPasswordBeforeSaving() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(passwordEncoder.encode("pw123456")).thenReturn("ENC");

        authService.register(registerRequest("bob", "pw123456"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("bob");
        assertThat(captor.getValue().getPassword()).isEqualTo("ENC");
    }

    @Test
    void loginWithWrongPasswordThrowsBadCredentials() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword("ENC");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "ENC")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("alice", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginWithCorrectPasswordReturnsToken() {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setPassword("ENC");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw123456", "ENC")).thenReturn(true);
        when(jwtTokenProvider.generateToken(7L, "alice")).thenReturn("TOKEN");

        LoginResponse response = authService.login(loginRequest("alice", "pw123456"));

        assertThat(response.getToken()).isEqualTo("TOKEN");
    }
}
