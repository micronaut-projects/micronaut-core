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

package io.micronaut.security.filters;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.exceptions.HttpServerException;
import io.micronaut.security.config.InterceptUrlMapPattern;
import io.micronaut.security.config.SecurityConfiguration;
import io.micronaut.security.rules.SecurityRuleProvider;
import io.micronaut.security.rules.SecurityRuleResult;
import io.micronaut.security.token.configuration.TokenConfiguration;
import io.micronaut.security.token.reader.BearerTokenReader;
import io.micronaut.security.token.reader.TokenReader;
import io.micronaut.security.token.validator.TokenValidator;
import io.micronaut.web.router.RouteMatch;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JWT Filter.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = SecurityConfiguration.PREFIX + ".enabled")
@Filter("/**")
public class JwtFilter extends OncePerRequestHttpServerFilter {

    private static final Logger LOG = LoggerFactory.getLogger(BearerTokenReader.class);

    protected final TokenConfiguration tokenConfiguration;
    protected final TokenReader tokenReader;
    protected final TokenValidator tokenValidator;
    private final Collection<SecurityRuleProvider> ruleProviders;

    /**
     * @param tokenConfiguration The {@link TokenConfiguration} instance
     * @param tokenValidator The {@link TokenValidator} instance
     * @param tokenReader The {@link TokenReader} instance
     * @param ruleProviders The list of providers that will allow or reject the request
     */
    public JwtFilter(TokenConfiguration tokenConfiguration,
                     TokenValidator tokenValidator,
                     TokenReader tokenReader,
                     Collection<SecurityRuleProvider> ruleProviders) {
        this.tokenConfiguration = tokenConfiguration;
        this.tokenValidator = tokenValidator;
        this.tokenReader = tokenReader;
        this.ruleProviders = ruleProviders;
    }

    private Publisher<MutableHttpResponse<?>> unauthorized() {
        return Publishers.just(HttpResponse.status(HttpStatus.UNAUTHORIZED));
    }

    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
        String method = request.getMethod().toString();
        String path = request.getPath();
        Optional<String> token = tokenReader.findToken(request);

        if (token.isPresent()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Token {} found in request {} {}", token, method, path);
            }
            Optional<Map<String, Object>> optionalClaims = tokenValidator.validateTokenAndGetClaims(token.get());
            if (optionalClaims.isPresent()) {
                Map<String, Object> claims = optionalClaims.get();

                if (LOG.isDebugEnabled()) {
                    String claimsString = claims.entrySet()
                            .stream()
                            .map((entry) -> entry.getKey() + "=>" + entry.getValue().toString())
                            .collect(Collectors.joining(", "));
                    LOG.debug("Claims: {}", claimsString);
                }

                RouteMatch routeMatch = request.getAttribute(HttpAttributes.ROUTE_MATCH).map(RouteMatch.class::cast).orElseThrow(() -> new HttpServerException("Request attribute for route match must be set to process security rules"));

                for (SecurityRuleProvider provider: ruleProviders) {
                    SecurityRuleResult result = provider.check(request, routeMatch, claims);
                    if (result == SecurityRuleResult.REJECTED) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Unauthorized request {} {}. The rule provider {} rejected the request.", method, path, provider.getClass().getName());
                        }
                        return unauthorized();
                    }
                    if (result == SecurityRuleResult.ALLOWED) {
                        return chain.proceed(request);
                    }
                }

                //We haven't returned at this point so that means no rule provider allowed or rejected the request
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Unauthorized request {} {}. No rule provider matched the request.", method, path);
                }
                return unauthorized();
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Unauthorized request {} {}. Fetched claims null. Token validation failed.", method, path);
                }
                return unauthorized();
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unauthorized request {}, {}, no token found in request.", method, path);
            }
            return unauthorized();
        }
    }
}
