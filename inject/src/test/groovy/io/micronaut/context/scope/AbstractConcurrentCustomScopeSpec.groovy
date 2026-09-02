package io.micronaut.context.scope

import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanIdentifier
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AbstractConcurrentCustomScopeSpec extends Specification {

    void "remove evicts the bean so the next resolution creates a new instance"() {
        given:
        def id = BeanIdentifier.of("myBean")
        def creationContext = new TestCreationContext(id)

        when:
        def first = scope.getOrCreate(creationContext)

        then:
        scope.scopeMap.size() == 1

        when:
        def removed = scope.remove(id)

        then:
        removed.present
        removed.get().is(first)
        creationContext.created[0].closed
        scope.scopeMap.isEmpty()

        when:
        def second = scope.getOrCreate(creationContext)

        then:
        !second.is(first)
        creationContext.created.size() == 2
        !creationContext.created[1].closed

        where:
        scope << [new TestScope(), new TestScope(true)]
    }

    void "remove of an unknown identifier is empty and leaves the scope untouched"() {
        given:
        def creationContext = new TestCreationContext(BeanIdentifier.of("myBean"))
        def bean = scope.getOrCreate(creationContext)

        when:
        def removed = scope.remove(BeanIdentifier.of("otherBean"))

        then:
        !removed.present
        scope.scopeMap.size() == 1
        scope.getOrCreate(creationContext).is(bean)

        where:
        scope << [new TestScope(), new TestScope(true)]
    }

    void "stop destroys the beans and closes the scope"() {
        given:
        def first = new TestCreationContext(BeanIdentifier.of("first"))
        def second = new TestCreationContext(BeanIdentifier.of("second"))
        scope.getOrCreate(first)
        scope.getOrCreate(second)

        when:
        scope.stop()

        then:
        first.created[0].closed
        second.created[0].closed
        scope.scopeMap.isEmpty()
        scope.closed

        where:
        scope << [new TestScope(), new TestScope(true)]
    }

    void "the registration of a held bean is found and an unknown bean is not"() {
        given:
        def creationContext = new TestCreationContext(BeanIdentifier.of("myBean"))
        def bean = scope.getOrCreate(creationContext)

        expect:
        scope.findBeanRegistration(bean).get().bean.is(bean)
        !scope.findBeanRegistration(new Object()).present

        where:
        scope << [new TestScope(), new TestScope(true)]
    }

    void "under the scope-wide lock a creation cannot wait for another thread creating another bean of the scope"() {
        given:
        def scope = new TestScope()
        def other = new TestCreationContext(BeanIdentifier.of("other"))
        def otherThread = new AtomicReference<Thread>()
        def waiting = new TestCreationContext(BeanIdentifier.of("waiting"), {
            def thread = new Thread({ scope.getOrCreate(other) })
            otherThread.set(thread)
            thread.start()
            thread.join(300)
            assert thread.alive: "the other thread should still be blocked on the scope-wide lock"
        })

        when:
        scope.getOrCreate(waiting)
        otherThread.get().join(5000)

        then: "the other thread only got to create once the waiting creation released the lock"
        !otherThread.get().alive
        scope.scopeMap.size() == 2
    }

    void "under a lock per bean a creation can wait for another thread creating another bean of the scope"() {
        given:
        def scope = new TestScope(true)
        def other = new TestCreationContext(BeanIdentifier.of("other"))
        def waiting = new TestCreationContext(BeanIdentifier.of("waiting"), {
            def thread = new Thread({ scope.getOrCreate(other) })
            thread.start()
            thread.join(5000)
            assert !thread.alive: "the other thread should have created its bean while this creation waited"
        })

        when:
        scope.getOrCreate(waiting)

        then:
        scope.scopeMap.size() == 2
        other.created.size() == 1
        waiting.created.size() == 1
    }

    void "under a lock per bean concurrent creations of one identifier create once and hand out the same bean"() {
        given:
        def scope = new TestScope(true)
        def creationContext = new TestCreationContext(BeanIdentifier.of("myBean"), { Thread.sleep(100) })
        int threads = 8
        def executor = Executors.newFixedThreadPool(threads)
        def start = new CountDownLatch(1)

        when:
        def futures = (1..threads).collect {
            executor.submit({
                start.await()
                scope.getOrCreate(creationContext)
            } as java.util.concurrent.Callable<Object>)
        }
        start.countDown()
        def beans = futures.collect { it.get(10, TimeUnit.SECONDS) }

        then:
        creationContext.created.size() == 1
        beans.toSet().size() == 1
        scope.scopeMap.size() == 1

        cleanup:
        executor.shutdownNow()
    }

    void "under a lock per bean creations of different identifiers run in parallel"() {
        given:
        def scope = new TestScope(true)
        int threads = 4
        def executor = Executors.newFixedThreadPool(threads)
        // every creation waits for all the others to have begun, which only parallel creations can
        def allCreating = new CountDownLatch(threads)
        def contexts = (1..threads).collect {
            new TestCreationContext(BeanIdentifier.of("bean$it"), {
                allCreating.countDown()
                assert allCreating.await(5, TimeUnit.SECONDS): "the other creations should be in flight too"
            })
        }

        when:
        def futures = contexts.collect { ctx -> executor.submit({ scope.getOrCreate(ctx) } as java.util.concurrent.Callable<Object>) }
        futures.each { it.get(10, TimeUnit.SECONDS) }

        then:
        scope.scopeMap.size() == threads

        cleanup:
        executor.shutdownNow()
    }

    void "under a lock per bean remove waits for a creation of the identifier in flight and destroys what it created"() {
        given:
        def scope = new TestScope(true)
        def id = BeanIdentifier.of("myBean")
        def creating = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def creationContext = new TestCreationContext(id, {
            creating.countDown()
            release.await(5, TimeUnit.SECONDS)
        })
        def removed = new AtomicReference<Optional<Object>>()

        when:
        def creator = Thread.start { scope.getOrCreate(creationContext) }
        creating.await(5, TimeUnit.SECONDS)
        def remover = Thread.start { removed.set(scope.remove(id)) }
        remover.join(300)

        then: "the remover waits for the creation"
        remover.alive
        removed.get() == null

        when:
        release.countDown()
        creator.join(5000)
        remover.join(5000)

        then:
        !creator.alive
        !remover.alive
        removed.get().present
        removed.get().get().is(creationContext.created[0].bean)
        creationContext.created[0].closed
        scope.scopeMap.isEmpty()
    }

    void "under a lock per bean a failed creation leaves nothing behind and the next creation succeeds"() {
        given:
        def scope = new TestScope(true)
        def creationContext = new TestCreationContext(BeanIdentifier.of("myBean"), { throw new IllegalStateException("Bad things") })

        when:
        scope.getOrCreate(creationContext)

        then:
        thrown(IllegalStateException)
        scope.scopeMap.isEmpty()

        when:
        creationContext.onCreate = {}
        def bean = scope.getOrCreate(creationContext)

        then:
        bean != null
        scope.scopeMap.size() == 1
        scope.getOrCreate(creationContext).is(bean)
    }

    void "under a lock per bean the scope map must be a concurrent map"() {
        given:
        def scope = new TestScope(true, new HashMap<BeanIdentifier, CreatedBean<?>>())

        when:
        scope.getOrCreate(new TestCreationContext(BeanIdentifier.of("myBean")))

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("ConcurrentMap")
    }

    void "under a lock per bean a scope map that is not available is handled as under the scope-wide lock"() {
        given:
        def scope = new TestScope(lockPerBean, null)

        expect:
        !scope.remove(BeanIdentifier.of("myBean")).present
        !scope.findBeanRegistration(new Object()).present
        scope.stop().closed

        when:
        scope.getOrCreate(new TestCreationContext(BeanIdentifier.of("myBean")))

        then:
        thrown(IllegalStateException)

        where:
        lockPerBean << [false, true]
    }

    static class TestScope extends AbstractConcurrentCustomScope<Singleton> {

        final Map<BeanIdentifier, CreatedBean<?>> scopeMap
        boolean closed

        TestScope() {
            this(false)
        }

        TestScope(boolean lockPerBean) {
            this(lockPerBean, new ConcurrentHashMap<>())
        }

        TestScope(boolean lockPerBean, Map<BeanIdentifier, CreatedBean<?>> scopeMap) {
            super(Singleton, lockPerBean)
            this.scopeMap = scopeMap
        }

        @Override
        boolean isRunning() {
            return true
        }

        @Override
        protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
            if (scopeMap == null) {
                throw new IllegalStateException("No scope map")
            }
            return scopeMap
        }

        @Override
        void close() {
            closed = true
            destroyScope(scopeMap)
        }
    }

    static class TestCreationContext implements BeanCreationContext<Object> {

        final BeanIdentifier id
        final List<TestCreatedBean> created = []
        Runnable onCreate

        TestCreationContext(BeanIdentifier id, Runnable onCreate = {}) {
            this.id = id
            this.onCreate = onCreate
        }

        @Override
        BeanDefinition<Object> definition() {
            return null
        }

        @Override
        BeanIdentifier id() {
            return id
        }

        @Override
        CreatedBean<Object> create() {
            onCreate.run()
            def createdBean = new TestCreatedBean(id: id, bean: new Object())
            created << createdBean
            return createdBean
        }
    }

    static class TestCreatedBean implements CreatedBean<Object> {

        BeanIdentifier id
        Object bean
        boolean closed

        @Override
        BeanDefinition<Object> definition() {
            return null
        }

        @Override
        Object bean() {
            return bean
        }

        @Override
        BeanIdentifier id() {
            return id
        }

        @Override
        void close() {
            closed = true
        }
    }
}
