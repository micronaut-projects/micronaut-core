package io.micronaut.inject.lifecycle.destroyorder

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.core.annotation.Order

/**
 * The order in which singletons are destroyed when the context is closed.
 */
class BeanDestructionOrderSpec extends AbstractTypeElementSpec {

    static final String HEADER = '''
package test;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
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

    void "@Order controls the destruction order of independent beans, lowest first"() {
        given:
        ApplicationContext ctx = buildContext(HEADER + '''
@Singleton @Order(30)
class Producer {
    @PreDestroy void close() { Log.add("producer"); }
}
@Singleton @Order(10)
class Consumer {
    @PreDestroy void close() { Log.add("consumer"); }
}
@Singleton @Order(20)
class Dao {
    @PreDestroy void close() { Log.add("dao"); }
}
@Singleton @Order(-5)
class Cache {
    @PreDestroy void close() { Log.add("cache"); }
}
@Singleton
class Unordered {
    @PreDestroy void close() { Log.add("unordered"); }
}
''')
        def log = ctx.classLoader.loadClass('test.Log')
        ['Producer', 'Unordered', 'Dao', 'Cache', 'Consumer'].each { ctx.getBean(ctx.classLoader.loadClass("test.$it")) }

        when:
        ctx.close()

        then:
        log.EVENTS == ['cache', 'unordered', 'consumer', 'dao', 'producer']
    }

    void "Ordered interface controls the destruction order of independent beans"() {
        given:
        ApplicationContext ctx = buildContext(HEADER + '''
@Singleton
class Second implements Ordered {
    public int getOrder() { return 2; }
    @PreDestroy void close() { Log.add("second"); }
}
@Singleton
class First implements Ordered {
    public int getOrder() { return 1; }
    @PreDestroy void close() { Log.add("first"); }
}
@Singleton
class Third implements Ordered {
    public int getOrder() { return 3; }
    @PreDestroy void close() { Log.add("third"); }
}
''')
        def log = ctx.classLoader.loadClass('test.Log')
        ['Third', 'First', 'Second'].each { ctx.getBean(ctx.classLoader.loadClass("test.$it")) }

        when:
        ctx.close()

        then:
        log.EVENTS == ['first', 'second', 'third']
    }

    void "dependencies take precedence over @Order"() {
        given:
        ApplicationContext ctx = buildContext(HEADER + '''
@Singleton @Order(1)
class Producer {
    @PreDestroy void close() { Log.add("producer"); }
}
@Singleton @Order(2)
class Consumer {
    Consumer(Producer producer) {}
    @PreDestroy void close() { Log.add("consumer"); }
}
@Singleton @Order(3)
class Other {
    @PreDestroy void close() { Log.add("other"); }
}
''')
        def log = ctx.classLoader.loadClass('test.Log')
        ['Producer', 'Consumer', 'Other'].each { ctx.getBean(ctx.classLoader.loadClass("test.$it")) }

        when:
        ctx.close()

        then:
        log.EVENTS == ['consumer', 'producer', 'other']
    }

    void "beans of equal precedence are destroyed in a stable order"() {
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

    void "a dependency cycle is destroyed starting with the highest precedence bean"() {
        given:
        ApplicationContext ctx = buildContext(HEADER + '''
@Singleton @Order(2)
class A {
    A(BeanProvider<B> b) {}
    @PreDestroy void close() { Log.add("a"); }
}
@Singleton @Order(1)
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
        log.EVENTS == ['b', 'a']
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
