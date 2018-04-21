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
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.security.Secured;
import io.micronaut.security.authentication.*;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.token.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.token.configuration.TokenConfiguration;

import java.util.Optional;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Controller("/")
@Requires(property = SecurityEndpointsConfigurationProperties.PREFIX + ".login")
@Secured(SecurityRule.IS_ANONYMOUS)
public class LoginController implements LoginControllerApi {

    public static final String LOGIN_PATH = "/login";

    protected final AccessRefreshTokenGenerator accessRefreshTokenGenerator;
    protected final TokenConfiguration tokenConfiguration;
    protected final Authenticator authenticator;

    /**
     *
     * @param accessRefreshTokenGenerator AccessRefresh Token generator
     * @param tokenConfiguration Token configuration
     * @param authenticator {@link Authenticator} collaborator
     */
    public LoginController(AccessRefreshTokenGenerator accessRefreshTokenGenerator, TokenConfiguration tokenConfiguration, Authenticator authenticator) {
        this.accessRefreshTokenGenerator = accessRefreshTokenGenerator;
        this.tokenConfiguration = tokenConfiguration;
        this.authenticator = authenticator;
    }

    /**
     *
     * @param usernamePasswordCredentials An instance of {@link UsernamePasswordCredentials} in the body payload
     * @return An AccessRefreshToken encapsulated in the HttpResponse or a failure indicated by the HTTP status
     */
    @Override
    public HttpResponse login(@Body UsernamePasswordCredentials usernamePasswordCredentials) {
        Optional<AuthenticationResponse> response = authenticator.authenticate(usernamePasswordCredentials);
        if (response.map(AuthenticationResponse::isAuthenticated).orElse(false)) {
            return accessRefreshTokenGenerator.generate((UserDetails) response.get());
        }
        throw new AuthenticationException(response.flatMap(AuthenticationResponse::getMessage).orElse(null));
    }
}
