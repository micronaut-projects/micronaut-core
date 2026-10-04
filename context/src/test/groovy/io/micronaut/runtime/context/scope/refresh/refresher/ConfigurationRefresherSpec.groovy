package io.micronaut.runtime.context.scope.refresh.refresher

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.context.watch.ConfigurationChange
import io.micronaut.context.watch.ConfigurationWatcher
import io.micronaut.runtime.context.scope.refresh.ConfigurationRefreshedEvent
import io.micronaut.runtime.context.scope.refresh.ConfigurationRefresher
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import spock.lang.Specification

class ConfigurationRefresherSpec extends Specification {

    void "a refresh rebinds, recreates, disposes and tells the watches, in that order, and the legacy event follows"() {
        given:
        Pool.CREATED.set(0)
        Cache.CREATED.set(0)
        Map<String, Object> values = ["spec.name": "ConfigurationRefresherSpec", "pool.url": "one", "pool.size": 5, "bound.label": "a", "cache.ttl": 1]
        def source = new MapPropertySource("test", values) {
            @Override
            Object get(String key) { values[key] }

            @Override
            Iterator<String> iterator() { values.keySet().iterator() }
        }
        def context = ApplicationContext.builder().trackBeanDependencies(true).propertySources(source).start()
        def refresher = context.getBean(ConfigurationRefresher)
        def configuration = context.getBean(PoolConfiguration)
        def bound = context.getBean(BoundConfiguration)
        def pool = context.getBean(Pool)
        def cache = context.getBean(Cache)
        def legacy = context.getBean(LegacyListener)
        List<ConfigurationRefreshedEvent> refreshed = []
        context.getEventPublisher(ConfigurationRefreshedEvent)
        context.registerSingleton(io.micronaut.context.event.ApplicationEventListener, { event -> if (event instanceof ConfigurationRefreshedEvent) refreshed << event } as io.micronaut.context.event.ApplicationEventListener)

        expect:
        configuration.url == "one"
        bound.label == "a"
        pool.url == "one"
        Pool.CREATED.get() == 1
        cache.number() > 0 // a refreshable bean is a proxy: its target exists once a method was called

        when: "a credential-like key changes: the setter bean is rebound in place and the pool applies it"
        values["pool.size"] = 9
        def result = refresher.refresh()

        then:
        result.change().changed() == ["pool.size"] as Set
        result.change().previous()["pool.size"] == 5
        result.change().current()["pool.size"] == 9
        result.rebound()*.beanType == [PoolConfiguration]
        result.recreated().isEmpty()
        configuration.size == 9
        context.getBean(Pool).is(pool)
        pool.applied == 1
        result.outcomes() == [ConfigurationWatcher.Outcome.APPLIED]
        !result.requiresRestart()
        refreshed.size() == 1
        refreshed[0].change().changed() == ["pool.size"] as Set

        and: "the legacy listener saw the event after the rebind, once"
        legacy.received.size() == 1
        legacy.urlsSeen == ["one"]
        legacy.received[0] == ["pool.size": 5]

        when: "the url changes: the watch asks for a new pool, and only the pool"
        values["pool.url"] = "two"
        result = refresher.refresh()

        then:
        result.outcomes() == [ConfigurationWatcher.Outcome.RECREATE]
        !context.getBean(Pool).is(pool)
        context.getBean(Pool).url == "two"
        Pool.CREATED.get() == 2
        context.getBean(PoolConfiguration).is(configuration)

        when: "a constructor-bound configuration changes: the instance is replaced"
        values["bound.label"] = "b"
        result = refresher.refresh()

        then:
        result.recreated()*.beanType == [BoundConfiguration]
        !context.getBean(BoundConfiguration).is(bound)
        context.getBean(BoundConfiguration).label == "b"

        when: "a refreshable bean's prefix changes: it is disposed of and created again on use"
        int cacheBefore = cache.number()
        values["cache.ttl"] = 2
        result = refresher.refresh()

        then:
        result.disposed() == 1
        cache.number() > cacheBefore

        when: "a key nobody watches is added"
        values["other.thing"] = "x"
        result = refresher.refresh()

        then: "an addition has no previous value"
        result.change().changed() == ["other.thing"] as Set
        !result.change().previous().containsKey("other.thing")
        result.change().current()["other.thing"] == "x"
        result.rebound().isEmpty()
        result.disposed() == 0
        result.outcomes().isEmpty()

        when: "someone publishes the legacy event, having refreshed the environment as such publishers do: the phases run once, driven by the scope"
        values["pool.size"] = 11
        int appliedBefore = context.getBean(Pool).applied
        def diff = context.environment.refreshAndDiff()
        context.publishEvent(new RefreshEvent(diff))

        then:
        configuration.size == 11
        context.getBean(Pool).applied == appliedBefore + 1
        legacy.received.size() == 3 // pool.size, pool.url and this one: the listener observes the pool prefix only

        cleanup:
        context.close()
    }

    void "a change is reported for a removed key and the setter bean holding it is recreated"() {
        given:
        Map<String, Object> values = ["spec.name": "ConfigurationRefresherSpec", "pool.url": "one", "pool.size": 5, "bound.label": "a"]
        def source = new MapPropertySource("test", values) {
            @Override
            Object get(String key) { values[key] }

            @Override
            Iterator<String> iterator() { values.keySet().iterator() }
        }
        def context = ApplicationContext.builder().trackBeanDependencies(true).propertySources(source).start()
        def refresher = context.getBean(ConfigurationRefresher)
        def configuration = context.getBean(PoolConfiguration)

        when:
        values.remove("pool.size")
        def result = refresher.refresh()

        then: "the value cannot be reset in place, so the bean is a new one with the default"
        result.change().changed() == ["pool.size"] as Set
        result.recreated()*.beanType == [PoolConfiguration]
        !context.getBean(PoolConfiguration).is(configuration)
        context.getBean(PoolConfiguration).size == 1

        cleanup:
        context.close()
    }

    void "without a dependency graph a setter bean that lost a key is rebound in place, so a holder that cached it is still refreshed"() {
        given:
        Map<String, Object> values = ["spec.name": "ConfigurationRefresherSpec", "pool.url": "one", "pool.size": 5, "bound.label": "a"]
        def source = new MapPropertySource("test", values) {
            @Override
            Object get(String key) { values[key] }

            @Override
            Iterator<String> iterator() { values.keySet().iterator() }
        }
        def context = ApplicationContext.builder().propertySources(source).start()
        def refresher = context.getBean(ConfigurationRefresher)
        def configuration = context.getBean(PoolConfiguration)

        expect:
        context.findDependencyGraph().isEmpty()

        when: "a key goes away"
        values.remove("pool.size")
        def result = refresher.refresh()

        then: "nothing recreates what holds the bean, so the bean stays the one they hold"
        result.change().changed() == ["pool.size"] as Set
        result.recreated().isEmpty()
        result.rebound()*.beanType == [PoolConfiguration]
        context.getBean(PoolConfiguration).is(configuration)

        when: "a later change"
        values["pool.url"] = "two"
        refresher.refresh()

        then: "reaches the instance the holder cached"
        configuration.url == "two"

        cleanup:
        context.close()
    }

    void "without a dependency graph the beans holding a recreated configuration are found by type"() {
        given:
        Map<String, Object> values = ["spec.name": "ConfigurationRefresherSpec", "pool.url": "one", "bound.label": "a"]
        def source = new MapPropertySource("test", values) {
            @Override
            Object get(String key) { values[key] }

            @Override
            Iterator<String> iterator() { values.keySet().iterator() }
        }
        def context = ApplicationContext.builder().propertySources(source).start()
        def user = context.getBean(BoundUser)

        expect:
        context.findDependencyGraph().isEmpty()
        user.configuration.label == "a"

        when:
        values["bound.label"] = "b"
        def result = context.getBean(ConfigurationRefresher).refresh()

        then: "the configuration is a new instance and so is the bean that held it"
        result.recreated()*.beanType == [BoundConfiguration]
        !context.getBean(BoundUser).is(user)
        context.getBean(BoundUser).configuration.label == "b"

        cleanup:
        context.close()
    }

    void "the singletons holding a removed @EachProperty entry go with it, with a dependency graph: #graph"() {
        given:
        Map<String, Object> values = ["spec.name": "ConfigurationRefresherSpec.eachProperty", "endpoints.one.url": "a", "endpoints.two.url": "b"]
        def source = new MapPropertySource("test", values) {
            @Override
            Object get(String key) { values[key] }

            @Override
            Iterator<String> iterator() { values.keySet().iterator() }
        }
        def context = ApplicationContext.builder().trackBeanDependencies(graph).propertySources(source).start()
        def refresher = context.getBean(ConfigurationRefresher)
        def user = context.getBean(EndpointUser)
        def users = context.getBean(EndpointsUser)
        def other = context.getBean(OtherEndpointUser)

        expect:
        context.findDependencyGraph().isPresent() == graph
        user.endpoint.url == "b"
        users.endpoints*.url.sort() == ["a", "b"]

        when: "the entry the singletons hold is removed"
        values.remove("endpoints.two.url")
        def result = refresher.refresh()

        then: "the entry is gone, and so are the instances that held it"
        result.recreated()*.beanType == [EndpointConfiguration]
        !context.getActiveBeanRegistrations(EndpointUser).any { it.bean.is(user) }
        !context.getActiveBeanRegistrations(EndpointsUser).any { it.bean.is(users) }

        and: "a holder of another entry did not receive the removed one and keeps its instance"
        context.getActiveBeanRegistrations(OtherEndpointUser).any { it.bean.is(other) }
        context.getBean(OtherEndpointUser).is(other)

        and: "a holder of every entry is created again with the ones that remain"
        context.getBean(EndpointsUser).endpoints*.url == ["a"]

        when: "the holder of the removed entry is looked up"
        context.getBean(EndpointUser)

        then: "it cannot be created, as at a startup without the entry"
        thrown(io.micronaut.context.exceptions.DependencyInjectionException)

        cleanup:
        context.close()

        where:
        graph << [true, false]
    }

    void "refreshAll treats everything as changed"() {
        given:
        def context = ApplicationContext.run(["spec.name": "ConfigurationRefresherSpec", "pool.url": "one", "bound.label": "a"])
        def refresher = context.getBean(ConfigurationRefresher)
        context.getBean(Pool)
        context.getBean(BoundConfiguration)

        when:
        def result = refresher.refreshAll()

        then:
        result.change().all()
        result.change().touches("anything")
        result.rebound()*.beanType.contains(PoolConfiguration)
        result.recreated()*.beanType.contains(BoundConfiguration)
        result.outcomes() == [ConfigurationWatcher.Outcome.RECREATE]

        cleanup:
        context.close()
    }
}
