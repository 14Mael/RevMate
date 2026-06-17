package com.team.study.service;

import com.team.study.dto.request.LoginRequest;
import com.team.study.dto.request.RegisterRequest;
import com.team.study.dto.response.LoginResponse;

public interface AuthService {
    void register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
}
