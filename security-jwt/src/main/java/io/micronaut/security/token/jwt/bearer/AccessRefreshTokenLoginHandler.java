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

package io.micronaut.security.token.jwt.bearer;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.security.authentication.AuthenticationException;
import io.micronaut.security.authentication.AuthenticationFailed;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.handlers.LoginHandler;
import io.micronaut.security.token.jwt.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.token.jwt.render.AccessRefreshToken;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Implementation of {@link LoginHandler} for Token Based Authentication.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = BearerTokenConfigurationProperties.PREFIX + ".enabled", notEquals = "false")
@Singleton
public class AccessRefreshTokenLoginHandler implements LoginHandler {

    protected final AccessRefreshTokenGenerator accessRefreshTokenGenerator;

    /**
     * @param accessRefreshTokenGenerator AccessRefresh Token generator
     */
    public AccessRefreshTokenLoginHandler(AccessRefreshTokenGenerator accessRefreshTokenGenerator) {
        this.accessRefreshTokenGenerator = accessRefreshTokenGenerator;
    }

    @Override
    public HttpResponse loginSuccess(UserDetails userDetails, HttpRequest<?> request) {
        Optional<AccessRefreshToken> accessRefreshTokenOptional = accessRefreshTokenGenerator.generate(userDetails);
        if (accessRefreshTokenOptional.isPresent()) {
            return HttpResponse.ok(accessRefreshTokenOptional.get());
        }
        return HttpResponse.serverError();
    }

    @Override
    public HttpResponse loginFailed(AuthenticationFailed authenticationFailed) {
        throw new AuthenticationException(authenticationFailed.getMessage().orElse(null));
    }
}
