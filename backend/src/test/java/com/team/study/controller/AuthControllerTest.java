package com.team.study.controller;

import com.team.study.common.Result;
import com.team.study.dto.request.LoginRequest;
import com.team.study.dto.request.RegisterRequest;
import com.team.study.dto.response.LoginResponse;
import com.team.study.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthControllerTest {

    @Test
    void registerReturnsSuccess() {
        StubAuthService authService = new StubAuthService();
        AuthController controller = new AuthController(authService);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("secret123");

        Result<Void> result = controller.register(request);

        assertThat(result.getCode()).isZero();
        assertThat(authService.registerCalled).isTrue();
        assertThat(authService.lastRegisterRequest.getUsername()).isEqualTo("alice");
    }

    @Test
    void registerPropagatesExceptionForDuplicateUser() {
        StubAuthService authService = new StubAuthService();
        authService.registerShouldFail = true;
        AuthController controller = new AuthController(authService);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing");
        request.setPassword("secret123");

        assertThatThrownBy(() -> controller.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void loginReturnsToken() {
        StubAuthService authService = new StubAuthService();
        AuthController controller = new AuthController(authService);

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret123");

        Result<LoginResponse> result = controller.login(request);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getToken()).isEqualTo("fake-jwt-token");
        assertThat(authService.lastLoginRequest.getUsername()).isEqualTo("alice");
    }

    @Test
    void loginWithWrongPasswordPropagatesException() {
        StubAuthService authService = new StubAuthService();
        authService.loginShouldFail = true;
        AuthController controller = new AuthController(authService);

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong");

        assertThatThrownBy(() -> controller.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    private static class StubAuthService implements AuthService {
        boolean registerCalled;
        boolean registerShouldFail;
        RegisterRequest lastRegisterRequest;
        LoginRequest lastLoginRequest;
        boolean loginShouldFail;

        @Override
        public void register(RegisterRequest request) {
            registerCalled = true;
            lastRegisterRequest = request;
            if (registerShouldFail) {
                throw new IllegalArgumentException("用户名已存在");
            }
        }

        @Override
        public LoginResponse login(LoginRequest request) {
            lastLoginRequest = request;
            if (loginShouldFail) {
                throw new BadCredentialsException("用户名或密码错误");
            }
            return new LoginResponse("fake-jwt-token");
        }
    }
}
