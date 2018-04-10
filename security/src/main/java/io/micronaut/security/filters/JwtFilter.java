/*
 * Copyright 2017 original authors
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

import io.micronaut.context.BeanContext;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.management.endpoint.EndpointConfiguration;
import io.micronaut.security.config.InterceptUrlMapPattern;
import io.micronaut.security.config.SecurityConfigType;
import io.micronaut.security.config.SecurityConfiguration;
import io.micronaut.security.config.JwtConfiguration;
import io.micronaut.security.token.reader.BearerTokenReader;
import io.micronaut.security.token.reader.TokenReader;
import io.micronaut.security.token.validator.TokenValidator;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import io.micronaut.core.util.PathMatcher;

/**
 * JWT Filter
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Filter("/**")
public class JwtFilter extends OncePerRequestHttpServerFilter {
    private static final Logger log = LoggerFactory.getLogger(BearerTokenReader.class);
    protected final SecurityConfiguration securityConfiguration;
    protected final JwtConfiguration jwtConfiguration;
    protected final TokenReader tokenReader;
    protected final TokenValidator tokenValidator;
    protected final BeanContext beanContext;

    /**
     * @param jwtConfiguration The {@link JwtConfiguration} instance
     * @param tokenReader The {@link TokenReader} instance
     * @param tokenValidator The {@link TokenValidator} instance
     */
    public JwtFilter(BeanContext beanContext,
                     SecurityConfiguration securityConfiguration,
                     JwtConfiguration jwtConfiguration,
                     TokenReader tokenReader,
                     TokenValidator tokenValidator) {
        this.beanContext = beanContext;
        this.securityConfiguration = securityConfiguration;
        this.jwtConfiguration = jwtConfiguration;
        this.tokenReader = tokenReader;
        this.tokenValidator = tokenValidator;
    }

    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {

        if ( securityConfiguration.isEnabled() ) {

            List<InterceptUrlMapPattern> patterns = endpointsInterceptUrlMap();
            patterns.addAll(interceptUrlMapPatternsOfSecurityControllers(jwtConfiguration));
            final boolean useInterceptUrlMap = securityConfiguration.getSecurityConfigType().equals(SecurityConfigType.INTERCEPT_URL_MAP);
            if ( useInterceptUrlMap ) {
                List<InterceptUrlMapPattern> configInterceptUrlMap = securityConfiguration.getInterceptUrlMap();
                if ( configInterceptUrlMap != null ) {
                    patterns.addAll(configInterceptUrlMap);
                }
            }
            List<InterceptUrlMapPattern> patternsForRequest = patternsForRequest(request, patterns);
            if ( matchesAccess(patternsForRequest, Arrays.asList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY)) ) {
                return chain.proceed(request);
            }

            String token = tokenReader.findToken(request);
            if (token != null) {
                log.debug("Token {} found in request {} {}", token, request.getMethod().toString(), request.getPath());
                Map<String, Object> claims = tokenValidator.validateTokenAndGetClaims(token);
                if ( claims == null ) {
                    log.debug("Unauthorized request {} {}. Fetched claims null. Token validation failed.", request.getMethod().toString(), request.getPath(), token);
                    return Publishers.just(HttpResponse.status(HttpStatus.UNAUTHORIZED));
                }
                log.debug("Claims: {}", claims.keySet().stream().reduce((a, b) -> a + "=>" + claims.get(a) + ", " + b + "=>" + claims.get(b)).get());


                if (matchesAccess(patternsForRequest, Arrays.asList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED))) {
                    log.debug("Proceed since the user is authenticated and access list in intercept url map allows access to authenticated users.");
                    return chain.proceed(request);
                }

                if ( !claims.containsKey(jwtConfiguration.getRolesClaimName())) {
                    log.debug("Unauthorized request {} {}. Claims did not contained {}", request.getMethod().toString(), request.getPath(), jwtConfiguration.getRolesClaimName());
                    return Publishers.just(HttpResponse.status(HttpStatus.UNAUTHORIZED));
                }
                Object rolesObj = claims.get(jwtConfiguration.getRolesClaimName());
                if ( !areRolesListOfStrings(rolesObj) ) {
                    log.debug("Unauthorized request {} {}. roles not instance of List<String> {}", request.getMethod().toString(), request.getPath(), rolesObj.toString());
                    return Publishers.just(HttpResponse.status(HttpStatus.UNAUTHORIZED));
                }
                if (patternsForRequest.isEmpty()) {
                    log.debug("Unauthorized request {}, {}, no token found in request", request.getMethod().toString(), request.getPath());
                    return Publishers.just(HttpResponse.status(HttpStatus.UNAUTHORIZED));
                }
                List<String> roles = (List<String>) rolesObj;
                if (matchesAccess(patternsForRequest, roles)) {
                    log.debug("Proceed since the user is authenticated and a role matches the access list in intercept url map.");
                    return chain.proceed(request);
                }
                return Publishers.just(HttpResponse.status(HttpStatus.FORBIDDEN));

            } else {
                log.debug("Unauthorized request {}, {}, no token found in request", request.getMethod().toString(), request.getPath());
                return Publishers.just(HttpResponse.status(HttpStatus.UNAUTHORIZED));
            }
        } else {
            return chain.proceed(request);
        }
    }

    private boolean areRolesListOfStrings(Object rolesObj) {
        if ( rolesObj instanceof List ) {
            for ( Object obj : (List) rolesObj ) {
                if ( !(obj instanceof String) ) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private List<InterceptUrlMapPattern> patternsForRequest(HttpRequest<?> request, List<InterceptUrlMapPattern> interceptUrlMap) {
        final URI uri = request.getUri();
        final String uriString = uri.toString();
        final HttpMethod httpMethod = request.getMethod();
        return interceptUrlMap
                .stream()
                .filter(p ->
                        p.getHttpMethod().equals(httpMethod) &&
                                PathMatcher.ANT.matches(p.getPattern(), uriString))
                .collect(Collectors.toList());
    }

    private boolean matchesAccess(List<InterceptUrlMapPattern> interceptUrlMap, List<String> allowedAccesses) {
        return interceptUrlMap
                .stream()
                .anyMatch(p ->
                        p.getAccess().stream().anyMatch(access -> allowedAccesses.contains(access))
                );
    }

    private List<InterceptUrlMapPattern> endpointsInterceptUrlMap() {
        List<InterceptUrlMapPattern> patterns = new ArrayList<>();
        List<String> anonymousAccess = Arrays.asList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY);
        List<String> authenticatedAccess = Arrays.asList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED);
        for ( HttpMethod method : Arrays.asList(HttpMethod.GET, HttpMethod.POST) ) {
            patterns.addAll(beanContext.getBeansOfType(EndpointConfiguration.class).stream()
                    .filter(ec -> ec.isEnabled().isPresent() ? ec.isEnabled().get() : false)
                    .map(ec -> new InterceptUrlMapPattern(endpointPattern(ec), (ec.isSensitive().isPresent() ? ec.isSensitive().get() : false) ? authenticatedAccess : anonymousAccess, method))
                    .collect(Collectors.toList()));
        }
        return patterns;
    }

    private String endpointPattern(EndpointConfiguration ec) {
        StringBuilder sb = new StringBuilder();
        sb.append("/");
        sb.append(ec.getId());
        return sb.toString();
    }

    private List<InterceptUrlMapPattern> interceptUrlMapPatternsOfSecurityControllers(JwtConfiguration jwtConfiguration) {
        final List<InterceptUrlMapPattern> results = new ArrayList<>();
        final List<String> access = Collections.singletonList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY);
        if ( jwtConfiguration != null) {
            if ( jwtConfiguration.isLogin() ) {
                results.add(new InterceptUrlMapPattern("/login", access, HttpMethod.POST));
            }

            if ( jwtConfiguration.isRefresh() ) {
                results.add(new InterceptUrlMapPattern("/oauth/access_token", access, HttpMethod.POST));
            }
        }
        return results;
    }


}
