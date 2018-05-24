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
import io.micronaut.web.router.RouteMatchUtils;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.functions.Function;
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
 * @author Graeme Rocher
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
     * @param securityRules               The list of rules that will allow or reject the request
     * @param authenticationFetchers      List of {@link AuthenticationFetcher} beans in the context.
     * @param rejectionHandler            Bean which handles routes which need to be rejected
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

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
        String method = request.getMethod().toString();
        String path = request.getPath();

        Maybe<Authentication> authentication = Flowable.fromIterable(authenticationFetchers)
            .flatMap(authenticationFetcher -> authenticationFetcher.fetchAuthentication(request))
            .firstElement();

        return authentication.toFlowable().flatMap((Function<Authentication, Publisher<MutableHttpResponse<?>>>) authentication1 -> {
            request.setAttribute(AUTHENTICATION, authentication1);
            Map<String, Object> attributes = authentication1.getAttributes();
            Optional<RouteMatch> routeMatch = RouteMatchUtils.findRouteMatchAtRequest(request);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Attributes: {}", attributes
                    .entrySet()
                    .stream()
                    .map((entry) -> entry.getKey() + "=>" + entry.getValue().toString())
                    .collect(Collectors.joining(", ")));
            }
            for (SecurityRule rule : securityRules) {
                SecurityRuleResult result = rule.check(request, routeMatch.orElse(null), attributes);
                if (result == SecurityRuleResult.REJECTED) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Unauthorized request {} {}. The rule provider {} rejected the request.", method, path, rule.getClass().getName());
                    }
                    return rejectionHandler.reject(request, true);
                }
                if (result == SecurityRuleResult.ALLOWED) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Authorized request {} {}. The rule provider {} authorized the request.", method, path, rule.getClass().getName());
                    }
                    return chain.proceed(request);
                }
            }

            //no rule found for the given request, reject
            return rejectionHandler.reject(request, true);
        }).switchIfEmpty(Flowable.just(securityRules).flatMap(securityRules -> {
            request.setAttribute(AUTHENTICATION, null);
            Optional<RouteMatch> routeMatch = RouteMatchUtils.findRouteMatchAtRequest(request);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failure to authenticate request. {} {}.", method, path);
            }

            for (SecurityRule rule : securityRules) {
                SecurityRuleResult result = rule.check(request, routeMatch.orElse(null), null);
                if (result == SecurityRuleResult.REJECTED) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Unauthorized request {} {}. The rule provider {} rejected the request.", method, path, rule.getClass().getName());
                    }
                    return rejectionHandler.reject(request, false);
                }
                if (result == SecurityRuleResult.ALLOWED) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Authorized request {} {}. The rule provider {} authorized the request.", method, path, rule.getClass().getName());
                    }
                    return chain.proceed(request);
                }
            }

            //no rule found for the given request, reject
            return rejectionHandler.reject(request, false);
        }));
    }
}
