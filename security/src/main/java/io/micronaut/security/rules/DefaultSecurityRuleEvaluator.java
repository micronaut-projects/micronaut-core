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

package io.micronaut.security.rules;

import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.RouteMatchUtils;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Evaluates a {@link SecurityRule}s for a given request.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@Singleton
public class DefaultSecurityRuleEvaluator implements SecurityRuleEvaluator {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultSecurityRuleEvaluator.class);

    protected final Collection<SecurityRule> securityRules;

    /**
     * @param securityRules The list of rules that will allow or reject the request
     */
    public DefaultSecurityRuleEvaluator(Collection<SecurityRule> securityRules) {
        this.securityRules = securityRules;
    }

    @Override
    public Optional<SecurityRuleEvaluation> findFirst(@NonNull HttpRequest<?> request,
                                            @Nullable Map<String, Object> claims,
                                            @NonNull List<SecurityRuleResult> matchAnyResult) {
        RouteMatch routeMatch = RouteMatchUtils.findRouteMatchAtRequest(request).orElse(null);
        if (routeMatch == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("could find a routeMatch for the request {} {}", request.getMethod(), request.getPath());
            }
        }
        return securityRules.stream()
                .map(rule -> new SecurityRuleEvaluation(rule, rule.check(request, routeMatch, claims)))
                .filter(evaluation -> matchAnyResult.contains(evaluation.getResult()))
                .findFirst();
    }
}
