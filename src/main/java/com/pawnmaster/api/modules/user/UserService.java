package com.pawnmaster.api.modules.user;

import com.pawnmaster.api.modules.user.dto.UserCreateRequest;
import com.pawnmaster.api.modules.user.dto.UserResponse;
import com.pawnmaster.api.modules.user.dto.UserUpdateRequest;

import java.util.List;

public interface UserService {

    UserResponse createUser(UserCreateRequest request);

    UserResponse updateUser(Long id, UserUpdateRequest request);

    UserResponse getUserById(Long id);

    UserResponse getUserByUsername(String username);

    List<UserResponse> getUsersByBranch(Long branchId);

    List<UserResponse> getAllUsers();

    void deleteUser(Long id);
}
