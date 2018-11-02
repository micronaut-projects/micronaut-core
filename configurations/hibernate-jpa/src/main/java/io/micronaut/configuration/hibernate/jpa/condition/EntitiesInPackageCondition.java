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

package io.micronaut.configuration.hibernate.jpa.condition;

import io.micronaut.configuration.hibernate.jpa.JpaConfiguration;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.naming.NameResolver;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.persistence.Entity;
import java.util.Optional;

/**
 * A custom condition that scans for entities in the configured packages.
 *
 * @author graemerocher
 * @since 1.0
 */
public class EntitiesInPackageCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context) {
        final AnnotationMetadataProvider component = context.getComponent();
        if (component instanceof BeanDefinition) {
            BeanDefinition<?> definition = (BeanDefinition<?>) component;
            final BeanContext beanContext = context.getBeanContext();
            if (beanContext instanceof ApplicationContext) {

                final Optional<String> name = definition instanceof NameResolver ? ((NameResolver) definition).resolveName() : Optional.empty();
                final Qualifier<JpaConfiguration> q = Qualifiers.byName(name.orElse("default"));
                final Optional<JpaConfiguration> jpaConfiguration = beanContext.findBean(JpaConfiguration.class, q);
                final String[] packagesToScan = jpaConfiguration.map(JpaConfiguration::getPackagesToScan).orElse(StringUtils.EMPTY_STRING_ARRAY);

                final Environment environment = ((ApplicationContext) beanContext).getEnvironment();
                if (ArrayUtils.isNotEmpty(packagesToScan)) {
                    return environment.scan(Entity.class, packagesToScan).findAny().isPresent();
                } else {
                    return environment.scan(Entity.class).findAny().isPresent();
                }
            }
        }
        return true;
    }
}
