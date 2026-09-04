/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.management.endpoint.annotation.Endpoint
import spock.lang.Specification

/**
 * {@code @Endpoint} is meta-annotated with {@code @Indexed(Endpoint)}, so the endpoint beans a
 * {@code BeanDefinitionProcessor<Endpoint>} is handed are looked up through the compile-time index
 * instead of by scanning every bean definition reference.
 */
class EndpointIndexedLookupSpec extends Specification {

    void "test endpoint beans are enumerable through the compile-time index"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                ['endpoints.all.enabled': true, 'endpoints.all.sensitive': false], Environment.TEST)

        when: 'the beans are looked up through the index'
        Collection<BeanDefinition<?>> indexed = context.getBeanDefinitions(Endpoint)

        and: 'and by scanning every bean definition, which is what the index replaces'
        Collection<BeanDefinition<?>> scanned = context.getAllBeanDefinitions().findAll { it.hasStereotype(Endpoint) }

        then: 'endpoints are actually present'
        !indexed.isEmpty()

        and: 'both answer the same beans, in the same order, which the endpoint routes are registered in'
        indexed*.beanType.name == scanned*.beanType.name

        and: 'every one of them carries the index, which is what makes the lookup exhaustive'
        indexed.every { Endpoint in ((BeanDefinitionReference<?>) it).indexes }

        cleanup:
        context.close()
    }
}
