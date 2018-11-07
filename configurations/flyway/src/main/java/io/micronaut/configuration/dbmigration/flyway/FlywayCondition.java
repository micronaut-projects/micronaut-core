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

package io.micronaut.configuration.dbmigration.flyway;

import io.micronaut.context.BeanContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.naming.NameResolver;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.Optional;

/**
 * Condition used to create {@link org.flywaydb.core.Flyway} beans. Only enabled and valid Flyway configurations
 * will enable the creation of the Flyway bean.
 *
 * @author Iván López
 * @since 1.1
 */
public class FlywayCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        BeanContext beanContext = context.getBeanContext();
        AnnotationMetadataProvider component = context.getComponent();

        if (component instanceof NameResolver) {
            Optional<String> name = ((NameResolver) component).resolveName();

            if (name.isPresent()) {
                Optional<FlywayConfigurationProperties> optionConfig = beanContext.findBean(FlywayConfigurationProperties.class, Qualifiers.byName(name.get()));

                if (optionConfig.isPresent()) {
                    FlywayConfigurationProperties config = optionConfig.get();

                    if (config.getDataSource() == null && !config.hasAlternativeDatabaseConfiguration()) {
                        context.fail("Flyway bean not created for identifier \"" + name.get() + "\" because no data source found");
                        return false;
                    }

                    if (!config.isEnabled()) {
                        context.fail("Flyway bean not created for identifier \"" + name.get() + "\" because flyway configuration is disabled");
                        return false;
                    }
                }
            }
        }

        return true;
    }
}
