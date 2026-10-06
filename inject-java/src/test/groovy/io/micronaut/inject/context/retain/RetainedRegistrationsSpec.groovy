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
package io.micronaut.inject.context.retain

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.event.BeanDestroyedEvent
import io.micronaut.context.event.BeanDestroyedEventListener
import spock.lang.Specification

class RetainedRegistrationsSpec extends Specification {

    def setup() {
        Expensive.CREATED.set(0)
        Expensive.DESTROYED.set(0)
        Consumer.DESTROYED.set(0)
        Scratch.DESTROYED.set(0)
        FeedReader.DESTROYED.set(0)
    }

    void "a retained singleton survives a restart and the beans that need it are rebuilt on top of it"() {
        given: "a running context that records what its beans receive"
        ApplicationContext first = ApplicationContext.builder()
            .properties('spec.name': 'RetainedRegistrationsSpec')
            .trackBeanDependencies(true)
            .start()
        Consumer consumer = first.getBean(Consumer)
        Expensive expensive = consumer.expensive
        Helper helper = expensive.helper

        when: "it is stopped retaining the expensive bean"
        def retained = ((DefaultBeanContext) first).stopRetaining { it.beanType == Expensive }

        then: "the expensive bean and the helper it holds were neither destroyed nor announced as destroyed, the consumer was"
        retained.size() == 2
        retained.any { it.bean.is(expensive) }
        retained.any { it.bean.is(helper) }
        Expensive.DESTROYED.get() == 0
        Consumer.DESTROYED.get() == 1
        !first.running

        and: "the prototype the expensive bean owns travelled with it, undestroyed"
        Scratch.DESTROYED.get() == 0
        retained.find { it.bean.is(expensive) }.dependentBeans()*.bean.contains(expensive.scratch)

        when: "a new context adopts the retained registrations"
        ApplicationContext second = ApplicationContext.builder()
            .properties('spec.name': 'RetainedRegistrationsSpec')
            .trackBeanDependencies(true)
            .retainedRegistrations(retained)
            .start()
        Consumer newConsumer = second.getBean(Consumer)

        then: "the same instance is served, under the new context's definition, and the consumer is new"
        second.getBean(Expensive).is(expensive)
        second.getBean(Helper).is(helper)
        newConsumer.expensive.is(expensive)
        !newConsumer.is(consumer)
        Expensive.CREATED.get() == 1
        second.getBeanRegistration(Expensive, null).beanDefinition.is(second.getBeanDefinition(Expensive))
        second.getBeanRegistrations(Expensive).size() == 1

        and: "the new context's graph knows what the adopted bean holds, under its own definitions"
        def graph = second.findDependencyGraph().get()
        def carried = graph.dependenciesOf(second.getBeanDefinition(Expensive))
        carried*.dependency()*.beanType.toSet() == [Helper, Scratch] as Set
        carried.every { it.kind() == io.micronaut.context.BeanDependencyGraph.InjectionKind.CONSTRUCTOR }
        carried.find { it.dependency().beanType == Helper }.dependency().is(second.getBeanDefinition(Helper))
        graph.transitiveDependentsOf(second.getBeanDefinition(Helper))*.beanType.containsAll([Expensive, Consumer])

        when: "the new context is closed"
        second.close()

        then: "the retained bean and the prototype it owns are destroyed once, with it"
        Expensive.DESTROYED.get() == 1
        Scratch.DESTROYED.get() == 1
        Consumer.DESTROYED.get() == 2
    }

    void "an edge carried by the prototypes of two adopted beans stays until the last of them is destroyed"() {
        given: "two singletons that each own a prototype holding the same singleton"
        ApplicationContext first = ApplicationContext.builder()
            .properties('spec.name': 'RetainedRegistrationsSpec')
            .trackBeanDependencies(true)
            .start()
        LeaseHolderA a = first.getBean(LeaseHolderA)
        LeaseHolderB b = first.getBean(LeaseHolderB)
        def retained = ((DefaultBeanContext) first).stopRetaining { it.beanType in [LeaseHolderA, LeaseHolderB] }

        when: "a new context adopts both, then destroys one of them and the prototype it owns"
        ApplicationContext second = ApplicationContext.builder()
            .properties('spec.name': 'RetainedRegistrationsSpec')
            .trackBeanDependencies(true)
            .retainedRegistrations(retained)
            .start()
        def graph = second.findDependencyGraph().get()
        def source = second.getBeanDefinition(Source)
        assert second.getBean(LeaseHolderA).is(a)
        assert second.getBean(LeaseHolderB).is(b)
        assert graph.transitiveDependentsOf(source)*.beanType.containsAll([Lease, LeaseHolderA, LeaseHolderB])
        second.destroyBean(LeaseHolderA)

        then: "the prototype the other one owns still holds the singleton, so the other one still depends on it"
        graph.dependentsOf(source)*.dependent()*.beanType.contains(Lease)
        graph.transitiveDependentsOf(source)*.beanType.containsAll([Lease, LeaseHolderB])

        when: "the other one is destroyed too"
        second.destroyBean(LeaseHolderB)

        then: "no prototype holds the singleton any more"
        !graph.dependentsOf(source)*.dependent()*.beanType.contains(Lease)

        cleanup:
        second.close()
    }

    void "a retained registration nobody adopts is destroyed with what it owns"() {
        given:
        ApplicationContext first = ApplicationContext.run('spec.name': 'RetainedRegistrationsSpec')
        first.getBean(Consumer)
        def retained = ((DefaultBeanContext) first).stopRetaining { it.beanType in [Expensive, Helper] }
        ApplicationContext other = ApplicationContext.run('spec.name': 'RetainedRegistrationsSpec')

        when: "a launcher gives up on them, twice over"
        retained.each { other.destroyBean(it) }
        retained.each { other.destroyBean(it) }

        then: "each instance is destroyed once"
        Expensive.DESTROYED.get() == 1
        Scratch.DESTROYED.get() == 1

        cleanup:
        other.close()
    }

    void "a retained bean whose definition the new context lacks is destroyed instead of adopted"() {
        given:
        ApplicationContext first = ApplicationContext.run('spec.name': 'RetainedRegistrationsSpec')
        first.getBean(Consumer)
        def retained = ((DefaultBeanContext) first).stopRetaining { it.beanType in [Expensive, Helper] }

        when: "the new context does not have the bean"
        ApplicationContext second = ApplicationContext.builder()
            .retainedRegistrations(retained)
            .start()

        then:
        Expensive.DESTROYED.get() == 1
        Scratch.DESTROYED.get() == 1
        !second.containsBean(Expensive)

        cleanup:
        second.close()
    }

    void "a retained bean is not adopted when the bean it holds is displaced by one the new context supplies"() {
        given:
        ApplicationContext first = ApplicationContext.builder()
            .properties('spec.name': 'RetainedRegistrationsSpec')
            .trackBeanDependencies(true)
            .start()
        Expensive expensive = first.getBean(Consumer).expensive
        def retained = ((DefaultBeanContext) first).stopRetaining { it.beanType == Expensive }
        Helper replacement = new Helper()

        when: "the new context supplies a helper of its own"
        ApplicationContext second = ApplicationContext.builder()
            .properties('spec.name': 'RetainedRegistrationsSpec')
            .trackBeanDependencies(true)
            .singletons(replacement)
            .retainedRegistrations(retained)
            .start()

        then: "the expensive bean, which holds the displaced helper, is destroyed and made anew on the supplied one"
        retained.size() == 2
        Expensive.DESTROYED.get() == 1
        !second.getBean(Expensive).is(expensive)
        second.getBean(Expensive).helper.is(replacement)

        cleanup:
        second.close()
    }

    void "a retained @EachProperty bean is adopted under the new context's delegate, or destroyed when its entry is gone"() {
        given: "two configured pools"
        Pool.DESTROYED.clear()
        ApplicationContext first = ApplicationContext.run(
            'spec.name': 'RetainedRegistrationsSpec', 'pools.a.size': 1, 'pools.b.size': 1)
        Pool a = first.getBean(Pool, io.micronaut.inject.qualifiers.Qualifiers.byName("a"))
        Pool b = first.getBean(Pool, io.micronaut.inject.qualifiers.Qualifiers.byName("b"))

        when: "both are retained and the new context only configures one"
        def retained = ((DefaultBeanContext) first).stopRetaining { it.beanType == Pool }
        ApplicationContext second = ApplicationContext.builder()
            .properties('spec.name': 'RetainedRegistrationsSpec', 'pools.a.size': 1)
            .retainedRegistrations(retained)
            .start()

        then: "the configured one is the same instance, the removed one was destroyed on adoption"
        retained.size() == 2
        second.getBean(Pool, io.micronaut.inject.qualifiers.Qualifiers.byName("a")).is(a)
        second.getBeansOfType(Pool).size() == 1
        !second.findBean(Pool, io.micronaut.inject.qualifiers.Qualifiers.byName("b")).isPresent()
        Pool.DESTROYED == ["b"]

        when: "the new context is restarted after being stopped"
        second.stop()
        second.start()

        then: "the adopted instance is not registered again"
        Pool.DESTROYED == ["b", "a"]
        !second.getBean(Pool, io.micronaut.inject.qualifiers.Qualifiers.byName("a")).is(a)

        cleanup:
        second.close()
    }

    void "a retained registered singleton is found through every type it exposes"() {
        given:
        Runnable task = {} as Runnable
        ApplicationContext first = ApplicationContext.run('spec.name': 'RetainedRegistrationsSpec')
        first.registerSingleton(Runnable, task)

        when:
        def retained = ((DefaultBeanContext) first).stopRetaining { it.bean.is(task) }
        ApplicationContext second = ApplicationContext.builder()
            .properties('spec.name': 'RetainedRegistrationsSpec')
            .retainedRegistrations(retained)
            .start()

        then: "the instance is a candidate for the type it was registered under, next to any compiled bean of it"
        retained.size() == 1
        second.getBeansOfType(Runnable).any { it.is(task) }
        second.getBeanRegistrations(Runnable).count { it.bean.is(task) } == 1

        cleanup:
        second.close()
    }

    void "an instance registered under several types is kept under all of them when one registration is accepted"() {
        given:
        def task = new CountingTask()
        ApplicationContext first = ApplicationContext.run('spec.name': 'RetainedRegistrationsSpec')
        first.registerSingleton(Runnable, task)
        first.registerSingleton(java.util.concurrent.Callable, task)
        first.getBean(Runnable)
        first.getBean(java.util.concurrent.Callable)

        when: "only the Callable registration is accepted"
        def retained = ((DefaultBeanContext) first).stopRetaining { it.beanDefinition.beanType == java.util.concurrent.Callable }
        ApplicationContext second = ApplicationContext.builder()
            .properties('spec.name': 'RetainedRegistrationsSpec')
            .retainedRegistrations(retained)
            .start()

        then: "the instance was not destroyed and is found under both types"
        retained.size() == 2
        second.getBean(Runnable).is(task)
        second.getBean(java.util.concurrent.Callable).is(task)

        cleanup:
        second.close()
    }

    void "a retained @EachBean member of a retained registered singleton is adopted with it"() {
        given: "a member of an @EachBean set whose origin is a registered instance"
        def feed = new Feed("x")
        ApplicationContext first = ApplicationContext.builder()
            .properties('spec.name': 'RetainedRegistrationsSpec')
            .trackBeanDependencies(true)
            .start()
        first.registerSingleton(Feed, feed, io.micronaut.inject.qualifiers.Qualifiers.byName("x"))
        FeedReader reader = first.getBean(FeedReader, io.micronaut.inject.qualifiers.Qualifiers.byName("x"))

        when: "both are retained, the member first, as the destruction order puts a bean before what it holds"
        def retained = ((DefaultBeanContext) first).stopRetaining { it.bean.is(reader) || it.bean.is(feed) }
            .sort(false) { it.bean instanceof FeedReader ? 0 : 1 }
        ApplicationContext second = ApplicationContext.builder()
            .properties('spec.name': 'RetainedRegistrationsSpec')
            .trackBeanDependencies(true)
            .retainedRegistrations(retained)
            .start()

        then: "both are adopted: the member is the same instance, on the same origin"
        retained*.bean*.getClass() == [FeedReader, Feed]
        FeedReader.DESTROYED.get() == 0
        second.getBean(Feed, io.micronaut.inject.qualifiers.Qualifiers.byName("x")).is(feed)
        second.getBean(FeedReader, io.micronaut.inject.qualifiers.Qualifiers.byName("x")).is(reader)
        second.getBeansOfType(FeedReader).size() == 1

        cleanup:
        second.close()
    }

    static class CountingTask implements Runnable, java.util.concurrent.Callable<String> {
        void run() {}
        String call() { "called" }
    }

    void "a bean holding a provider is not retained when the graph shows it"() {
        given:
        LazyHolder.DESTROYED.set(0)
        ApplicationContext first = ApplicationContext.builder()
            .properties('spec.name': 'RetainedRegistrationsSpec')
            .trackBeanDependencies(true)
            .start()
        first.getBean(Chain)
        first.getBean(Consumer)

        when: "the predicate accepts it, and a bean that holds it, anyway"
        def retained = ((DefaultBeanContext) first).stopRetaining { it.beanType in [LazyHolder, Chain, Helper] }

        then: "the provider holder and the bean holding it are destroyed, the plain bean is retained"
        retained*.bean*.getClass() == [Helper]
        LazyHolder.DESTROYED.get() == 1
    }

    void "stopping without retention destroys everything as before"() {
        given:
        ApplicationContext context = ApplicationContext.run('spec.name': 'RetainedRegistrationsSpec')
        context.getBean(Consumer)

        when:
        context.close()

        then:
        Expensive.DESTROYED.get() == 1
        Consumer.DESTROYED.get() == 1
    }
}
