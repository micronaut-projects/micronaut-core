package io.micronaut.inject.lifecycle.destroyorder

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.annotation.Order

/**
 * The order in which singletons are created and destroyed.
 */
class BeanDestructionOrderSpec extends AbstractTypeElementSpec {

    static final String HEADER = '''
package test;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.DependsOn;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.core.annotation.Order;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class Log {
    static final List<String> EVENTS = new CopyOnWriteArrayList<>();
    static void add(String event) { EVENTS.add(event); }
}
'''

    void "field injected dependency #dependency outlives dependent #dependent"() {
        given:
        ApplicationContext ctx = buildContext(HEADER + """
@Singleton
class $dependent {
    @Inject $dependency dependency;
    @PreDestroy void close() { Log.add("dependent"); }
}
@Singleton
class $dependency {
    @PreDestroy void close() { Log.add("dependency"); }
}
""")
        def log = ctx.classLoader.loadClass('test.Log')
        ctx.getBean(ctx.classLoader.loadClass("test.$dependency"))
        ctx.getBean(ctx.classLoader.loadClass("test.$dependent"))

        expect:
        ctx.getBeanDefinition(ctx.classLoader.loadClass("test.$dependent")).requiredComponents == [ctx.classLoader.loadClass("test.$dependency")] as Set

        when:
        ctx.close()

        then:
        log.EVENTS == ['dependent', 'dependency']

        where:
        dependent  | dependency
        'Consumer' | 'Producer'
        'Producer' | 'Consumer'
        'Alpha'    | 'Beta'
        'Beta'     | 'Alpha'
    }

    void "@Value fields are not dependencies"() {
        given:
        ApplicationContext ctx = buildContext(HEADER + '''
@Singleton
class Configured {
    @io.micronaut.context.annotation.Value("${foo.bar:default}") String value;
    @Inject Dependency dependency;
}
@Singleton
class Dependency {
}
''')

        expect:
        ctx.getBeanDefinition(ctx.classLoader.loadClass("test.Configured")).requiredComponents == [ctx.classLoader.loadClass("test.Dependency")] as Set

        cleanup:
        ctx.close()
    }

    void "constructor and method injected dependencies outlive dependents"() {
        given:
        ApplicationContext ctx = buildContext(HEADER + '''
@Singleton
class Service {
    Service(Repository repository) {}
    @Inject void setCache(Cache cache) {}
    @PreDestroy void close() { Log.add("service"); }
}
@Singleton
class Repository {
    Repository(DataSource dataSource) {}
    @PreDestroy void close() { Log.add("repository"); }
}
@Singleton
class Cache {
    @PreDestroy void close() { Log.add("cache"); }
}
@Singleton
class DataSource {
    @PreDestroy void close() { Log.add("dataSource"); }
}
''')
        def log = ctx.classLoader.loadClass('test.Log')
        ['DataSource', 'Cache', 'Repository', 'Service'].each { ctx.getBean(ctx.classLoader.loadClass("test.$it")) }

        when:
        ctx.close()

        then:
        log.EVENTS.indexOf('service') < log.EVENTS.indexOf('repository')
        log.EVENTS.indexOf('service') < log.EVENTS.indexOf('cache')
        log.EVENTS.indexOf('repository') < log.EVENTS.indexOf('dataSource')
    }

    void "@DependsOn creates the dependency first and destroys it last"() {
        given:
        ApplicationContext ctx = buildContext(HEADER + '''
@Singleton
@DependsOn(Producer.class)
class Consumer {
    Consumer() { Log.add("consumer created"); }
    @PreDestroy void close() { Log.add("consumer destroyed"); }
}
@Singleton
class Producer {
    Producer() { Log.add("producer created"); }
    @PreDestroy void close() { Log.add("producer destroyed"); }
}
''')
        def log = ctx.classLoader.loadClass('test.Log')
        def consumer = ctx.classLoader.loadClass("test.Consumer")
        def producer = ctx.classLoader.loadClass("test.Producer")

        when:
        ctx.getBean(consumer)

        then:
        ctx.getBeanDefinition(consumer).requiredComponents == [producer] as Set
        log.EVENTS == ['producer created', 'consumer created']

        when:
        ctx.close()

        then:
        log.EVENTS == ['producer created', 'consumer created', 'consumer destroyed', 'producer destroyed']
    }

    void "@DependsOn on an interface applies to every implementation"() {
        given:
        ApplicationContext ctx = buildContext(HEADER + '''
interface Channel {}
@Singleton
@DependsOn(Channel.class)
class Consumer {
    Consumer() { Log.add("consumer created"); }
    @PreDestroy void close() { Log.add("consumer destroyed"); }
}
@Singleton
class TopicChannel implements Channel {
    TopicChannel() { Log.add("topic created"); }
    @PreDestroy void close() { Log.add("topic destroyed"); }
}
@Singleton
class QueueChannel implements Channel {
    QueueChannel() { Log.add("queue created"); }
    @PreDestroy void close() { Log.add("queue destroyed"); }
}
''')
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.getBean(ctx.classLoader.loadClass("test.Consumer"))

        then:
        log.EVENTS.indexOf('consumer created') == 2

        when:
        ctx.close()

        then:
        log.EVENTS.indexOf('consumer destroyed') == 3
        log.EVENTS.indexOf('topic destroyed') > 3
        log.EVENTS.indexOf('queue destroyed') > 3
    }

    void "@DependsOn on a factory method"() {
        given:
        ApplicationContext ctx = buildContext(HEADER + '''
class Consumer {
    Consumer() { Log.add("consumer created"); }
    void stop() { Log.add("consumer destroyed"); }
}
@Factory
class ConsumerFactory {
    @Singleton
    @Bean(preDestroy = "stop")
    @DependsOn(Producer.class)
    Consumer consumer() { return new Consumer(); }
}
@Singleton
class Producer {
    Producer() { Log.add("producer created"); }
    @PreDestroy void close() { Log.add("producer destroyed"); }
}
''')
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.getBean(ctx.classLoader.loadClass("test.Consumer"))

        then:
        log.EVENTS == ['producer created', 'consumer created']

        when:
        ctx.close()

        then:
        log.EVENTS == ['producer created', 'consumer created', 'consumer destroyed', 'producer destroyed']
    }

    void "@DependsOn on a type with no bean fails"() {
        given:
        ApplicationContext ctx = buildContext(HEADER + '''
interface Missing {}
@Singleton
@DependsOn(Missing.class)
class Consumer {
}
''')

        when:
        ctx.getBean(ctx.classLoader.loadClass("test.Consumer"))

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains('depends on [test.Missing] but no bean of that type exists')

        cleanup:
        ctx.close()
    }

    void "beans without dependencies are destroyed in a stable order"() {
        given:
        def source = new StringBuilder(HEADER)
        def names = (0..<12).collect { "Bean${String.format('%02d', it)}" }
        names.each { name ->
            source << """
@Singleton
class $name {
    @PreDestroy void close() { Log.add("$name"); }
}
"""
        }
        ApplicationContext ctx = buildContext(source.toString())
        def log = ctx.classLoader.loadClass('test.Log')
        names.reverse().each { ctx.getBean(ctx.classLoader.loadClass("test.$it")) }

        when:
        ctx.close()

        then:
        log.EVENTS == names
    }

    void "a dependency cycle is still destroyed"() {
        given:
        ApplicationContext ctx = buildContext(HEADER + '''
@Singleton
class A {
    A(BeanProvider<B> b) {}
    @PreDestroy void close() { Log.add("a"); }
}
@Singleton
class B {
    B(BeanProvider<A> a) {}
    @PreDestroy void close() { Log.add("b"); }
}
''')
        def log = ctx.classLoader.loadClass('test.Log')
        ['A', 'B'].each { ctx.getBean(ctx.classLoader.loadClass("test.$it")) }

        when:
        ctx.close()

        then:
        log.EVENTS == ['a', 'b']
    }

    void "a bean required by a dependency cycle is not used to break the cycle"() {
        given:
        ApplicationContext ctx = buildContext(HEADER + '''
@Singleton
class Aardvark {
    @PreDestroy void close() { Log.add("aardvark"); }
}
@Singleton
class Beta {
    Beta(BeanProvider<Gamma> gamma, Aardvark aardvark) {}
    @PreDestroy void close() { Log.add("beta"); }
}
@Singleton
class Gamma {
    Gamma(BeanProvider<Beta> beta) {}
    @PreDestroy void close() { Log.add("gamma"); }
}
''')
        def log = ctx.classLoader.loadClass('test.Log')
        ['Aardvark', 'Beta', 'Gamma'].each { ctx.getBean(ctx.classLoader.loadClass("test.$it")) }

        when:
        ctx.close()

        then: "the cycle is broken at Beta, the first bean on the cycle, so Aardvark still outlives it"
        log.EVENTS == ['beta', 'aardvark', 'gamma']
    }

    void "@Order on @EventListener methods orders ShutdownEvent listeners"() {
        given:
        ApplicationContext ctx = buildContext(HEADER + '''
@Singleton
class Hooks {
    @Order(3) @EventListener void closeProducer(ShutdownEvent event) { Log.add("producer"); }
    @Order(1) @EventListener void stopConsumer(ShutdownEvent event) { Log.add("consumer"); }
    @Order(2) @EventListener void flushDao(ShutdownEvent event) { Log.add("dao"); }
}
@Singleton @Order(10)
class ClassOrdered {
    @EventListener void onShutdown(ShutdownEvent event) { Log.add("classOrdered"); }
    @Order(0) @EventListener void methodOverridesClass(ShutdownEvent event) { Log.add("methodOverridesClass"); }
}
''')
        def log = ctx.classLoader.loadClass('test.Log')
        ['Hooks', 'ClassOrdered'].each { ctx.getBean(ctx.classLoader.loadClass("test.$it")) }

        expect:
        ctx.getBeanDefinitions(ApplicationEventListener).collect { it.intValue(Order).orElse(0) }.sort() == [0, 1, 2, 3, 10]

        when:
        ctx.close()

        then:
        log.EVENTS == ['methodOverridesClass', 'consumer', 'dao', 'producer', 'classOrdered']
    }
}
