/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.security.rules.DefaultSecurityRuleEvaluator;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.rules.SecurityRuleEvaluation;
import io.micronaut.security.rules.SecurityRuleEvaluator;
import io.micronaut.security.rules.SecurityRuleResult;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.annotations.NonNull;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
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
    public static final CharSequence AUTHENTICATION = HttpAttributes.PRINCIPAL;

    /**
     * The attribute used to store a valid token in the request.
     */
    public static final CharSequence TOKEN = "micronaut.TOKEN";

    private static final Logger LOG = LoggerFactory.getLogger(SecurityFilter.class);

    /**
     * The order of the Security Filter.
     */
    protected final Integer order;

    protected final Collection<AuthenticationFetcher> authenticationFetchers;
    protected final RejectionHandler rejectionHandler;
    protected final SecurityRuleEvaluator securityRuleEvaluator;

    /**
     * @deprecated Use {@link #SecurityFilter(Collection, RejectionHandler, SecurityFilterOrderProvider, SecurityRuleEvaluator)} instead
     * @param securityRules               The list of rules that will allow or reject the request
     * @param authenticationFetchers      List of {@link AuthenticationFetcher} beans in the context.
     * @param rejectionHandler            Bean which handles routes which need to be rejected
     * @param securityFilterOrderProvider filter order provider
     */
    public SecurityFilter(Collection<SecurityRule> securityRules,
                          Collection<AuthenticationFetcher> authenticationFetchers,
                          RejectionHandler rejectionHandler,
                          @Nullable SecurityFilterOrderProvider securityFilterOrderProvider) {
        this(authenticationFetchers, rejectionHandler, securityFilterOrderProvider, new DefaultSecurityRuleEvaluator(securityRules));
    }

    /**
     * @param authenticationFetchers      List of {@link AuthenticationFetcher} beans in the context.
     * @param rejectionHandler            Bean which handles routes which need to be rejected
     * @param securityFilterOrderProvider filter order provider
     * @param securityRuleEvaluator securityRuleEvaluator
     */
    @Inject
    public SecurityFilter(Collection<AuthenticationFetcher> authenticationFetchers,
                          RejectionHandler rejectionHandler,
                          @Nullable SecurityFilterOrderProvider securityFilterOrderProvider,
                          SecurityRuleEvaluator securityRuleEvaluator) {
        this.authenticationFetchers = authenticationFetchers;
        this.rejectionHandler = rejectionHandler;
        this.order = securityFilterOrderProvider != null ? securityFilterOrderProvider.getOrder() : 0;
        this.securityRuleEvaluator = securityRuleEvaluator;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
        Maybe<Authentication> authentication = Flowable.fromIterable(authenticationFetchers)
            .flatMap(authenticationFetcher -> authenticationFetcher.fetchAuthentication(request))
            .firstElement();

        return authentication.toFlowable().flatMap(auth -> process(request, chain, auth, true))
                .switchIfEmpty(Flowable.just("").flatMap(i -> process(request, chain, null, false)));
    }

    private void logResult(HttpRequest<?> request, @NonNull SecurityRuleEvaluation evaluation) {
        if (LOG.isDebugEnabled()) {
            String msg = "";
            if (evaluation.getResult() == SecurityRuleResult.REJECTED) {
                msg = "Unauthorized request {} {}. The rule provider {} rejected the request.";
            } else if (evaluation.getResult() == SecurityRuleResult.ALLOWED) {
                msg = "Authorized request {} {}. The rule provider {} authorized the request.";
            }
            LOG.debug(msg,
                    request.getMethod().toString(),
                    request.getPath(),
                    evaluation.getRule().getClass().getName());
        }
    }

    private Publisher<MutableHttpResponse<?>> process(HttpRequest<?> request,
                                                                ServerFilterChain chain,
                                                                @Nullable Authentication authentication,
                                                                boolean forbidden) {
        request.setAttribute(AUTHENTICATION, authentication);
        Map<String, Object> attributes = authentication != null ? authentication.getAttributes() : null;
        if (attributes != null && LOG.isDebugEnabled()) {
            LOG.debug("Attributes: {}", attributes
                    .entrySet()
                    .stream()
                    .map((entry) -> entry.getKey() + "=>" + entry.getValue().toString())
                    .collect(Collectors.joining(", ")));
        }
        SecurityRuleEvaluation evaluation = securityRuleEvaluator.findFirst(request, attributes, Arrays.asList(SecurityRuleResult.ALLOWED, SecurityRuleResult.REJECTED)).orElse(null);
        if (evaluation != null) {
            SecurityRuleResult result = evaluation.getResult();
            logResult(request, evaluation);
            if (result == SecurityRuleResult.REJECTED) {
                return rejectionHandler.reject(request, forbidden);
            }
            if (result == SecurityRuleResult.ALLOWED) {
                return chain.proceed(request);
            }
        }

    //no rule found for the given request, reject
        return rejectionHandler.reject(request, forbidden);
    }
}
