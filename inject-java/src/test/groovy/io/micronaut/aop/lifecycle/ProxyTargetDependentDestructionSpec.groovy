package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * Destroying a proxy has to destroy each of the proxy's dependents once, after the target's {@code @PreDestroy}.
 *
 * <p>Destroying an around proxy means destroying the bean behind it, so the proxy hands its dependents over to the
 * target and the target's destruction disposes of them in the usual order: the bean first, then what was created
 * for it. Destroying them in the proxy as well runs each of them twice, and runs the first of the two before the
 * target's own pre-destroy callback.</p>
 */
class ProxyTargetDependentDestructionSpec extends AbstractTypeElementSpec {

    /**
     * A snapshot, because Spock renders a failed condition lazily and the list keeps being appended to by the
     * cleanup that closes the context.
     */
    private static List<String> recorded(Class<?> callsType) {
        new ArrayList<String>(callsType.RECORDED)
    }

    private static String source(String pkg, String aroundAttributes) {
        """
package $pkg;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

class Calls {
    static final List<String> RECORDED = new ArrayList<>();
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around($aroundAttributes)
@interface Traced {
}

@Prototype
@InterceptorBinding(value = Traced.class, kind = InterceptorKind.AROUND)
class TracedInterceptor implements Interceptor<Object, Object> {
    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        return context.proceed();
    }

    @PreDestroy
    public void close() {
        Calls.RECORDED.add("interceptor-destroyed");
    }
}

@Prototype
@Traced
class Advised {
    public String work() {
        return "done";
    }

    @PreDestroy
    public void close() {
        Calls.RECORDED.add("target-destroyed");
    }
}

@Singleton
class Holder {
    final Advised target;

    Holder(Advised target) {
        this.target = target;
    }
}
"""
    }

    void 'test the dependents of an eager proxy target proxy are destroyed once, after the target'() {
        given:
        ApplicationContext context = buildContext(source('proxytargetdependents.eager', 'proxyTarget = true'))
        Class<?> callsType = context.classLoader.loadClass('proxytargetdependents.eager.Calls')

        when:
        def holder = context.getBean(context.classLoader.loadClass('proxytargetdependents.eager.Holder'))
        holder.target.work()
        context.stop()

        then:
        recorded(callsType) == ['target-destroyed', 'interceptor-destroyed']

        cleanup:
        context.close()
    }

    void 'test the dependents of a lazy cacheable proxy target proxy are destroyed once, after the target'() {
        given:
        ApplicationContext context = buildContext(
                source('proxytargetdependents.lazy', 'proxyTarget = true, lazy = true, cacheableLazyTarget = true'))
        Class<?> callsType = context.classLoader.loadClass('proxytargetdependents.lazy.Calls')

        when:
        def holder = context.getBean(context.classLoader.loadClass('proxytargetdependents.lazy.Holder'))
        holder.target.work()
        context.stop()

        then:
        recorded(callsType) == ['target-destroyed', 'interceptor-destroyed']

        cleanup:
        context.close()
    }
}
