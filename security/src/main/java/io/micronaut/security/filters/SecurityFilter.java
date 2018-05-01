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

import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.handlers.RejectionHandler;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.rules.SecurityRuleResult;
import io.micronaut.web.router.RouteMatch;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Security Filter.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Filter("/**")
public class SecurityFilter extends OncePerRequestHttpServerFilter {

    /**
     * The attribute used to store the authentication object in the request.
     */
    public static final CharSequence AUTHENTICATION = "micronaut.AUTHENTICATION";

    private static final Logger LOG = LoggerFactory.getLogger(SecurityFilter.class);

    /**
     * The order of the Security Filter.
     */
    protected final Integer order;

    protected final Collection<SecurityRule> securityRules;
    protected final Collection<AuthenticationFetcher> authenticationFetchers;
    protected final RejectionHandler rejectionHandler;

    /**
     * @param securityRules The list of rules that will allow or reject the request
     * @param authenticationFetchers List of {@link AuthenticationFetcher} beans in the context.
     * @param rejectionHandler Bean which handles routes which need to be rejected
     * @param securityFilterOrderProvider filter order provider
     */
    public SecurityFilter(Collection<SecurityRule> securityRules,
                          Collection<AuthenticationFetcher> authenticationFetchers,
                          RejectionHandler rejectionHandler,
                          @Nullable SecurityFilterOrderProvider securityFilterOrderProvider) {
        this.securityRules = securityRules;
        this.authenticationFetchers = authenticationFetchers;
        this.rejectionHandler = rejectionHandler;
        this.order = securityFilterOrderProvider != null ? securityFilterOrderProvider.getSecurityFilterOrder() : 0;
    }

    private Optional<RouteMatch> getRouteMatch(HttpRequest<?> request) {
        Optional<Object> routeMatchAttribute = request.getAttribute(HttpAttributes.ROUTE_MATCH);
        if (routeMatchAttribute.isPresent()) {
            return Optional.of((RouteMatch) routeMatchAttribute.get());
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Route match attribute for request ({}) not found", request.getPath());
            }
            return Optional.empty();
        }
    }

    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
        String method = request.getMethod().toString();
        String path = request.getPath();

        Optional<Authentication> authentication = Optional.empty();
        for (AuthenticationFetcher authenticationFetcher : authenticationFetchers) {
            authentication = authenticationFetcher.fetchAuthentication(request);
            if (authentication.isPresent()) {
                break;
            }
        }

        request.setAttribute(AUTHENTICATION, authentication.orElse(null));
        Optional<Map<String, Object>> attributes = authentication.map(Authentication::getAttributes);
        Optional<RouteMatch> routeMatch = getRouteMatch(request);

        if (LOG.isDebugEnabled()) {
            attributes.ifPresent(stringObjectMap -> LOG.debug("Attributes: {}", stringObjectMap.entrySet()
                    .stream()
                    .map((entry) -> entry.getKey() + "=>" + entry.getValue().toString())
                    .collect(Collectors.joining(", "))));
            if (authentication.isPresent()) {
                LOG.debug("Failure to authenticate request. {} {}.", method, path);
            }
        }

        for (SecurityRule rule: securityRules) {
            SecurityRuleResult result = rule.check(request, routeMatch.orElse(null), attributes.orElse(null));
            if (result == SecurityRuleResult.REJECTED) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Unauthorized request {} {}. The rule provider {} rejected the request.", method, path, rule.getClass().getName());
                }
                return rejectionHandler.reject(request, attributes.isPresent());
            }
            if (result == SecurityRuleResult.ALLOWED) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Authorized request {} {}. The rule provider {} authorized the request.", method, path, rule.getClass().getName());
                }
                return chain.proceed(request);
            }
        }

        //no rule found for the given request, reject
        return rejectionHandler.reject(request, attributes.isPresent());
    }

    @Override
    public int getOrder() {
        return order;
    }
}
