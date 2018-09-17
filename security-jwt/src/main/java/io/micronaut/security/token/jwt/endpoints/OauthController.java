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

package io.micronaut.security.token.jwt.endpoints;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.jwt.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.token.jwt.validator.JwtTokenValidator;
import io.micronaut.security.token.validator.TokenValidator;
import io.micronaut.security.token.jwt.render.AccessRefreshToken;
import io.micronaut.validation.Validated;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import java.util.Map;
import java.util.Optional;

/**
 *
 * A controller that handles token refresh.
 *
 * @author Sergio del Amo
 * @author Graeme Rocher
 * @since 1.0
 */
@Requires(property = OauthControllerConfigurationProperties.PREFIX + ".enabled")
@Controller("${" + OauthControllerConfigurationProperties.PREFIX + ".path:/oauth/access_token}")
@Secured(SecurityRule.IS_ANONYMOUS)
@Validated
public class OauthController {

    private static final Logger LOG = LoggerFactory.getLogger(OauthController.class);
    protected final TokenValidator tokenValidator;
    protected final AccessRefreshTokenGenerator accessRefreshTokenGenerator;

    /**
     *
     * @param tokenValidator An instance of {@link TokenValidator}
     * @param accessRefreshTokenGenerator An instance of {@link AccessRefreshTokenGenerator}
     */
    public OauthController(JwtTokenValidator tokenValidator,
                           AccessRefreshTokenGenerator accessRefreshTokenGenerator) {
        this.tokenValidator = tokenValidator;
        this.accessRefreshTokenGenerator = accessRefreshTokenGenerator;
    }

    /**
     *
     * @param tokenRefreshRequest An instance of {@link TokenRefreshRequest} present in the request
     * @return An AccessRefreshToken encapsulated in the HttpResponse or a failure indicated by the HTTP status
     */
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON})
    @Post
    public Single<HttpResponse<AccessRefreshToken>> index(@Valid TokenRefreshRequest tokenRefreshRequest) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("grantType: {} refreshToken: {}", tokenRefreshRequest.getGrantType(), tokenRefreshRequest.getRefreshToken());
        }

        Flowable<Authentication> authenticationFlowable = Flowable.fromPublisher(tokenValidator.validateToken(tokenRefreshRequest.getRefreshToken()));
        return authenticationFlowable.map((Function<Authentication, HttpResponse<AccessRefreshToken>>) authentication -> {
            Map<String, Object> claims = authentication.getAttributes();
            Optional<AccessRefreshToken> accessRefreshToken = accessRefreshTokenGenerator.generate(tokenRefreshRequest.getRefreshToken(), claims);
            if (accessRefreshToken.isPresent()) {
                return HttpResponse.ok(accessRefreshToken.get());
            }
            return HttpResponse.serverError();
        }).first(HttpResponse.status(HttpStatus.FORBIDDEN));
    }
}
