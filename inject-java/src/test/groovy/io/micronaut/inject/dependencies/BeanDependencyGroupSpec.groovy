package io.micronaut.inject.dependencies

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.type.Argument

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class BeanDependencyGroupSpec extends AbstractTypeElementSpec {
    private static final String HEADER = '''
package test;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.type.Argument;
import jakarta.inject.*;
import jakarta.annotation.*;
import java.util.*;
import java.util.concurrent.*;
class Log { static final List<String> events = new CopyOnWriteArrayList<>(); }
@Prototype class Resource {
    @PreDestroy void stop() { Log.events.add("resource"); }
}
@Singleton class Shared {
    @PreDestroy void stop() { Log.events.add("shared"); }
}
'''

    void "fresh singleton registrations retain dependencies without replacing the scoped instance"() {
        given:
        def ctx = buildContext(HEADER + '''
@Singleton class Owner {
    final Resource resource;
    Owner(Resource resource) { this.resource = resource; }
    @PreDestroy void stop() { Log.events.add("owner"); }
}
''')
        def type = ctx.classLoader.loadClass('test.Owner')
        def log = ctx.classLoader.loadClass('test.Log')
        def singleton = ctx.getBean(type)
        def first = ctx.createBeanRegistration(ctx.getBeanDefinition(type))
        def second = ctx.createBeanRegistration(ctx.getBeanDefinition(type))

        expect:
        !first.bean().is(singleton)
        !first.bean().is(second.bean())
        !first.bean().resource.is(second.bean().resource)

        when:
        first.close()
        first.close()
        second.close()

        then:
        log.events == ['owner', 'resource', 'owner', 'resource']
        ctx.getBean(type).is(singleton)

        when:
        ctx.close()

        then:
        log.events == ['owner', 'resource', 'owner', 'resource', 'owner', 'resource']
    }

    void "groups remove only the exact owned registration and never destroy a shared bean"() {
        given:
        def ctx = buildContext(HEADER)
        def type = Argument.of(ctx.classLoader.loadClass('test.Resource'))
        def log = ctx.classLoader.loadClass('test.Log')
        def group = ctx.createDependencyGroup()
        def first = group.getBeanRegistration(type)
        def second = group.getBeanRegistration(type.type)
        def shared = group.getBeanRegistration(ctx.classLoader.loadClass('test.Shared'), null)

        expect:
        !group.isClosed()
        first == second // registrations compare by definition, but ownership compares by identity
        !first.is(second)
        !group.destroy(shared)
        group.destroy(first)
        !group.destroy(first)
        log.events == ['resource']

        when:
        group.close()
        group.close()

        then:
        group.isClosed()
        log.events == ['resource', 'resource']

        when:
        group.getBeanRegistration(type)

        then:
        thrown(IllegalStateException)

        cleanup:
        ctx.close()
    }

    void "child groups belong to the injected consumer and may be closed early"() {
        given:
        def ctx = buildContext(HEADER + '''
@Singleton class Owner {
    final BeanDependencyGroup early;
    final BeanDependencyGroup late;
    Owner(BeanDependencyResolver resolver) {
        early = resolver.createGroup();
        late = resolver.createGroup();
        early.getBean(Resource.class);
        late.getBean(Resource.class);
    }
    @PreDestroy void stop() { Log.events.add("owner"); }
}
''')
        def owner = ctx.getBean(ctx.classLoader.loadClass('test.Owner'))
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        owner.early.close()
        ctx.close()

        then:
        log.events == ['resource', 'owner', 'resource']

        when:
        owner.late.getBean(String)

        then:
        thrown(IllegalStateException)
    }

    void "temporary destruction dependencies use the explicit event context"() {
        given:
        def ctx = buildContext(HEADER + '''
@Singleton class Owner {
    @PreDestroy void stop() { Log.events.add("finished"); }
}
@Singleton class Disposer implements io.micronaut.context.event.BeanPreDestroyEventListener<Owner> {
    static BeanDependencyGroup escaped;
    static BeanResolutionContext invocation;
    private final BeanContext context;
    Disposer(BeanContext context) { this.context = context; }
    public Owner onPreDestroy(io.micronaut.context.event.BeanPreDestroyEvent<Owner> event) {
        invocation = event.getResolutionContext();
        invocation.withDependencies(group -> {
            escaped = group;
            group.getBean(Resource.class);
            group.createGroup().getBean(Resource.class);
            invocation.withDependencies(nested -> {
                nested.getBean(Resource.class);
                Log.events.add("nested");
                return null;
            });
            try {
                context.withDependencies(ordinary -> null);
                throw new AssertionError("ordinary context allowed resolution during shutdown");
            } catch (IllegalStateException expected) {
                Log.events.add("ordinary rejected");
            }
            Log.events.add("callback");
            return null;
        });
        return event.getBean();
    }
}
''')
        def type = ctx.classLoader.loadClass('test.Disposer')
        ctx.getBean(ctx.classLoader.loadClass('test.Owner'))
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.close()

        then:
        log.events == ['nested', 'resource', 'ordinary rejected', 'callback', 'resource', 'resource', 'finished']

        when:
        type.escaped.getBean(String)

        then:
        thrown(IllegalStateException)

        when:
        type.invocation.withDependencies { null }

        then:
        thrown(IllegalStateException)

        when:
        ctx.withDependencies { null }

        then:
        thrown(IllegalStateException)
    }

    void "destruction permission cannot escape through another thread or a context copy"() {
        given:
        def ctx = buildContext(HEADER + '''
@Singleton class Owner { }
@Singleton class Disposer implements io.micronaut.context.event.BeanPreDestroyEventListener<Owner> {
    public Owner onPreDestroy(io.micronaut.context.event.BeanPreDestroyEvent<Owner> event) {
        BeanResolutionContext invocation = event.getResolutionContext();
        invocation.withDependencies(group -> {
            CompletableFuture.runAsync(() -> {
                try {
                    invocation.withDependencies(other -> null);
                    throw new AssertionError("cross-thread invocation allowed");
                } catch (IllegalStateException expected) { Log.events.add("thread rejected"); }
                try {
                    group.getBean(Resource.class);
                    throw new AssertionError("cross-thread lookup allowed");
                } catch (IllegalStateException expected) { Log.events.add("group thread rejected"); }
            }).join();
            try (BeanResolutionContext copy = invocation.copy()) {
                try {
                    copy.withDependencies(other -> null);
                    throw new AssertionError("copy granted destruction permission");
                } catch (IllegalStateException expected) { Log.events.add("copy rejected"); }
            }
            group.getBean(Resource.class);
            return null;
        });
        return event.getBean();
    }
}
''')
        ctx.getBean(ctx.classLoader.loadClass('test.Owner'))
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.close()

        then:
        log.events == ['thread rejected', 'group thread rejected', 'copy rejected', 'resource']
    }

    void "disposable definitions receive an explicit context whose permission ends with destruction"() {
        given:
        def ctx = buildContext(HEADER)
        def resource = ctx.classLoader.loadClass('test.Resource')
        def log = ctx.classLoader.loadClass('test.Log')
        def bean = new Object()
        def invocation
        io.micronaut.inject.DisposableBeanDefinition<Object> definition = Stub() {
            getBeanType() >> Object
            getAnnotationMetadata() >> io.micronaut.core.annotation.AnnotationMetadata.EMPTY_METADATA
            dispose(_, _, _) >> { resolution, context, target ->
                invocation = resolution
                resolution.withDependencies { group ->
                    group.getBean(resource)
                    log.events.add('definition')
                    null
                }
                target
            }
        }
        def registration = io.micronaut.context.BeanRegistration.of(ctx,
            io.micronaut.inject.BeanIdentifier.of('probe'), definition, bean)

        when:
        ctx.destroyBean(registration)

        then:
        log.events == ['definition', 'resource']

        when: 'even while the application is still running, the destruction context cannot be reused'
        invocation.withDependencies { null }

        then:
        thrown(IllegalStateException)

        cleanup:
        ctx.close()
    }

    void "failed destruction invocations release all temporary dependencies and suppress cleanup failures for #failureType"() {
        given:
        def ctx = buildContext(HEADER + """
@Singleton class Owner { }
@Singleton class Failure implements io.micronaut.context.event.BeanPreDestroyEventListener<Resource> {
    public Resource onPreDestroy(io.micronaut.context.event.BeanPreDestroyEvent<Resource> event) {
        Log.events.add("attempt");
        throw new ${failureType}("cleanup");
    }
}
@Singleton class Disposer implements io.micronaut.context.event.BeanPreDestroyEventListener<Owner> {
    static RuntimeException failure;
    static BeanDependencyGroup escaped;
    public Owner onPreDestroy(io.micronaut.context.event.BeanPreDestroyEvent<Owner> event) {
        try {
            event.withDependencies(group -> {
                escaped = group;
                group.getBean(Resource.class);
                group.getBean(Resource.class);
                throw new IllegalArgumentException("invocation");
            });
        } catch (RuntimeException e) { failure = e; }
        return event.getBean();
    }
}
""")
        ctx.getBean(ctx.classLoader.loadClass('test.Owner'))
        def disposer = ctx.classLoader.loadClass('test.Disposer')
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.close()

        then:
        disposer.failure instanceof IllegalArgumentException
        disposer.failure.message == 'invocation'
        disposer.failure.suppressed.length == 1
        disposer.failure.suppressed[0].suppressed.length == 1
        disposer.escaped.closed
        log.events == ['attempt', 'attempt']
        where:
        failureType << ['IllegalStateException', 'AssertionError']
    }

    void "closing during creation rolls back the unpublished registration"() {
        given:
        def ctx = buildContext(HEADER + '''
@Prototype class Slow {
    static final CountDownLatch entered = new CountDownLatch(1);
    static final CountDownLatch proceed = new CountDownLatch(1);
    Slow(Resource resource) throws Exception { entered.countDown(); proceed.await(); }
    @PreDestroy void stop() { Log.events.add("slow"); }
}
''')
        def slow = ctx.classLoader.loadClass('test.Slow')
        def log = ctx.classLoader.loadClass('test.Log')
        def group = ctx.createDependencyGroup()
        def result = CompletableFuture.supplyAsync { group.getBean(slow) }
        assert slow.entered.await(10, TimeUnit.SECONDS)

        when:
        group.close()
        slow.proceed.countDown()
        result.get(10, TimeUnit.SECONDS)

        then:
        def e = thrown(java.util.concurrent.ExecutionException)
        e.cause instanceof IllegalStateException
        log.events == ['slow', 'resource']

        cleanup:
        slow.proceed.countDown()
        ctx.close()
    }

    void "invocation failure retains cleanup failures and releases every sibling for #failureType"() {
        given:
        def ctx = buildContext(HEADER + """
@Singleton class Failure implements io.micronaut.context.event.BeanPreDestroyEventListener<Resource> {
    public Resource onPreDestroy(io.micronaut.context.event.BeanPreDestroyEvent<Resource> event) {
        Log.events.add("attempt");
        throw new ${failureType}("cleanup");
    }
}
""")
        def type = ctx.classLoader.loadClass('test.Resource')
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.withDependencies { group ->
            group.getBean(type)
            group.getBean(type)
            throw new IllegalArgumentException('invocation')
        }

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'invocation'
        e.suppressed.length == 1
        e.suppressed[0].suppressed.length == 1
        log.events == ['attempt', 'attempt']

        cleanup:
        ctx.close()
        where:
        failureType << ['IllegalStateException', 'AssertionError']
    }
    void "registration cleanup preserves destruction failures and releases siblings - owner failure #ownerFails cleanup #failureType"() {
        given:
        def ctx = buildContext(HEADER + """
@Prototype class Owner {
    Owner(Resource first, Resource second) { }
}
@Singleton class OwnerListener implements io.micronaut.context.event.BeanPreDestroyEventListener<Owner> {
    public Owner onPreDestroy(io.micronaut.context.event.BeanPreDestroyEvent<Owner> event) {
        Log.events.add("owner");
        if (${ownerFails}) { throw new AssertionError("owner failure"); }
        return event.getBean();
    }
}
@Singleton class ResourceListener implements io.micronaut.context.event.BeanPreDestroyEventListener<Resource> {
    public Resource onPreDestroy(io.micronaut.context.event.BeanPreDestroyEvent<Resource> event) {
        Log.events.add("resource");
        throw new ${failureType}("cleanup");
    }
}
""")
        def type = ctx.classLoader.loadClass('test.Owner')
        def log = ctx.classLoader.loadClass('test.Log')
        def registration = ctx.createBeanRegistration(ctx.getBeanDefinition(type))

        when:
        registration.close()

        then:
        def failure = thrown(Throwable)
        log.events == ['owner', 'resource', 'resource']
        if (ownerFails) {
            assert failure instanceof AssertionError
            assert failure.message == 'owner failure'
            assert failure.suppressed.length == 1
        }
        def cleanup = ownerFails ? failure.suppressed[0] : failure
        cleanup.message.contains('cleanup')
        cleanup.suppressed.length == 1
        cleanup.suppressed[0].message.contains('cleanup')

        when:
        registration.close()

        then:
        log.events == ['owner', 'resource', 'resource']

        cleanup:
        ctx.close()

        where:
        ownerFails | failureType
        false      | 'IllegalStateException'
        false      | 'AssertionError'
        true       | 'IllegalStateException'
        true       | 'AssertionError'
    }

    void "fresh registrations created through a group are released with that group"() {
        given:
        def ctx = buildContext(HEADER + """
@Singleton class Owner {
    Owner(Resource resource) { }
    @PreDestroy void stop() { Log.events.add("owner"); }
}
""")
        def type = ctx.classLoader.loadClass('test.Owner')
        def log = ctx.classLoader.loadClass('test.Log')
        def group = ctx.createDependencyGroup()
        def first = group.createBeanRegistration(ctx.getBeanDefinition(type))
        def second = group.createBeanRegistration(ctx.getBeanDefinition(type))

        when:
        group.destroy(first)
        group.close()
        second.close()

        then:
        log.events == ['owner', 'resource', 'owner', 'resource']

        cleanup:
        ctx.close()
    }

}
