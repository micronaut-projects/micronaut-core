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

package io.micronaut.configuration.hibernate.validator;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Configuration;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.MessageInterpolator;
import javax.validation.ParameterNameProvider;
import javax.validation.TraversableResolver;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Provides a {@link ValidatorFactory} instance.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
public class ValidatorFactoryProvider {

    @Inject
    protected Optional<MessageInterpolator> messageInterpolator = Optional.empty();

    @Inject
    protected Optional<TraversableResolver> traversableResolver = Optional.empty();

    @Inject
    protected Optional<ConstraintValidatorFactory> constraintValidatorFactory = Optional.empty();

    @Inject
    protected Optional<ParameterNameProvider> parameterNameProvider = Optional.empty();

    @Value("${hibernate.validator.ignore-xml-configuration:true}")
    protected boolean ignoreXmlConfiguration = true;

    /**
     * Produces a Validator factory class.
     * @param environment optional param for environment
     * @return validator factory
     */
    @Singleton
    @Bean
    ValidatorFactory validatorFactory(Optional<Environment> environment) {
        Configuration validatorConfiguration = Validation.byDefaultProvider()
            .configure();

        messageInterpolator.ifPresent(validatorConfiguration::messageInterpolator);
        traversableResolver.ifPresent(validatorConfiguration::traversableResolver);
        constraintValidatorFactory.ifPresent(validatorConfiguration::constraintValidatorFactory);
        parameterNameProvider.ifPresent(validatorConfiguration::parameterNameProvider);

        if (ignoreXmlConfiguration) {
            validatorConfiguration.ignoreXmlConfiguration();
        }
        environment.ifPresent(env -> {
            Optional<Properties> config = env.getProperty("hibernate.validator", Properties.class);
            config.ifPresent(properties -> {
                for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                    Object value = entry.getValue();
                    if (value != null) {
                        validatorConfiguration.addProperty(
                            "hibernate.validator." + entry.getKey(),
                            value.toString()
                        );
                    }
                }
            });
        });
        return validatorConfiguration.buildValidatorFactory();
    }
}
