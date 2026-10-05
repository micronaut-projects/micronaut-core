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
import io.micronaut.context.BeanDependencyGraph.InjectionKind
import spock.lang.Specification

class BeanDependencyGraphSpec extends Specification {

    void "the graph is not recorded unless asked for"() {
        given:
        ApplicationContext context = ApplicationContext.run('spec.name': 'BeanDependencyGraphSpec')

        expect:
        context.findDependencyGraph().isEmpty()

        cleanup:
        context.close()
    }

    void "development mode switched on by configuration records the graph from the first bean"() {
        given: "development mode is only in the properties of the context, not a system property"
        ApplicationContext context = ApplicationContext.run('spec.name': 'BeanDependencyGraphSpec', 'micronaut.dev.enabled': true)

        when:
        context.getBean(ConstructorService)
        def dependents = context.findDependencyGraph().get().dependentsOf(context.getBeanDefinition(Repo))

        then:
        System.getProperty('micronaut.dev.enabled') == null
        dependents.find { it.dependent().beanType == ConstructorService }.kind() == InjectionKind.CONSTRUCTOR

        cleanup:
        context.close()
    }

    void "a builder switching tracking off wins over development mode switched on by configuration"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
            .properties('spec.name': 'BeanDependencyGraphSpec', 'micronaut.dev.enabled': true)
            .trackBeanDependencies(false)
            .start()

        expect:
        context.findDependencyGraph().isEmpty()

        cleanup:
        context.close()
    }

    void "the graph records how each singleton received the others"() {
        given:
        ApplicationContext context = ApplicationContext.builder()
            .properties('spec.name': 'BeanDependencyGraphSpec')
            .trackBeanDependencies(true)
            .start()
        BeanDependencyGraph graph = context.findDependencyGraph().get()
        def repo = context.getBeanDefinition(Repo)

        when: "the beans are created"
        context.getBean(Facade)
        context.getBean(FieldService)
        context.getBean(SetterService)
        context.getBean(ListService)
        context.getBean(Produced)
        ProviderService providerService = context.getBean(ProviderService)

        and: "the provider is used"
        providerService.repo()
        def dependents = graph.dependentsOf(repo)

        then: "each receiving bean is recorded with the way it received the repository"
        edge(dependents, ConstructorService).kind() == InjectionKind.CONSTRUCTOR
        !edge(dependents, ConstructorService).reinjectable()
        edge(dependents, FieldService).kind() == InjectionKind.FIELD
        edge(dependents, FieldService).reinjectable()
        edge(dependents, SetterService).kind() == InjectionKind.METHOD
        edge(dependents, SetterService).reinjectable()

        and: "a bean that resolves the repository through a provider holds nothing and is not recorded"
        !dependents.any { it.dependent().beanType == ProviderService }

        and: "a collection injection is recorded once per member and marked as such"
        def listEdges = graph.dependenciesOf(context.getBeanDefinition(ListService))
        listEdges.size() == 2
        listEdges.every { it.collection() && it.kind() == InjectionKind.CONSTRUCTOR }
        listEdges*.dependency()*.beanType.toSet() == [AHandler, BHandler] as Set

        and: "a factory method argument is held by the produced bean as a constructor argument"
        edge(dependents, Produced).kind() == InjectionKind.CONSTRUCTOR

        and: "the factory is a dependency of the bean it produces, not of the bean holding the product"
        context.getBean(Holder)
        graph.dependenciesOf(context.getBeanDefinition(Produced))*.dependency()*.beanType.toSet() == [Repo, ProducedFactory] as Set
        graph.dependenciesOf(context.getBeanDefinition(Holder))*.dependency()*.beanType == [Produced]

        and: "the members of an @EachBean set are told apart by their qualifier"
        context.getBeansOfType(ConnConsumer).size() == 2
        def connA = context.getBeanDefinition(Conn, io.micronaut.inject.qualifiers.Qualifiers.byName("a"))
        def consumersOfA = graph.dependentsOf(connA).findAll { it.dependent().beanType == ConnConsumer }
        consumersOfA.size() == 1
        consumersOfA.first().dependent().declaredQualifier == io.micronaut.inject.qualifiers.Qualifiers.byName("a")
        graph.transitiveDependenciesOf(context.getBeanDefinition(Holder))*.beanType.containsAll([Produced, Repo, ProducedFactory])

        and: "a path through a prototype is kept"
        context.getBean(PrototypeHolder)
        edge(graph.dependentsOf(repo), PrototypeBean).kind() == InjectionKind.CONSTRUCTOR
        edge(graph.dependentsOf(context.getBeanDefinition(PrototypeBean)), PrototypeHolder).kind() == InjectionKind.CONSTRUCTOR

        and: "the transitive dependents follow held references only"
        def transitive = graph.transitiveDependentsOf(repo)*.beanType
        transitive.containsAll([ConstructorService, FieldService, SetterService, Facade, Produced, PrototypeBean, PrototypeHolder])
        !transitive.contains(ProviderService)

        when: "a bean is injected again"
        int fieldEdges = graph.dependenciesOf(context.getBeanDefinition(FieldService)).size()
        context.refreshBean(context.getBeanRegistration(FieldService, null))

        then: "its field edges are replaced, not doubled"
        graph.dependenciesOf(context.getBeanDefinition(FieldService)).size() == fieldEdges

        when: "one of two holders of a prototype is destroyed"
        context.getBean(PrototypeHolder)
        def secondHolder = context.createBean(PrototypeHolder)
        context.destroyBean(secondHolder)

        then: "the prototype's edge to the repository survives for the other holder"
        graph.dependentsOf(repo).any { it.dependent().beanType == PrototypeBean }

        when: "a bean is destroyed"
        context.destroyBean(FieldService)

        then: "what it held is forgotten, what held it is not"
        !graph.dependentsOf(repo).any { it.dependent().beanType == FieldService }
        graph.dependenciesOf(context.getBeanDefinition(FieldService)).isEmpty()
        graph.dependentsOf(context.getBeanDefinition(ConstructorService)).any { it.dependent().beanType == Facade }

        and: "the graph is cleared with the context"
        context.stop()
        graph.dependencies().isEmpty()

        cleanup:
        context.close()
    }

    private static BeanDependencyGraph.BeanDependency edge(Collection<BeanDependencyGraph.BeanDependency> edges, Class<?> dependent) {
        def edge = edges.find { it.dependent().beanType == dependent }
        assert edge != null : "no dependency recorded for $dependent in $edges"
        return edge
    }
}
