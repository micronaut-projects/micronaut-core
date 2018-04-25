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

package io.micronaut.security.jwt.generator;

import io.micronaut.context.BeanContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.jwt.config.JwtGeneratorConfiguration;
import io.micronaut.security.token.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.token.generator.TokenGenerator;
import io.micronaut.security.token.render.AccessRefreshToken;
import io.micronaut.security.token.render.TokenRenderer;
import io.micronaut.security.jwt.generator.claims.JwtClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class DefaultAccessRefreshTokenGenerator implements AccessRefreshTokenGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAccessRefreshTokenGenerator.class);

    protected final JwtGeneratorConfiguration jwtGeneratorConfiguration;
    protected final BeanContext beanContext;
    protected final TokenRenderer tokenRenderer;
    protected final TokenGenerator tokenGenerator;

    /**
     *
     * @param beanContext Instance of {@link BeanContext}
     * @param jwtGeneratorConfiguration Instance of {@link JwtGeneratorConfiguration}
     * @param tokenRenderer Instance of {@link TokenRenderer}
     * @param tokenGenerator Intance of {@link TokenGenerator}
     */
    public DefaultAccessRefreshTokenGenerator(BeanContext beanContext,
                                              JwtGeneratorConfiguration jwtGeneratorConfiguration,
                                              TokenRenderer tokenRenderer,
                                              TokenGenerator tokenGenerator) {
        this.beanContext = beanContext;
        this.jwtGeneratorConfiguration = jwtGeneratorConfiguration;
        this.tokenRenderer = tokenRenderer;
        this.tokenGenerator = tokenGenerator;
    }

    /**
     * Wraps {@link AccessRefreshToken} in an HTTP Response.
     * @param accessRefreshToken {@link AccessRefreshToken}
     * @return HTTPResponse warpping {@link AccessRefreshToken}
     */
    protected HttpResponse<AccessRefreshToken> httpResponseWithAccessRefreshToken(AccessRefreshToken accessRefreshToken) {
        return HttpResponse.ok(accessRefreshToken);
    }

    /**
     *
     * @param userDetails Authenticated user's representation.
     * @return
     */
    @Override
    public HttpResponse<AccessRefreshToken> generate(UserDetails userDetails) {
        try {
            Optional<String> accessToken = tokenGenerator.generateToken(userDetails, jwtGeneratorConfiguration.getAccessTokenExpiration());
            Optional<String> refreshToken = tokenGenerator.generateToken(userDetails, jwtGeneratorConfiguration.getRefreshTokenExpiration());
            if (!accessToken.isPresent() || !refreshToken.isPresent()) {
                if (LOG.isDebugEnabled()) {
                    if (!accessToken.isPresent()) {
                        LOG.debug("tokenGenerator failed to generate access token for userDetails {}", userDetails.getUsername());
                    }
                    if (!refreshToken.isPresent()) {
                        LOG.debug("tokenGenerator failed to generate refreshToken token for userDetails {}", userDetails.getUsername());
                    }
                }
                return HttpResponse.serverError();
            }
            AccessRefreshToken accessRefreshToken = tokenRenderer.render(userDetails, jwtGeneratorConfiguration.getAccessTokenExpiration(), accessToken.get(), refreshToken.get());
            return httpResponseWithAccessRefreshToken(accessRefreshToken);
        } catch (Exception e) {
            return HttpResponse.serverError();
        }
    }

    /**
     *
     * @param refreshToken The refresh token - a JWT token
     * @param oldClaims The claims to generate the access token
     * @return An AccessRefreshToken encapsulated in the HttpResponse or a failure indicated by the HTTP status
     */
    @Override
    public HttpResponse<AccessRefreshToken> generate(String refreshToken, Map<String, Object> oldClaims) {
        Map<String, Object> claims = new HashMap<>(oldClaims);
        claims.put(JwtClaims.EXPIRATION_TIME, expirationDate());
        try {
            Optional<String> accessToken = tokenGenerator.generateToken(claims);
            if (!accessToken.isPresent()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("tokenGenerator failed to generate access token claims: {}", claims.entrySet()
                            .stream()
                            .map((entry) -> entry.getKey() + "=>" + entry.getValue().toString())
                            .collect(Collectors.joining(", ")));
                }
                return HttpResponse.serverError();
            }
            AccessRefreshToken accessRefreshToken = tokenRenderer.render(jwtGeneratorConfiguration.getAccessTokenExpiration(), accessToken.get(), refreshToken);
            return httpResponseWithAccessRefreshToken(accessRefreshToken);
        } catch (Exception e) {
            return HttpResponse.serverError();
        }
    }

    /**
     * An expiration Date built with current date + default expiration.
     * @return java.util.Date
     */
    protected Date expirationDate() {
        Integer expiration = jwtGeneratorConfiguration.getAccessTokenExpiration();
        LOG.debug("Setting expiration to {}", expiration.toString());
        return Date.from(Instant.now().plus(expiration, ChronoUnit.MILLIS));
    }
}
