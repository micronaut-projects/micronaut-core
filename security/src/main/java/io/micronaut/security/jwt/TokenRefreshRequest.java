package io.micronaut.security.jwt;

public class TokenRefreshRequest {
    String grant_type;
    String refresh_token;

    public TokenRefreshRequest() {}

    public TokenRefreshRequest(String grant_type, String refresh_token) {
        this.grant_type = grant_type;
        this.refresh_token = refresh_token;
    }

    public String getGrant_type() {
        return grant_type;
    }

    public void setGrant_type(String grant_type) {
        this.grant_type = grant_type;
    }

    public String getRefresh_token() {
        return refresh_token;
    }

    public void setRefresh_token(String refresh_token) {
        this.refresh_token = refresh_token;
    }
}
