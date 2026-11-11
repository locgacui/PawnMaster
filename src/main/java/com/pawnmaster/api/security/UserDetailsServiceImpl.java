package com.pawnmaster.api.security;

import com.pawnmaster.api.modules.user.UserEntity;
import com.pawnmaster.api.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản với username: " + username));

        return UserPrincipal.from(user);
    }
}
