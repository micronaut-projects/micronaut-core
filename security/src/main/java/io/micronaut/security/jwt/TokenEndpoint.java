package io.micronaut.security.jwt;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.management.endpoint.Endpoint;
import io.micronaut.management.endpoint.Write;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Endpoint("token")
public class TokenEndpoint {
    private static final Logger log = LoggerFactory.getLogger(TokenEndpoint.class);
    protected final TokenValidator tokenValidator;
    protected final AccessRefreshTokenGenerator accessRefreshTokenGenerator;

    public TokenEndpoint(TokenValidator tokenValidator, AccessRefreshTokenGenerator accessRefreshTokenGenerator ) {
        this.tokenValidator = tokenValidator;
        this.accessRefreshTokenGenerator = accessRefreshTokenGenerator;
    }
    @Write(consumes = MediaType.APPLICATION_FORM_URLENCODED)
    HttpResponse token(TokenRefreshRequest tokenRefreshRequest) {
        if ( tokenRefreshRequest.getGrant_type() == null ||
                tokenRefreshRequest.getRefresh_token() == null ||
                !tokenRefreshRequest.getGrant_type().equals("refresh_token") ) {
            return HttpResponse.status(HttpStatus.BAD_REQUEST);
        }
        log.info("grantType: {} refreshToken: {}", tokenRefreshRequest.getGrant_type(), tokenRefreshRequest.getRefresh_token());
        Map<String, Object> claims = tokenValidator.validateTokenAndGetClaims(tokenRefreshRequest.getRefresh_token());
        if ( claims == null ) {
            return HttpResponse.status(HttpStatus.FORBIDDEN);
        }
        AccessRefreshToken accessRefreshToken = accessRefreshTokenGenerator.generate(tokenRefreshRequest.getRefresh_token(), claims);
        return HttpResponse.status(HttpStatus.OK).body(accessRefreshToken);
    }
}
