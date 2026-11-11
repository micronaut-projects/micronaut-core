package io.micronaut.context

import io.micronaut.context.BeanDependencyGraph.BeanDependency
import io.micronaut.context.BeanDependencyGraph.InjectionKind
import spock.lang.Specification

class DefaultBeanDependencyGraphSpec extends Specification {

    void "two unqualified runtime registrations of the same type are different nodes"() {
        given:
        def graph = new DefaultBeanDependencyGraph()
        def first = RuntimeBeanDefinition.of(new Receiver())
        def second = RuntimeBeanDefinition.of(new Receiver())
        def received = RuntimeBeanDefinition.of(new Received())

        when:
        graph.add(new BeanDependency(first, received, InjectionKind.CONSTRUCTOR, false, false))
        graph.add(new BeanDependency(second, received, InjectionKind.CONSTRUCTOR, false, false))

        then: "each registration keeps its own edge"
        graph.dependentsOf(received).size() == 2
        graph.dependenciesOf(first).size() == 1
        graph.dependenciesOf(second).size() == 1

        when: "one of them is destroyed"
        graph.remove(first)

        then: "the other's edge survives"
        graph.dependenciesOf(first).isEmpty()
        graph.dependentsOf(received)*.dependent() == [second]
    }

    void "a runtime definition is identified by the definition object"() {
        given:
        def runtime = RuntimeBeanDefinition.of(new Receiver())

        expect:
        DefaultBeanDependencyGraph.Key.of(runtime) == DefaultBeanDependencyGraph.Key.of(runtime)
        DefaultBeanDependencyGraph.Key.of(runtime) != DefaultBeanDependencyGraph.Key.of(RuntimeBeanDefinition.of(new Receiver()))
    }

    void "forgetting the reinjectable edges of a bean leaves no empty index entries"() {
        given:
        def graph = new DefaultBeanDependencyGraph()
        def receiver = RuntimeBeanDefinition.of(new Receiver())
        def received = RuntimeBeanDefinition.of(new Received())
        graph.add(new BeanDependency(receiver, received, InjectionKind.FIELD, false, false))

        when:
        graph.removeReinjectable(receiver)

        then:
        graph.dependentsOf(received).isEmpty()
        graph.dependenciesOf(receiver).isEmpty()
        graph.@byDependency.isEmpty()
        graph.@byDependent.isEmpty()
    }

    static class Receiver {
    }

    static class Received {
    }
}
