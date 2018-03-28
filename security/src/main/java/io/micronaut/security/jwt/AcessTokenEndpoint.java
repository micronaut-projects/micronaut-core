package io.micronaut.security.jwt;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.management.endpoint.Endpoint;
import io.micronaut.management.endpoint.Write;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Endpoint("accessToken")
public class AcessTokenEndpoint {
    private static final Logger log = LoggerFactory.getLogger(AcessTokenEndpoint.class);
    protected final TokenValidator tokenValidator;
    protected final AccessRefreshTokenGenerator accessRefreshTokenGenerator;

    public AcessTokenEndpoint(TokenValidator tokenValidator, AccessRefreshTokenGenerator accessRefreshTokenGenerator ) {
        this.tokenValidator = tokenValidator;
        this.accessRefreshTokenGenerator = accessRefreshTokenGenerator;
    }
    @Write(consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.APPLICATION_JSON)
    HttpResponse accessToken(String grant_type, String refresh_token) {
        if ( !grant_type.equals("refresh_token") ) {
            return HttpResponse.status(HttpStatus.BAD_REQUEST);
        }
        log.info("grantType: {} refreshToken: {}", grant_type, refresh_token);
        Map<String, Object> claims = tokenValidator.validateTokenAndGetClaims(refresh_token);
        if ( claims == null ) {
            return HttpResponse.status(HttpStatus.FORBIDDEN);
        }
        AccessRefreshToken accessRefreshToken = accessRefreshTokenGenerator.generate(refresh_token, claims);
        return HttpResponse.status(HttpStatus.OK).body(accessRefreshToken);
    }
}
