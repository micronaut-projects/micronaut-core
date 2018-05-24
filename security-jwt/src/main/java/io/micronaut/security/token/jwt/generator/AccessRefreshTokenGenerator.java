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

package io.micronaut.security.token.jwt.generator;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.token.generator.TokenGenerator;
import io.micronaut.security.token.jwt.event.AccessTokenGeneratedEvent;
import io.micronaut.security.token.jwt.event.RefreshTokenGeneratedEvent;
import io.micronaut.security.token.jwt.generator.claims.ClaimsGenerator;
import io.micronaut.security.token.jwt.render.AccessRefreshToken;
import io.micronaut.security.token.jwt.render.TokenRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generates http responses with access and refresh token.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class AccessRefreshTokenGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(AccessRefreshTokenGenerator.class);

    protected final ClaimsGenerator claimsGenerator;
    protected final JwtGeneratorConfiguration jwtGeneratorConfiguration;
    protected final TokenRenderer tokenRenderer;
    protected final TokenGenerator tokenGenerator;
    protected final ApplicationEventPublisher eventPublisher;

    /**
     *
     * @param jwtGeneratorConfiguration Instance of {@link JwtGeneratorConfiguration}
     * @param tokenRenderer Instance of {@link TokenRenderer}
     * @param tokenGenerator Intance of {@link TokenGenerator}
     * @param claimsGenerator Claims generator
     * @param eventPublisher The Application event publiser
     */
    public AccessRefreshTokenGenerator(JwtGeneratorConfiguration jwtGeneratorConfiguration,
                                       TokenRenderer tokenRenderer,
                                       TokenGenerator tokenGenerator,
                                       ClaimsGenerator claimsGenerator,
                                       ApplicationEventPublisher eventPublisher) {
        this.jwtGeneratorConfiguration = jwtGeneratorConfiguration;
        this.tokenRenderer = tokenRenderer;
        this.tokenGenerator = tokenGenerator;
        this.claimsGenerator = claimsGenerator;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Generate an {@link AccessRefreshToken} response for the given
     * user details.
     *
     * @param userDetails Authenticated user's representation.
     * @return The http response
     */
    public Optional<AccessRefreshToken> generate(UserDetails userDetails) {

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
            return Optional.empty();

        }
        AccessRefreshToken accessRefreshToken = tokenRenderer.render(userDetails, jwtGeneratorConfiguration.getAccessTokenExpiration(), accessToken.get(), refreshToken.get());
        eventPublisher.publishEvent(new AccessTokenGeneratedEvent(accessRefreshToken.getAccessToken()));
        eventPublisher.publishEvent(new RefreshTokenGeneratedEvent(accessRefreshToken.getRefreshToken()));
        return Optional.of(accessRefreshToken);
    }

    /**
     * Generate an {@link AccessRefreshToken} response for the given
     * refresh token and claims.
     *
     * @param refreshToken The refresh token
     * @param oldClaims The claims to generate the access token
     * @return The http response
     */
    public Optional<AccessRefreshToken> generate(String refreshToken, Map<String, Object> oldClaims) {
        Map<String, Object> claims = claimsGenerator.generateClaimsSet(oldClaims, jwtGeneratorConfiguration.getAccessTokenExpiration());

        Optional<String> optionalAccessToken = tokenGenerator.generateToken(claims);
        if (!optionalAccessToken.isPresent()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("tokenGenerator failed to generate access token claims: {}", claims.entrySet()
                        .stream()
                        .map((entry) -> entry.getKey() + "=>" + entry.getValue().toString())
                        .collect(Collectors.joining(", ")));
            }
            return Optional.empty();
        }
        String accessToken = optionalAccessToken.get();
        eventPublisher.publishEvent(new AccessTokenGeneratedEvent(accessToken));
        return Optional.of(tokenRenderer.render(jwtGeneratorConfiguration.getAccessTokenExpiration(), accessToken, refreshToken));

    }
}
