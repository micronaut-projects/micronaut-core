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

package io.micronaut.views.model.security;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.SecurityFilter;
import io.micronaut.security.utils.SecurityService;
import io.micronaut.views.model.ViewsModelDecorator;
import javax.annotation.Nonnull;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Returns information about the current user so that it can be append it to the model being rendered.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@Requires(property = SecurityViewsModelDecoratorConfigurationProperties.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
@Requires(beans = {SecurityFilter.class, SecurityService.class, SecurityViewsModelDecoratorConfiguration.class})
@Singleton
public class SecurityViewsModelDecorator implements ViewsModelDecorator {

    private final SecurityService securityService;
    private final SecurityViewsModelDecoratorConfiguration securityViewsModelDecoratorConfiguration;

    /**
     *
     * @param securityViewsModelDecoratorConfiguration The Security Views Model Decorator configuration
     * @param securityService Utility to access Security information
     */
    public SecurityViewsModelDecorator(SecurityViewsModelDecoratorConfiguration securityViewsModelDecoratorConfiguration,
                                       SecurityService securityService) {
        this.securityViewsModelDecoratorConfiguration = securityViewsModelDecoratorConfiguration;
        this.securityService = securityService;
    }

    @Override
    public void decorateModel(@Nonnull Map<String, Object> model, @Nonnull HttpRequest request) {
        Optional<Authentication> authentication = securityService.getAuthentication();
        if (authentication.isPresent()) {
            Map<String, Object> securityModel = new HashMap<>();
            securityModel.put(securityViewsModelDecoratorConfiguration.getPrincipalNameKey(), authentication.get().getName());
            securityModel.put(securityViewsModelDecoratorConfiguration.getAttributesKey(), authentication.get());
            model.putIfAbsent(securityViewsModelDecoratorConfiguration.getSecurityKey(), securityModel);
        }
    }
}
