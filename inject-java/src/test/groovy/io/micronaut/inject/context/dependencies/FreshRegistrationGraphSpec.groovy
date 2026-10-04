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
import spock.lang.AutoCleanup
import spock.lang.Specification

class FreshRegistrationGraphSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.builder()
        .properties('spec.name': 'BeanDependencyGraphSpec')
        .trackBeanDependencies(true)
        .start()
    BeanDependencyGraph graph = context.findDependencyGraph().get()

    void "what a fresh registration received is in the graph until it is closed"() {
        given:
        def repo = context.getBeanDefinition(Repo)
        def prototype = context.getBeanDefinition(PrototypeBean)

        when:
        def fresh = context.createBeanRegistration(prototype)

        then:
        edge(graph.dependenciesOf(prototype), Repo).kind() == InjectionKind.CONSTRUCTOR
        graph.transitiveDependentsOf(repo)*.beanType.contains(PrototypeBean)

        when:
        fresh.close()

        then:
        graph.dependenciesOf(prototype).isEmpty()
    }

    void "a fresh registration of a singleton and the scoped instance keep each other's edges"() {
        given:
        def service = context.getBeanDefinition(ConstructorService)
        context.getBean(Facade)

        when: "a fresh instance beside the scoped one is closed"
        context.createBeanRegistration(service).close()

        then: "the scoped instance still holds the repository"
        edge(graph.dependenciesOf(service), Repo).kind() == InjectionKind.CONSTRUCTOR

        when: "the scoped instance is destroyed while a fresh one lives"
        def fresh = context.createBeanRegistration(service)
        context.destroyBean(ConstructorService)

        then: "the fresh instance still holds it"
        edge(graph.dependenciesOf(service), Repo)

        when: "the last instance goes"
        fresh.close()

        then:
        graph.dependenciesOf(service).isEmpty()
    }

    void "what a bean resolves and creates through its resolver is recorded under the bean"() {
        given:
        def owner = context.getBean(ResolverOwner)
        def ownerDefinition = context.getBeanDefinition(ResolverOwner)
        def prototype = context.getBeanDefinition(PrototypeBean)

        when:
        def fresh = owner.fresh(prototype)
        owner.fresh(prototype)
        owner.lookup(Repo)
        owner.lookup(Repo)
        def edges = graph.dependenciesOf(ownerDefinition)

        then: "each received bean is one edge the owner holds"
        edges*.dependency()*.beanType as Set == [PrototypeBean, Repo] as Set
        edges.size() == 2
        edges.every { it.kind() == InjectionKind.OTHER && !it.lazy() && !it.reinjectable() }

        and: "a change to what the fresh instance holds reaches the owner"
        graph.transitiveDependentsOf(context.getBeanDefinition(Repo))*.beanType.containsAll([PrototypeBean, ResolverOwner])

        when: "one fresh instance is destroyed early"
        fresh.close()

        then: "the other keeps its edge to the repository"
        edge(graph.dependenciesOf(prototype), Repo)

        when:
        context.destroyBean(ResolverOwner)

        then: "the owner's edges, and those of the fresh instances it owned, go with it"
        graph.dependenciesOf(ownerDefinition).isEmpty()
        graph.dependenciesOf(prototype).isEmpty()
    }

    void "an edge one instance of a prototype received through its resolver goes with that instance only"() {
        given:
        def ownerDefinition = context.getBeanDefinition(PrototypeResolverOwner)
        def prototype = context.getBeanDefinition(PrototypeBean)
        def first = context.createBeanRegistration(ownerDefinition)
        def second = context.createBeanRegistration(ownerDefinition)

        when: "each instance receives something different"
        first.bean().lookup(Repo)
        second.bean().fresh(prototype)

        then:
        graph.dependenciesOf(ownerDefinition)*.dependency()*.beanType as Set == [Repo, PrototypeBean] as Set

        when: "the instance that never looked up the repository is destroyed"
        second.close()

        then: "the other one still holds it"
        graph.dependenciesOf(ownerDefinition)*.dependency()*.beanType == [Repo]

        when:
        first.close()

        then:
        graph.dependenciesOf(ownerDefinition).isEmpty()
    }

    void "a lookup through the resolver of an owner being destroyed records nothing"() {
        given:
        def ownerDefinition = context.getBeanDefinition(PrototypeResolverOwner)
        def owner = context.createBeanRegistration(ownerDefinition)
        def bean = owner.bean()
        owner.close()

        when:
        bean.lookup(Repo)

        then:
        thrown(IllegalStateException)
        graph.dependenciesOf(ownerDefinition).isEmpty()
    }

    private static BeanDependencyGraph.BeanDependency edge(Collection<BeanDependencyGraph.BeanDependency> edges, Class<?> dependency) {
        def edge = edges.find { it.dependency().beanType == dependency }
        assert edge != null : "no dependency on $dependency recorded in $edges"
        return edge
    }
}
