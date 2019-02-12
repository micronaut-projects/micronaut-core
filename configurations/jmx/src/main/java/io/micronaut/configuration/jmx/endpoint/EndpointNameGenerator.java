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
package io.micronaut.configuration.jmx.endpoint;

import io.micronaut.configuration.jmx.context.DefaultNameGenerator;
import io.micronaut.inject.BeanDefinition;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Generates object names for endpoint bean definitions.
 */
@Singleton
@Named("endpoint")
public class EndpointNameGenerator extends DefaultNameGenerator {

    private static final String MICRONUAT_ENDPOINT = "io.micronaut.management.endpoint";

    @Override
    protected String getDomain(BeanDefinition<?> beanDefinition) {
        String domain = super.getDomain(beanDefinition);
        if (domain.startsWith(MICRONUAT_ENDPOINT)) {
            return MICRONUAT_ENDPOINT;
        } else {
            return domain;
        }
    }
}
