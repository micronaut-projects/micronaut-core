/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.security.endpoints;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.token.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.token.validator.TokenValidator;
import io.micronaut.security.token.render.AccessRefreshToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Singleton;
import java.util.Map;

import static io.micronaut.http.HttpResponse.status;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
@Controller(OauthController.CONTROLLER_PATH)
@Requires(property = SecurityEndpointsConfigurationProperties.PREFIX + ".refresh", value = "true")
public class OauthController implements OauthControllerApi {

    public static final String CONTROLLER_PATH = "/oauth";
    public static final String ACCESSTOKEN_PATH = "/access_token";

    private static final Logger log = LoggerFactory.getLogger(OauthController.class);
    protected final TokenValidator tokenValidator;
    protected final AccessRefreshTokenGenerator accessRefreshTokenGenerator;

    public OauthController(TokenValidator tokenValidator,
                           AccessRefreshTokenGenerator accessRefreshTokenGenerator) {
        this.tokenValidator = tokenValidator;
        this.accessRefreshTokenGenerator = accessRefreshTokenGenerator;
    }

    //@Consumes({MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON})
    @Override
    @Post(OauthController.ACCESSTOKEN_PATH)
    public HttpResponse<AccessRefreshToken> token(TokenRefreshRequest tokenRefreshRequest) {
        if ( tokenRefreshRequest.getGrant_type() == null ||
                tokenRefreshRequest.getRefresh_token() == null ||
                !tokenRefreshRequest.getGrant_type().equals("refresh_token") ) {
            return status(HttpStatus.BAD_REQUEST);
        }
        log.info("grantType: {} refreshToken: {}", tokenRefreshRequest.getGrant_type(), tokenRefreshRequest.getRefresh_token());

        Map<String, Object> claims = tokenValidator.validateTokenAndGetClaims(tokenRefreshRequest.getRefresh_token());
        if ( claims == null ) {
            return status(HttpStatus.FORBIDDEN);
        }
        return accessRefreshTokenGenerator.generate(tokenRefreshRequest.getRefresh_token(), claims);
    }
}
