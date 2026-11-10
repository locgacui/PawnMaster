package com.pawnmaster.api.modules.user;

import com.pawnmaster.api.modules.user.dto.AuthResponse;
import com.pawnmaster.api.modules.user.dto.LoginRequest;
import com.pawnmaster.api.modules.user.dto.UserResponse;

public interface AuthService {

    AuthResponse login(LoginRequest request);

    UserResponse getCurrentUser(String username);
}
