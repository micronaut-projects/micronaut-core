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
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.Authenticator;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.token.generator.TokenConfiguration;
import io.micronaut.security.token.render.AccessRefreshToken;
import java.util.Optional;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Controller("/")
@Requires(property = SecurityEndpointsConfigurationProperties.PREFIX + ".login")
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
    public HttpResponse<AccessRefreshToken> login(@Body UsernamePasswordCredentials usernamePasswordCredentials) {
        Optional<UserDetails> userDetails = authenticate(usernamePasswordCredentials);
        if ( userDetails.isPresent()) {
            return accessRefreshTokenGenerator.generate(userDetails.get());
        }
        return HttpResponse.status(HttpStatus.UNAUTHORIZED);
    }

    /**
     *
     * @param usernamePasswordCredentials User creds being authenticated
     * @return Empty if the authentication fails. User details if authentication is successful.
     */
    protected Optional<UserDetails> authenticate(UsernamePasswordCredentials usernamePasswordCredentials) {
        Optional<AuthenticationResponse> authenticationResponse = authenticator.authenticate(usernamePasswordCredentials);
        if ( authenticationResponse.isPresent() && authenticationResponse.get() instanceof UserDetails) {
            return Optional.of((UserDetails)authenticationResponse.get());
        }
        return Optional.empty();
    }
}
