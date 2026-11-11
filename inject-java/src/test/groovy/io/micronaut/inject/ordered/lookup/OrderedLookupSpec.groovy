/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.ordered.lookup

import io.micronaut.context.ApplicationContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.core.annotation.Order
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.stream.Collectors

class OrderedLookupSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(['spec.name': 'OrderedLookupSpec'])

    void "lookups return compiled beans sorted by @Order and Ordered like an injected list"() {
        expect:
        names(context.getBeanRegistrations(OrderedService)*.bean) == ['ZFirst', 'MSecond', 'AThird']
        names(context.getBeansOfType(OrderedService)) == ['ZFirst', 'MSecond', 'AThird']
        names(context.streamOfType(OrderedService).collect(Collectors.toList())) == ['ZFirst', 'MSecond', 'AThird']
        names(context.getBean(OrderedServiceHolder).services) == ['ZFirst', 'MSecond', 'AThird']
    }

    void "lookups sort runtime registered beans by Ordered and @Order metadata"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'OrderedLookupSpec'])
        // looked up before the registrations, so the cached collection has to be resolved again
        ctx.getBeansOfType(OrderedService)
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(Order.name, [value: 15])

        when:
        ctx.registerBeanDefinition(RuntimeBeanDefinition.builder(OrderedService, () -> new RuntimeService('last', 100))
                .named('last')
                .singleton(true)
                .build())
        ctx.registerBeanDefinition(RuntimeBeanDefinition.builder(OrderedService, () -> new RuntimeService('first', -100))
                .named('first')
                .singleton(true)
                .build())
        ctx.registerBeanDefinition(RuntimeBeanDefinition.builder(OrderedService, () -> new UnorderedRuntimeService())
                .named('annotated')
                .annotationMetadata(metadata)
                .singleton(true)
                .build())

        then:
        names(ctx.getBeanRegistrations(OrderedService)*.bean) == ['first', 'ZFirst', 'MSecond', 'annotated', 'AThird', 'last']
        names(ctx.getBeansOfType(OrderedService)) == ['first', 'ZFirst', 'MSecond', 'annotated', 'AThird', 'last']
        names(ctx.streamOfType(OrderedService).collect(Collectors.toList())) == ['first', 'ZFirst', 'MSecond', 'annotated', 'AThird', 'last']
        names(ctx.getBean(OrderedServiceHolder).services) == ['first', 'ZFirst', 'MSecond', 'annotated', 'AThird', 'last']

        cleanup:
        ctx.close()
    }

    private static List<String> names(Collection<OrderedService> services) {
        services.collect { it instanceof RuntimeService ? it.name() : it instanceof UnorderedRuntimeService ? 'annotated' : it.class.simpleName }
    }

    static class UnorderedRuntimeService implements OrderedService {
    }
}
