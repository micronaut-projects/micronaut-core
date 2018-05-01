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

package io.micronaut.security.rules;

import io.micronaut.http.HttpRequest;
import io.micronaut.security.config.SecurityConfiguration;
import io.micronaut.security.config.SecurityConfigurationProperties;
import io.micronaut.security.token.config.TokenConfiguration;
import io.micronaut.web.router.RouteMatch;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A security rule implementation backed by the {@link SecurityConfigurationProperties#getIpPatterns()} ()}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class IpPatternsRule extends AbstractSecurityRule {

    /**
     * The order of the rule.
     */
    public static final Integer ORDER = SecuredAnnotationRule.ORDER - 100;

    private final List<Pattern> patternList;

    /**
     *
     * @param tokenConfiguration Token Configuration
     * @param securityConfiguration Security Configuration
     */
    public IpPatternsRule(TokenConfiguration tokenConfiguration,
                          SecurityConfiguration securityConfiguration) {
        super(tokenConfiguration);
        this.patternList = securityConfiguration.getIpPatterns()
                        .stream()
                        .map(Pattern::compile)
                        .collect(Collectors.toList());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public SecurityRuleResult check(HttpRequest request, @Nullable RouteMatch routeMatch, @Nullable Map<String, Object> claims) {

        if (patternList.isEmpty()) {
            return SecurityRuleResult.UNKNOWN;
        } else {
            if (patternList.stream().anyMatch(pattern ->
                    pattern.pattern().equals(SecurityConfigurationProperties.ANYWHERE) ||
                    pattern.matcher(request.getRemoteAddress().getAddress().getHostAddress()).matches())) {
                return SecurityRuleResult.UNKNOWN;
            } else {
                return SecurityRuleResult.REJECTED;
            }
        }
    }
}
