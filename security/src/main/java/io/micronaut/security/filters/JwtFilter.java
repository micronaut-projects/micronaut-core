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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.security.config.InterceptUrlMapPattern;
import io.micronaut.security.config.SecurityConfiguration;
import io.micronaut.security.token.configuration.TokenConfiguration;
import io.micronaut.security.token.reader.BearerTokenReader;
import io.micronaut.security.token.reader.TokenReader;
import io.micronaut.security.token.validator.TokenValidator;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    protected final EndpointAccessFetcher endpointAccessFetcher;

    /**
     * @param tokenConfiguration The {@link TokenConfiguration} instance
     * @param tokenValidator The {@link TokenValidator} instance
     * @param tokenReader The {@link TokenReader} instance
     * @param endpointAccessFetcher Allow you to search for access restriction which apply to a request
     */
    public JwtFilter(TokenConfiguration tokenConfiguration,
                     TokenValidator tokenValidator,
                     TokenReader tokenReader,
                     EndpointAccessFetcher endpointAccessFetcher) {
        this.tokenConfiguration = tokenConfiguration;
        this.tokenValidator = tokenValidator;
        this.tokenReader = tokenReader;
        this.endpointAccessFetcher = endpointAccessFetcher;
    }

    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
            List<InterceptUrlMapPattern> patternsForRequest = endpointAccessFetcher.findAllPatternsForRequest(request);
            HttpStatus status = filterRequest(request, patternsForRequest);
            if (status == HttpStatus.OK) {
                return chain.proceed(request);
            }
            return Publishers.just(HttpResponse.status(status));
    }

    /**
     *
     * @param request HTTP request
     * @param patternsForRequest Patterns which apply for this particular Request
     * @return HttpStatus.OK if the request should be allowed, an appropiate HTTPStatus code signalizing the failure (UNAUTHORIZED, FORBIDDEN)
     */
    public HttpStatus filterRequest(HttpRequest<?> request,
                                           List<InterceptUrlMapPattern> patternsForRequest) {

        if (matchesAccess(patternsForRequest, Collections.singletonList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY))) {
            return HttpStatus.OK;
        }

        Optional<String> token = tokenReader.findToken(request);
        if (token.isPresent()) {
            LOG.debug("Token {} found in request {} {}", token, request.getMethod().toString(), request.getPath());
            Optional<Map<String, Object>> optionalClaims = tokenValidator.validateTokenAndGetClaims(token.get());
            if (!optionalClaims.isPresent()) {
                LOG.debug("Unauthorized request {} {}. Fetched claims null. Token validation failed.", request.getMethod().toString(), request.getPath(), token);
                return HttpStatus.UNAUTHORIZED;
            }
            Map<String, Object> claims = optionalClaims.get();
            LOG.debug("Claims: {}", claims.keySet().stream().reduce((a, b) -> a + "=>" + claims.get(a) + ", " + b + "=>" + claims.get(b)).get());
            if (matchesAccess(patternsForRequest, Collections.singletonList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED))) {
                LOG.debug("Proceed since the user is authenticated and access list in intercept url map allows access to authenticated users.");
                return HttpStatus.OK;
            }
            if (!claims.containsKey(tokenConfiguration.getRolesClaimName())) {
                LOG.debug("Unauthorized request {} {}. Claims did not contained {}", request.getMethod().toString(), request.getPath(), tokenConfiguration.getRolesClaimName());
                return HttpStatus.UNAUTHORIZED;
            }
            Object rolesObj = claims.get(tokenConfiguration.getRolesClaimName());
            if (!areRolesListOfStrings(rolesObj)) {
                LOG.debug("Unauthorized request {} {}. roles not instance of List<String> {}", request.getMethod().toString(), request.getPath(), rolesObj.toString());
                return HttpStatus.UNAUTHORIZED;
            }
            if (matchesAccess(patternsForRequest, (List<String>) rolesObj)) {
                LOG.debug("Proceed since the user is authenticated and a role matches the access list in intercept url map.");
                return HttpStatus.OK;
            }
            return HttpStatus.FORBIDDEN;

        } else {
            LOG.debug("Unauthorized request {}, {}, no token found in request", request.getMethod().toString(), request.getPath());
            return HttpStatus.UNAUTHORIZED;
        }
    }

    /**
     *
     * @param rolesObj An object
     * @return true of the object is a list of string
     */
    public static boolean areRolesListOfStrings(Object rolesObj) {
        if (rolesObj == null) {
            return false;
        }
        if (rolesObj instanceof List) {
            for (Object obj : (List) rolesObj) {
                if (!(obj instanceof String)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     *
     * @param interceptUrlMap Instance of {@link InterceptUrlMapPattern}
     * @param allowedAccesses e.g. ['ROLE_USER']
     * @return true if any of the  {@link InterceptUrlMapPattern} matches the list of allowed access
     */
    public static boolean matchesAccess(List<InterceptUrlMapPattern> interceptUrlMap, List<String> allowedAccesses) {
        return interceptUrlMap
                .stream()
                .anyMatch(p ->
                        p.getAccess().stream().anyMatch(allowedAccesses::contains)
                );
    }
}
