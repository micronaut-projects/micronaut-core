package io.micronaut.security.controllers;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.token.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.config.JwtConfiguration;
import io.micronaut.security.token.validator.TokenValidator;
import io.micronaut.security.token.AccessRefreshToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
@Controller("/oauth")
@Requires(property = JwtConfiguration.PREFIX + ".refresh", value = "true")
public class OauthController {
    private static final Logger log = LoggerFactory.getLogger(OauthController.class);
    protected final TokenValidator tokenValidator;
    protected final AccessRefreshTokenGenerator accessRefreshTokenGenerator;

    public OauthController(TokenValidator tokenValidator, AccessRefreshTokenGenerator accessRefreshTokenGenerator ) {
        this.tokenValidator = tokenValidator;
        this.accessRefreshTokenGenerator = accessRefreshTokenGenerator;
    }

    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/access_token")
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
