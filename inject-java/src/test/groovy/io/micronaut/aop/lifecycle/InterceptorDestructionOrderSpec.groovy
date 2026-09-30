package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * The order in which a singleton interceptor and the beans it advises are destroyed.
 *
 * <p>The interceptor lives in a package that sorts before the one holding the advised bean, so that the name
 * tie-break the destruction order falls back to would destroy the interceptor first.</p>
 */
class InterceptorDestructionOrderSpec extends AbstractTypeElementSpec {

    private static final String LOG = '''
package shutdown.log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Log {
    public static final List<String> EVENTS = new CopyOnWriteArrayList<>();
    public static void add(String event) { EVENTS.add(event); }
}
'''

    private static final String TRACKED = '''
package shutdown.early;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
public @interface Tracked {
}
'''

    private static final String INTERCEPTOR = '''
package shutdown.early;

import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import shutdown.log.Log;

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
public class AGuardingInterceptor implements MethodInterceptor<Object, Object> {

    private boolean closed;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.getKind() == InterceptorKind.AROUND) {
            return context.proceed();
        }
        Log.add(closed ? "intercepted a destruction after being closed" : "intercepted a destruction");
        return context.proceed();
    }

    @PreDestroy
    void close() {
        closed = true;
        Log.add("interceptor closed");
    }
}
'''

    void "a singleton interceptor is destroyed after the bean it advises"() {
        given: "a bean bound to the interceptor by an interceptor binding, holding no reference to it"
        ApplicationContext context = buildContext(new AbstractTypeElementSpec.JavaFiles()
                .add('shutdown.log.Log', LOG)
                .add('shutdown.early.Tracked', TRACKED)
                .add('shutdown.early.AGuardingInterceptor', INTERCEPTOR)
                .add('shutdown.late.ZGuardedService', '''
package shutdown.late;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import shutdown.early.Tracked;
import shutdown.log.Log;

@Singleton
@Tracked
public class ZGuardedService {

    public void work() {
    }

    @PreDestroy
    public void close() {
        Log.add("service destroyed");
    }
}
'''))
        def log = context.classLoader.loadClass('shutdown.log.Log')
        context.getBean(context.classLoader.loadClass('shutdown.late.ZGuardedService')).work()

        when:
        context.close()

        then: "the interception runs against a live interceptor, which is closed after the bean it advises"
        log.EVENTS == ['intercepted a destruction', 'service destroyed', 'interceptor closed']
    }

    void "an injected interceptor is destroyed in the same order"() {
        given: "the same bean, injecting the interceptor it is advised by"
        ApplicationContext context = buildContext(new AbstractTypeElementSpec.JavaFiles()
                .add('shutdown.log.Log', LOG)
                .add('shutdown.early.Tracked', TRACKED)
                .add('shutdown.early.AGuardingInterceptor', INTERCEPTOR)
                .add('shutdown.late.ZGuardedService', '''
package shutdown.late;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import shutdown.early.AGuardingInterceptor;
import shutdown.early.Tracked;
import shutdown.log.Log;

@Singleton
@Tracked
public class ZGuardedService {

    @Inject
    AGuardingInterceptor interceptor;

    public void work() {
    }

    @PreDestroy
    public void close() {
        Log.add("service destroyed");
    }
}
'''))
        def log = context.classLoader.loadClass('shutdown.log.Log')
        context.getBean(context.classLoader.loadClass('shutdown.late.ZGuardedService')).work()

        when:
        context.close()

        then: "the declared dependency already gave that order, and the interceptor edge does not change it"
        log.EVENTS == ['intercepted a destruction', 'service destroyed', 'interceptor closed']
    }
}
