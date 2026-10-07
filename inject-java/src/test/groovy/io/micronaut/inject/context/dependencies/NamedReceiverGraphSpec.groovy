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
package io.micronaut.inject.context.dependencies

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanDependencyGraph
import io.micronaut.context.BeanRegistration
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class NamedReceiverGraphSpec extends Specification {

    void "a bean with a declared qualifier records what it received under the definition it is registered with"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
            .properties('spec.name': 'NamedReceiverGraphSpec')
            .trackBeanDependencies(true)
            .start()
        BeanDependencyGraph graph = context.findDependencyGraph().get()

        when: "a named factory product is looked up by its own name and held by another bean"
        context.getBean(NamedProduct, Qualifiers.byName("x"))
        context.getBean(NamedProductHolder)
        context.getBean(NamedService, Qualifiers.byName("y"))
        BeanRegistration<NamedProduct> product = context.getBeanRegistration(NamedProduct, Qualifiers.byName("x"))
        BeanRegistration<NamedService> service = context.getBeanRegistration(NamedService, Qualifiers.byName("y"))
        def repo = context.getBeanDefinition(NamedRepo)

        then: "the edges are recorded under the definitions the singletons are registered with"
        graph.dependenciesOf(product.beanDefinition)*.dependency()*.beanType.toSet() == [NamedRepo, NamedProductFactory] as Set
        graph.dependenciesOf(service.beanDefinition)*.dependency()*.beanType == [NamedRepo]
        graph.dependentsOf(product.beanDefinition)*.dependent()*.beanType == [NamedProductHolder]

        and: "the holder of the product is reached from what the product received"
        graph.transitiveDependentsOf(repo)*.beanType.containsAll([NamedProduct, NamedProductHolder, NamedService])
        graph.transitiveDependenciesOf(context.getBeanDefinition(NamedProductHolder))*.beanType.contains(NamedRepo)

        when: "the named beans are destroyed"
        context.destroyBean(product)
        context.destroyBean(service)

        then: "what they received is forgotten with them"
        graph.dependenciesOf(product.beanDefinition).isEmpty()
        graph.dependenciesOf(service.beanDefinition).isEmpty()
        !graph.dependentsOf(repo).any { it.dependent().beanType in [NamedProduct, NamedService] }

        cleanup:
        context.close()
    }

    void "an @Any bean is resolved through a delegate of the qualifier it is looked up with, even its own"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
            .properties('spec.name': 'NamedReceiverGraphSpec')
            .trackBeanDependencies(true)
            .start()
        BeanDependencyGraph graph = context.findDependencyGraph().get()

        when:
        context.getBean(AnyProduct, Qualifiers.any())
        context.getBean(AnyProduct, Qualifiers.byName("z"))
        BeanRegistration<AnyProduct> any = context.getBeanRegistration(AnyProduct, Qualifiers.any())
        BeanRegistration<AnyProduct> named = context.getBeanRegistration(AnyProduct, Qualifiers.byName("z"))

        then: "each is recorded under the delegate it is registered with"
        !any.is(named)
        graph.dependenciesOf(any.beanDefinition)*.dependency()*.beanType.toSet() == [NamedRepo, NamedProductFactory] as Set
        graph.dependenciesOf(named.beanDefinition)*.dependency()*.beanType.toSet() == [NamedRepo, NamedProductFactory] as Set

        when:
        context.destroyBean(any)

        then:
        graph.dependenciesOf(any.beanDefinition).isEmpty()
        !graph.dependenciesOf(named.beanDefinition).isEmpty()

        cleanup:
        context.close()
    }
}
