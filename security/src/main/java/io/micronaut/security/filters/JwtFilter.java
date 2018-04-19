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
import io.micronaut.security.endpoints.LoginController;
import io.micronaut.security.endpoints.OauthController;
import io.micronaut.security.endpoints.SecurityEndpointsConfiguration;
import io.micronaut.security.token.generator.TokenConfiguration;
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
 * JWT Filter.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Filter("/**")
public class JwtFilter extends OncePerRequestHttpServerFilter {

    private static final Logger LOG = LoggerFactory.getLogger(BearerTokenReader.class);

    protected final SecurityConfiguration securityConfiguration;
    protected final TokenConfiguration tokenConfiguration;
    protected final SecurityEndpointsConfiguration securityEndpointsConfiguration;
    protected final TokenReader tokenReader;
    protected final BeanContext beanContext;
    protected final TokenValidator tokenValidator;

    /**
     * @param beanContext {@link BeanContext}
     * @param securityConfiguration {@link SecurityConfiguration}
     * @param tokenConfiguration The {@link TokenConfiguration} instance
     * @param tokenValidator The {@link TokenValidator} instance
     * @param securityEndpointsConfiguration The {@link SecurityEndpointsConfiguration} instance
     * @param tokenReader The {@link TokenReader} instance
     */
    public JwtFilter(BeanContext beanContext,
                     SecurityConfiguration securityConfiguration,
                     TokenConfiguration tokenConfiguration,
                     TokenValidator tokenValidator,
                     SecurityEndpointsConfiguration securityEndpointsConfiguration,
                     TokenReader tokenReader) {
        this.beanContext = beanContext;
        this.securityConfiguration = securityConfiguration;
        this.tokenConfiguration = tokenConfiguration;
        this.tokenValidator = tokenValidator;
        this.securityEndpointsConfiguration = securityEndpointsConfiguration;
        this.tokenReader = tokenReader;
    }

    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {

        if ( securityConfiguration.isEnabled() ) {
            List<InterceptUrlMapPattern> patternsForRequest = findAllPatternsForRequest(request,
                    endpointsInterceptUrlMap(),
                    securityConfiguration,
                    securityEndpointsConfiguration);
            HttpStatus status = filterRequest(request,
                    patternsForRequest,
                    tokenReader,
                    tokenValidator,
                    tokenConfiguration);
            if ( status == HttpStatus.OK ) {
                return chain.proceed(request);
            }
            return Publishers.just(HttpResponse.status(status));

        } else {
            return chain.proceed(request);
        }
    }

    /**
     *
     * @param request HttpRequest
     * @param endpointsInterceptUrlMappings List of {@link InterceptUrlMapPattern} for Built-in Endpoints
     * @param securityConfiguration {@link SecurityConfiguration}
     * @param securityEndpointsConfiguration {@link SecurityEndpointsConfiguration}
     * @return the List of {@link InterceptUrlMapPattern} which apply for an HTTP Request.
     */
    public static List<InterceptUrlMapPattern> findAllPatternsForRequest(HttpRequest<?> request,
                                                                         List<InterceptUrlMapPattern> endpointsInterceptUrlMappings,
                                                                         SecurityConfiguration securityConfiguration,
                                                                         SecurityEndpointsConfiguration securityEndpointsConfiguration){
        endpointsInterceptUrlMappings.addAll(interceptUrlMapPatternsOfSecurityControllers(securityEndpointsConfiguration));
        final boolean useInterceptUrlMap = securityConfiguration.getSecurityConfigType().equals(SecurityConfigType.INTERCEPT_URL_MAP);
        if ( useInterceptUrlMap ) {
            List<InterceptUrlMapPattern> configInterceptUrlMap = securityConfiguration.getInterceptUrlMap();
            if ( configInterceptUrlMap != null ) {
                endpointsInterceptUrlMappings.addAll(configInterceptUrlMap);
            }
        }
        return patternsForRequest(request, endpointsInterceptUrlMappings);
    }

    /**
     *
     * @param request HTTP request
     * @param patternsForRequest Patterns which apply for this particular Request
     * @param tokenReader JWT token reader
     * @param tokenValidator JWT token validator
     * @param tokenConfiguration TokenConfiguration
     * @return HttpStatus.OK if the request should be allowed, an appropiate HTTPStatus code signalizing the failure (UNAUTHORIZED, FORBIDDEN)
     */
    public static HttpStatus filterRequest(HttpRequest<?> request,
                                           List<InterceptUrlMapPattern> patternsForRequest,
                                           TokenReader tokenReader,
                                           TokenValidator tokenValidator,
                                           TokenConfiguration tokenConfiguration
                                           ) {

        if ( matchesAccess(patternsForRequest, Collections.singletonList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY)) ) {
            return HttpStatus.OK;
        }

        Optional<String> token = tokenReader.findToken(request);
        if (token.isPresent() ) {
            LOG.debug("Token {} found in request {} {}", token, request.getMethod().toString(), request.getPath());
            Optional<Map<String, Object>> optionalClaims = tokenValidator.validateTokenAndGetClaims(token.get());
            if ( !optionalClaims.isPresent() ) {
                LOG.debug("Unauthorized request {} {}. Fetched claims null. Token validation failed.", request.getMethod().toString(), request.getPath(), token);
                return HttpStatus.UNAUTHORIZED;
            }
            Map<String, Object> claims = optionalClaims.get();
            LOG.debug("Claims: {}", claims.keySet().stream().reduce((a, b) -> a + "=>" + claims.get(a) + ", " + b + "=>" + claims.get(b)).get());
            if (matchesAccess(patternsForRequest, Collections.singletonList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED))) {
                LOG.debug("Proceed since the user is authenticated and access list in intercept url map allows access to authenticated users.");
                return HttpStatus.OK;
            }
            if ( !claims.containsKey(tokenConfiguration.getRolesClaimName())) {
                LOG.debug("Unauthorized request {} {}. Claims did not contained {}", request.getMethod().toString(), request.getPath(), tokenConfiguration.getRolesClaimName());
                return HttpStatus.UNAUTHORIZED;
            }
            Object rolesObj = claims.get(tokenConfiguration.getRolesClaimName());
            if ( !areRolesListOfStrings(rolesObj) ) {
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
        if ( rolesObj == null ) {
            return false;
        }
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

    /**
     * Filters a List of {@link InterceptUrlMapPattern} which apply of this particular request.
     * @param request HTTP Request
     * @param interceptUrlMap List of {@link InterceptUrlMapPattern}
     * @return a List of {@link InterceptUrlMapPattern}
     */
    public static List<InterceptUrlMapPattern> patternsForRequest(HttpRequest<?> request, List<InterceptUrlMapPattern> interceptUrlMap) {
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

    private List<InterceptUrlMapPattern> endpointsInterceptUrlMap() {
        return interceptUrlMapOfEndpointConfigurations(beanContext.getBeansOfType(EndpointConfiguration.class));
    }

    /**
     *
     * @param endpointConfigurations Collection of {@link EndpointConfiguration}
     * @return a List of {@link InterceptUrlMapPattern}
     */
    public static List<InterceptUrlMapPattern> interceptUrlMapOfEndpointConfigurations(Collection<EndpointConfiguration> endpointConfigurations) {
        if ( endpointConfigurations == null || endpointConfigurations.isEmpty() ) {
            return new ArrayList<>();
        }
        List<InterceptUrlMapPattern> patterns = new ArrayList<>();
        List<String> anonymousAccess = Collections.singletonList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY);
        List<String> authenticatedAccess = Collections.singletonList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED);
        for ( HttpMethod method : Arrays.asList(HttpMethod.GET, HttpMethod.POST) ) {
            patterns.addAll(endpointConfigurations.stream()
                    .filter(ec -> ec.isEnabled().isPresent() ? ec.isEnabled().get() : false)
                    .map(ec -> new InterceptUrlMapPattern(endpointPattern(ec), (ec.isSensitive().isPresent() ? ec.isSensitive().get() : false) ? authenticatedAccess : anonymousAccess, method))
                    .collect(Collectors.toList()));
        }
        return patterns;
    }

    /**
     *
     * @param ec Instance of {@link EndpointConfiguration}
     * @return / + endpoint.id
     */
    public static String endpointPattern(EndpointConfiguration ec) {
        StringBuilder sb = new StringBuilder();
        sb.append("/");
        sb.append(ec.getId());
        return sb.toString();
    }

    /**
     *
     * @param securityEndpointsConfiguration Instance of {@link SecurityEndpointsConfiguration}
     * @return a List of {@link InterceptUrlMapPattern}
     */
    public static List<InterceptUrlMapPattern> interceptUrlMapPatternsOfSecurityControllers(SecurityEndpointsConfiguration securityEndpointsConfiguration) {
        final List<InterceptUrlMapPattern> results = new ArrayList<>();
        final List<String> access = Collections.singletonList(InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY);
        if ( securityEndpointsConfiguration != null) {
            if ( securityEndpointsConfiguration.isLogin() ) {
                results.add(new InterceptUrlMapPattern(LoginController.LOGIN_PATH, access, HttpMethod.POST));
            }

            if ( securityEndpointsConfiguration.isRefresh() ) {
                final StringBuilder sb = new StringBuilder();
                sb.append(OauthController.CONTROLLER_PATH);
                sb.append(OauthController.ACCESS_TOKEN_PATH);
                final String pattern = sb.toString();
                results.add(new InterceptUrlMapPattern(pattern, access, HttpMethod.POST));
            }
        }
        return results;
    }
}
