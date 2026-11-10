package com.pawnmaster.api.security;

import com.pawnmaster.api.modules.user.RoleEnum;
import com.pawnmaster.api.modules.user.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String fullName;
    private final RoleEnum role;
    private final Long branchId;
    private final String branchCode;
    private final String branchName;
    private final String provinceOrCity;
    private final boolean active;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal from(UserEntity user) {
        Long branchId = null;
        String branchCode = null;
        String branchName = null;
        String provinceOrCity = null;

        if (user.getBranch() != null) {
            branchId = user.getBranch().getId();
            branchCode = user.getBranch().getBranchCode();
            branchName = user.getBranch().getBranchName();
            if (user.getBranch().getProvince() != null) {
                provinceOrCity = user.getBranch().getProvince().getName();
            } else {
                provinceOrCity = user.getBranch().getProvinceCode();
            }
        }

        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );

        return UserPrincipal.builder()
                .id(user.getId())
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .fullName(user.getFullName())
                .role(user.getRole())
                .branchId(branchId)
                .branchCode(branchCode)
                .branchName(branchName)
                .provinceOrCity(provinceOrCity)
                .active(user.isActive())
                .authorities(authorities)
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
