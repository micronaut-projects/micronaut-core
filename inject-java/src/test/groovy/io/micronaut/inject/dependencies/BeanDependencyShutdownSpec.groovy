package io.micronaut.inject.dependencies

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class BeanDependencyShutdownSpec extends AbstractTypeElementSpec {
    private static final String HEADER = '''
package test;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;
import io.micronaut.context.scope.CreatedBean;
import jakarta.inject.*;
import jakarta.annotation.*;
import java.util.*;
import java.util.concurrent.*;

class Log {
    static final List<String> EVENTS = new CopyOnWriteArrayList<>();
    static int next;
}
@Prototype class Resource {
    final int id = ++Log.next;
    @PreDestroy void close() { Log.EVENTS.add("resource" + id); }
}
@Singleton class Shared {
    @PreDestroy void close() { Log.EVENTS.add("shared"); }
}
'''

    void "a pre-destroy method resolves through its injected resolver during shutdown"() {
        given:
        def ctx = buildContext(HEADER + '''
@Singleton class Owner {
    final BeanDependencyResolver resolver;
    Owner(BeanDependencyResolver resolver) { this.resolver = resolver; }
    @PreDestroy void close() {
        resolver.getBean(Resource.class);
        resolver.createGroup().getBean(Resource.class);
        resolver.getBean(Shared.class);
        Log.EVENTS.add("owner");
    }
}
''')
        def owner = ctx.getBean(ctx.classLoader.loadClass('test.Owner'))
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.close()

        then: 'the dependents are destroyed with the owner, the singleton created during shutdown after it'
        log.EVENTS == ['owner', 'resource2', 'resource1', 'shared']

        when:
        owner.resolver.getBean(ctx.classLoader.loadClass('test.Resource'))

        then:
        thrown(IllegalStateException)
    }

    void "a shutdown event listener resolves through the context"() {
        given:
        def ctx = buildContext(HEADER + '''
@Singleton class Listener implements ApplicationEventListener<ShutdownEvent> {
    static BeanDependencyGroup retained;
    static CreatedBean<Resource> created;
    public void onApplicationEvent(ShutdownEvent event) {
        BeanContext context = event.getSource();
        context.withDependencies(group -> {
            group.getBean(Resource.class);
            Log.EVENTS.add("temporary");
            return null;
        });
        retained = context.createDependencyGroup();
        retained.getBean(Resource.class);
        created = context.createBeanRegistration(context.getBeanDefinition(Resource.class));
        Log.EVENTS.add("listener");
    }
}
''')
        def listener = ctx.classLoader.loadClass('test.Listener')
        def resource = ctx.classLoader.loadClass('test.Resource')
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.close()

        then: 'what the listener created and did not release is destroyed before the shutdown completes'
        log.EVENTS == ['temporary', 'resource1', 'listener', 'resource3', 'resource2']

        when:
        listener.created.close()
        listener.retained.close()

        then:
        log.EVENTS.size() == 5

        when:
        listener.retained.getBean(resource)

        then:
        thrown(IllegalStateException)
    }

    void "a group created before shutdown is used during it"() {
        given:
        def ctx = buildContext(HEADER + '''
class Holder { static BeanDependencyGroup group; }
@Singleton class Owner {
    @PreDestroy void close() {
        Holder.group.getBean(Resource.class);
        Log.EVENTS.add("owner");
    }
}
@Singleton class Listener implements ApplicationEventListener<ShutdownEvent> {
    public void onApplicationEvent(ShutdownEvent event) {
        Holder.group.getBean(Resource.class);
        Log.EVENTS.add("listener");
    }
}
''')
        def holder = ctx.classLoader.loadClass('test.Holder')
        def resource = ctx.classLoader.loadClass('test.Resource')
        def log = ctx.classLoader.loadClass('test.Log')
        holder.group = ctx.createDependencyGroup()
        holder.group.getBean(resource)
        ctx.getBean(ctx.classLoader.loadClass('test.Owner'))
        ctx.getBean(ctx.classLoader.loadClass('test.Listener'))

        when:
        ctx.close()

        then: 'what was created during shutdown is destroyed before it completes, what the group held before is kept'
        log.EVENTS == ['listener', 'owner', 'resource3', 'resource2']
        !holder.group.isClosed()

        when:
        holder.group.close()

        then:
        log.EVENTS == ['listener', 'owner', 'resource3', 'resource2', 'resource1']
    }

    void "new top-level work during shutdown is rejected"() {
        given:
        def ctx = buildContext(HEADER + '''
class Holder { static BeanDependencyGroup group; }
@Singleton class Owner {
    final BeanContext context;
    Owner(BeanContext context) { this.context = context; }
    @PreDestroy void close() {
        try {
            context.createDependencyGroup();
            Log.EVENTS.add("group created");
        } catch (IllegalStateException expected) { Log.EVENTS.add("group rejected"); }
        try {
            context.createBeanRegistration(context.getBeanDefinition(Resource.class));
            Log.EVENTS.add("registration created");
        } catch (IllegalStateException expected) { Log.EVENTS.add("registration rejected"); }
        try {
            CompletableFuture.runAsync(() -> Holder.group.getBean(Resource.class)).join();
            Log.EVENTS.add("other thread resolved");
        } catch (CompletionException expected) {
            if (expected.getCause() instanceof IllegalStateException) {
                Log.EVENTS.add("other thread rejected");
            }
        }
    }
}
''')
        def holder = ctx.classLoader.loadClass('test.Holder')
        def log = ctx.classLoader.loadClass('test.Log')
        holder.group = ctx.createDependencyGroup()
        ctx.getBean(ctx.classLoader.loadClass('test.Owner'))

        when:
        ctx.close()

        then:
        log.EVENTS == ['group rejected', 'registration rejected', 'other thread rejected']

        when:
        ctx.createDependencyGroup()

        then:
        thrown(IllegalStateException)

        when:
        holder.group.getBean(ctx.classLoader.loadClass('test.Resource'))

        then:
        thrown(IllegalStateException)
    }

    void "a per-target interceptor first needed during shutdown is destroyed with its target"() {
        given:
        def ctx = buildContext(HEADER + '''
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@io.micronaut.aop.InterceptorBinding(kind = io.micronaut.aop.InterceptorKind.AROUND)
@interface Traced {}
@Prototype @io.micronaut.aop.InterceptorBean(Traced.class)
class TracingInterceptor implements io.micronaut.aop.MethodInterceptor<Object, Object> {
    public Object intercept(io.micronaut.aop.MethodInvocationContext<Object, Object> invocation) {
        Log.EVENTS.add("intercepted");
        return invocation.proceed();
    }
    @PreDestroy void close() { Log.EVENTS.add("interceptor"); }
}
@Singleton @io.micronaut.aop.Around(proxyTarget = true, lazyInterceptorsPerTarget = true)
class ZTarget {
    @Traced public void run() { }
    @PreDestroy void close() { Log.EVENTS.add("target"); }
}
@Singleton class Owner {
    final ZTarget target;
    Owner(ZTarget target) { this.target = target; }
    @PreDestroy void close() {
        target.run();
        Log.EVENTS.add("owner");
    }
}
''')
        ctx.getBean(ctx.classLoader.loadClass('test.Owner'))
        def log = ctx.classLoader.loadClass('test.Log')

        when:
        ctx.close()

        then:
        log.EVENTS == ['intercepted', 'owner', 'target', 'interceptor']
    }

    void "a dependency group is rejected before the context is configured"() {
        given:
        def ctx = io.micronaut.context.ApplicationContext.builder().build()

        when:
        ctx.createDependencyGroup()

        then:
        thrown(IllegalStateException)
    }
}
