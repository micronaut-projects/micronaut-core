package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition

/**
 * A lifecycle interceptor sees the real {@code @PostConstruct} and {@code @PreDestroy} callbacks of the target
 * through {@link io.micronaut.aop.MethodInvocationContext#getLifecycleCallbacks()}.
 *
 * The callbacks are compiled in as reflection-free executable methods, but only for a bean that intercepts the
 * phase, and they never become executable methods of the bean, so processors and adapters do not observe them.
 * One chain still runs per phase: {@code proceed()} invokes every callback.
 */
class LifecycleCallbackMethodSpec extends AbstractTypeElementSpec {

    void 'test a post construct interceptor sees and can invoke the callback of the target'() {
        given:
        ApplicationContext context = buildContext('''
package callbacks.postconstruct;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Executable;
import io.micronaut.inject.ExecutableMethod;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static ExecutableMethod<Object, ?> seen;
    static int callbackCount;
    static String targetMethodName;
    static String phaseMethodName;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        List<ExecutableMethod<Object, ?>> callbacks = ctx.getLifecycleCallbacks();
        callbackCount = callbacks.size();
        seen = callbacks.get(0);
        seen.invoke(ctx.getTarget());
        targetMethodName = ctx.getTargetMethod().getName();
        phaseMethodName = ctx.getExecutableMethod().getMethodName();
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    int inits;

    @PostConstruct
    void init() {
        inits++;
    }

    @Executable
    String work() {
        return "done";
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('callbacks.postconstruct.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('callbacks.postconstruct.MyBean')

        when:
        def bean = context.getBean(beanType)
        BeanDefinition<?> definition = getBeanDefinition(context, beanType.name)

        then: 'the callback is exposed as an executable method of the target'
        interceptorType.callbackCount == 1
        interceptorType.seen.methodName == 'init'
        interceptorType.seen.declaringType == beanType
        interceptorType.seen.hasAnnotation('jakarta.annotation.PostConstruct')
        interceptorType.seen.arguments.length == 0

        and: 'the context still stands for the phase, but its target method is the callback'
        interceptorType.phaseMethodName == 'initialize'
        interceptorType.targetMethodName == 'init'

        and: 'invoking the callback and proceeding both run it'
        bean.inits == 2

        and: 'the callback is not an executable method of the bean'
        definition.executableMethods*.methodName == ['work']
        definition.postConstructExecutableMethods*.methodName == ['init']
        definition.preDestroyExecutableMethods.empty
        definition.postConstructMethods*.name == ['init']

        cleanup:
        context.close()
    }

    void 'test a pre destroy interceptor sees and can invoke the callback of the target'() {
        given:
        ApplicationContext context = buildContext('''
package callbacks.predestroy;

import io.micronaut.aop.*;
import io.micronaut.inject.ExecutableMethod;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.PRE_DESTROY)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static ExecutableMethod<Object, ?> seen;
    static InterceptorKind kind;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        kind = ctx.getKind();
        seen = ctx.getLifecycleCallbacks().get(0);
        seen.invoke(ctx.getTarget());
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    int destroys;

    @PreDestroy
    void close() {
        destroys++;
    }

    String work() {
        return "done";
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('callbacks.predestroy.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('callbacks.predestroy.MyBean')

        when:
        def bean = context.getBean(beanType)
        BeanDefinition<?> definition = getBeanDefinition(context, beanType.name)

        then:
        bean.destroys == 0
        definition.executableMethods.empty
        definition.postConstructExecutableMethods.empty
        definition.preDestroyExecutableMethods*.methodName == ['close']

        when:
        context.stop()

        then:
        interceptorType.kind == io.micronaut.aop.InterceptorKind.PRE_DESTROY
        interceptorType.seen.methodName == 'close'
        interceptorType.seen.declaringType == beanType
        interceptorType.seen.hasAnnotation('jakarta.annotation.PreDestroy')
        bean.destroys == 2

        cleanup:
        context.close()
    }

    void 'test callbacks of a superclass and a subclass are listed in invocation order'() {
        given:
        ApplicationContext context = buildContext('''
package callbacks.hierarchy;

import io.micronaut.aop.*;
import io.micronaut.inject.ExecutableMethod;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

class Events {
    static final List<String> LOG = new ArrayList<>();
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static List<String> names;
    static List<Class<?>> declaringTypes;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        List<ExecutableMethod<Object, ?>> callbacks = ctx.getLifecycleCallbacks();
        names = new ArrayList<>();
        declaringTypes = new ArrayList<>();
        for (ExecutableMethod<Object, ?> callback : callbacks) {
            names.add(callback.getMethodName());
            declaringTypes.add(callback.getDeclaringType());
        }
        Events.LOG.add("intercept");
        return ctx.proceed();
    }
}

class Base {
    @PostConstruct
    void baseInit() {
        Events.LOG.add("baseInit");
    }
}

@Singleton
@Tracked
class Sub extends Base {
    @PostConstruct
    void subInit() {
        Events.LOG.add("subInit");
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('callbacks.hierarchy.TrackingInterceptor')
        Class<?> baseType = context.classLoader.loadClass('callbacks.hierarchy.Base')
        Class<?> subType = context.classLoader.loadClass('callbacks.hierarchy.Sub')
        Class<?> events = context.classLoader.loadClass('callbacks.hierarchy.Events')

        when:
        context.getBean(subType)

        then: 'the superclass callback comes first, which is the order proceed() invokes them in'
        events.LOG == ['intercept', 'baseInit', 'subInit']
        interceptorType.names == ['baseInit', 'subInit']
        interceptorType.declaringTypes == [baseType, subType]

        cleanup:
        context.close()
    }

    void 'test a bean without a callback yields an empty list and proceed still works'() {
        given:
        ApplicationContext context = buildContext('''
package callbacks.none;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static int intercepted;
    static int callbackCount = -1;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        intercepted++;
        callbackCount = ctx.getLifecycleCallbacks().size();
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    String work() {
        return "done";
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('callbacks.none.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('callbacks.none.MyBean')

        when:
        def bean = context.getBean(beanType)
        BeanDefinition<?> definition = getBeanDefinition(context, beanType.name)

        then:
        bean.work() == 'done'
        interceptorType.intercepted == 1
        interceptorType.callbackCount == 0
        definition.executableMethods.empty
        definition.postConstructExecutableMethods.empty

        cleanup:
        context.close()
    }

    void 'test a bean that does not intercept its lifecycle compiles no callback executable method'() {
        given:
        ApplicationContext context = buildContext('''
package callbacks.unbound;

import io.micronaut.context.annotation.Executable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;

@Singleton
class MyBean {
    int inits;

    @PostConstruct
    void init() {
        inits++;
    }

    @PreDestroy
    void close() {
    }

    @Executable
    String work() {
        return "done";
    }
}
''')
        Class<?> beanType = context.classLoader.loadClass('callbacks.unbound.MyBean')

        when:
        def bean = context.getBean(beanType)
        BeanDefinition<?> definition = getBeanDefinition(context, beanType.name)

        then:
        bean.inits == 1
        definition.executableMethods*.methodName == ['work']
        definition.postConstructExecutableMethods.empty
        definition.preDestroyExecutableMethods.empty
        definition.postConstructMethods*.name == ['init']
        definition.preDestroyMethods*.name == ['close']

        cleanup:
        context.close()
    }

    void 'test package private and private callbacks are exposed and invokable'() {
        given:
        ApplicationContext context = buildContext('''
package callbacks.visibility;

import io.micronaut.aop.*;
import io.micronaut.inject.ExecutableMethod;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static List<String> names;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        names = new ArrayList<>();
        for (ExecutableMethod<Object, ?> callback : ctx.getLifecycleCallbacks()) {
            names.add(callback.getMethodName());
            callback.invoke(ctx.getTarget());
        }
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    final List<String> invoked = new ArrayList<>();

    @PostConstruct
    void packagePrivateInit() {
        invoked.add("packagePrivateInit");
    }

    @PostConstruct
    private void privateInit() {
        invoked.add("privateInit");
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('callbacks.visibility.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('callbacks.visibility.MyBean')

        when:
        def bean = context.getBean(beanType)

        then: 'both callbacks are listed and each ran once via invoke and once via proceed'
        interceptorType.names.toSet() == ['packagePrivateInit', 'privateInit'].toSet()
        bean.invoked.count('packagePrivateInit') == 2
        bean.invoked.count('privateInit') == 2
        bean.invoked.size() == 4

        cleanup:
        context.close()
    }

    void 'test private callbacks with the same signature in a superclass and a subclass are distinct'() {
        given:
        ApplicationContext context = buildContext('''
package callbacks.sameprivate;

import io.micronaut.aop.*;
import io.micronaut.inject.ExecutableMethod;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static List<Class<?>> declaringTypes;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        declaringTypes = new ArrayList<>();
        for (ExecutableMethod<Object, ?> callback : ctx.getLifecycleCallbacks()) {
            declaringTypes.add(callback.getDeclaringType());
            callback.invoke(ctx.getTarget());
        }
        return ctx.proceed();
    }
}

class Base {
    final List<String> invoked = new ArrayList<>();

    @PostConstruct
    private void init() {
        invoked.add("Base.init");
    }
}

@Singleton
@Tracked
class Sub extends Base {
    @PostConstruct
    private void init() {
        invoked.add("Sub.init");
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('callbacks.sameprivate.TrackingInterceptor')
        Class<?> baseType = context.classLoader.loadClass('callbacks.sameprivate.Base')
        Class<?> subType = context.classLoader.loadClass('callbacks.sameprivate.Sub')

        when:
        def bean = context.getBean(subType)

        then:
        interceptorType.declaringTypes == [baseType, subType]
        bean.invoked == ['Base.init', 'Sub.init', 'Base.init', 'Sub.init']

        cleanup:
        context.close()
    }

    void 'test the callbacks of a proxied bean are exposed to lifecycle advice only'() {
        given:
        ApplicationContext context = buildContext('''
package callbacks.proxied;

import io.micronaut.aop.*;
import io.micronaut.inject.ExecutableMethod;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Tracked {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND)
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static final Map<String, List<String>> CALLBACKS = new LinkedHashMap<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        List<String> names = new ArrayList<>();
        for (ExecutableMethod<Object, ?> callback : ctx.getLifecycleCallbacks()) {
            names.add(callback.getMethodName());
        }
        CALLBACKS.put(ctx.getKind().name(), names);
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    int inits;

    @PostConstruct
    void init() {
        inits++;
    }

    public String work() {
        return "done";
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('callbacks.proxied.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('callbacks.proxied.MyBean')

        when:
        def bean = context.getBean(beanType)
        bean.work()
        BeanDefinition<?> definition = getBeanDefinition(context, beanType.name)

        then:
        bean instanceof io.micronaut.aop.Intercepted
        bean.inits == 1
        interceptorType.CALLBACKS == [POST_CONSTRUCT: ['init'], AROUND: []]
        definition.executableMethods*.methodName == ['work']
        definition.postConstructExecutableMethods*.methodName == ['init']

        cleanup:
        context.close()
    }
}
