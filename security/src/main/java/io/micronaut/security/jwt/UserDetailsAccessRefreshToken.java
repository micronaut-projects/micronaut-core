package io.micronaut.security.jwt;

import java.util.List;

public class UserDetailsAccessRefreshToken implements AccessRefreshToken {

    private String username;
    private List<String> roles;
    private String accessToken;
    private String refreshToken;

    public UserDetailsAccessRefreshToken() {}

    public UserDetailsAccessRefreshToken(String username, List<String> roles, String accessToken, String refreshToken) {
        this.username = username;
        this.roles = roles;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    @Override
    public String getAccessToken() {
        return accessToken;
    }

    @Override
    public String getRefreshToken() {
        return refreshToken;
    }
}
