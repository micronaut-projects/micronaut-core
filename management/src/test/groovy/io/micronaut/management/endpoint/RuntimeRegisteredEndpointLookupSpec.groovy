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
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.env.Environment
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.management.endpoint.annotation.Endpoint
import spock.lang.Specification

import java.util.function.Supplier

/**
 * Whether the compile-time index holds every endpoint is remembered once it is known, so a bean definition
 * registered afterwards that carries {@code @Endpoint} without the index has to invalidate it.
 */
class RuntimeRegisteredEndpointLookupSpec extends Specification {

    void "test an endpoint registered without the index after the index answered is still found"() {
        given:
        ApplicationContext context = ApplicationContext.run(Environment.TEST)

        when: 'every endpoint is indexed, so the index answers and that is remembered'
        Collection<BeanDefinition<?>> before = context.getBeanDefinitions(Qualifiers.byStereotype(Endpoint))

        then:
        !before.isEmpty()
        !(RuntimeEndpoint in before*.beanType)

        when: 'a definition that carries @Endpoint but not its index is registered'
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(Endpoint.name, [id: 'runtime'])
        RuntimeBeanDefinition<RuntimeEndpoint> definition = RuntimeBeanDefinition.builder(RuntimeEndpoint, { new RuntimeEndpoint() } as Supplier<RuntimeEndpoint>)
                .annotationMetadata(metadata)
                .build()
        context.registerBeanDefinition(definition)
        Collection<BeanDefinition<?>> after = context.getBeanDefinitions(Qualifiers.byStereotype(Endpoint))

        then: 'it carries no index, so it is only found by filtering every definition'
        definition.annotationMetadata.hasStereotype(Endpoint)
        !(Endpoint in definition.indexes)
        RuntimeEndpoint in after*.beanType

        and: 'every endpoint found before is still found'
        after*.beanType.containsAll(before*.beanType)

        cleanup:
        context.close()
    }
}

class RuntimeEndpoint {
}
