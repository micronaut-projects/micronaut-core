package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition

/**
 * Each {@code @PostConstruct} and {@code @PreDestroy} callback of a bean that intercepts its lifecycle is
 * intercepted separately, and the interceptor sees the callback as {@code getExecutableMethod()}: a reflection-free
 * executable method carrying the declaring type, name, arguments, annotation metadata and resolved parameter values
 * of the callback. A bean without callbacks of the intercepted kind is intercepted once as a phase, as before.
 *
 * The callbacks are compiled in as executable methods only for a bean that intercepts the phase, and they never
 * become executable methods of the bean, so processors and adapters do not observe them.
 */
class LifecycleCallbackMethodSpec extends AbstractTypeElementSpec {

    void 'test a post construct interceptor sees the callback as the intercepted method'() {
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
    static int intercepted;
    static ExecutableMethod<Object, ?> seen;
    static String targetMethodName;
    static Object proceeded;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        intercepted++;
        seen = ctx.getExecutableMethod();
        targetMethodName = ctx.getTargetMethod().getName();
        seen.invoke(ctx.getTarget());
        proceeded = ctx.proceed();
        return proceeded;
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

    @Executable String work2() { return "done"; }
    @Executable String work3() { return "done"; }
    @Executable String work4() { return "done"; }
    @Executable String work5() { return "done"; }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('callbacks.postconstruct.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('callbacks.postconstruct.MyBean')

        when:
        def bean = context.getBean(beanType)
        BeanDefinition<?> definition = getBeanDefinition(context, beanType.name)

        then: 'the callback is the intercepted method'
        interceptorType.intercepted == 1
        interceptorType.seen.methodName == 'init'
        interceptorType.seen.declaringType == beanType
        interceptorType.seen.hasAnnotation('jakarta.annotation.PostConstruct')
        interceptorType.seen.arguments.length == 0
        interceptorType.targetMethodName == 'init'

        and: 'proceed() returns the bean'
        interceptorType.proceeded.is(bean)

        and: 'invoking the intercepted method and proceeding both run the callback'
        bean.inits == 2

        and: 'the callback is not an executable method of the bean'
        definition.executableMethods*.methodName == ['work', 'work2', 'work3', 'work4', 'work5']
        definition.findMethod('init').empty
        definition.findPossibleMethods('init').findAny().empty
        definition.postConstructExecutableMethods*.methodName == ['init']
        definition.preDestroyExecutableMethods.empty
        definition.postConstructMethods*.name == ['init']

        cleanup:
        context.close()
    }

    void 'test a pre destroy interceptor sees the callback as the intercepted method'() {
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
    static int intercepted;
    static ExecutableMethod<Object, ?> seen;
    static InterceptorKind kind;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        intercepted++;
        kind = ctx.getKind();
        seen = ctx.getExecutableMethod();
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
        interceptorType.intercepted == 0
        definition.executableMethods.empty
        definition.findMethod('close').empty
        definition.findPossibleMethods('close').findAny().empty
        definition.postConstructExecutableMethods.empty
        definition.preDestroyExecutableMethods*.methodName == ['close']

        when:
        context.stop()

        then:
        interceptorType.intercepted == 1
        interceptorType.kind == io.micronaut.aop.InterceptorKind.PRE_DESTROY
        interceptorType.seen.methodName == 'close'
        interceptorType.seen.declaringType == beanType
        interceptorType.seen.hasAnnotation('jakarta.annotation.PreDestroy')
        bean.destroys == 2

        cleanup:
        context.close()
    }

    void 'test callbacks of a superclass and a subclass are intercepted separately in invocation order'() {
        given:
        ApplicationContext context = buildContext('''
package callbacks.hierarchy;

import io.micronaut.aop.*;
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
    static final List<String> names = new ArrayList<>();
    static final List<Class<?>> declaringTypes = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        names.add(ctx.getExecutableMethod().getMethodName());
        declaringTypes.add(ctx.getExecutableMethod().getDeclaringType());
        Events.LOG.add("intercept " + ctx.getExecutableMethod().getMethodName());
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

        then: 'the superclass callback is intercepted and invoked first'
        events.LOG == ['intercept baseInit', 'baseInit', 'intercept subInit', 'subInit']
        interceptorType.names == ['baseInit', 'subInit']
        interceptorType.declaringTypes == [baseType, subType]

        cleanup:
        context.close()
    }

    void 'test a bean without a callback is intercepted once as a phase'() {
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
    static String methodName;
    static Object proceeded;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        intercepted++;
        methodName = ctx.getExecutableMethod().getMethodName();
        proceeded = ctx.proceed();
        return proceeded;
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
        interceptorType.methodName == 'initialize'
        interceptorType.proceeded.is(bean)
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

    void 'test the resolved arguments of a callback are the parameter values of the interception'() {
        given:
        ApplicationContext context = buildContext('''
package callbacks.arguments;

import io.micronaut.aop.*;
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
class Dependency {
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.POST_CONSTRUCT)
class TrackingInterceptor implements MethodInterceptor<Object, Object> {
    static Object[] parameterValues;
    static Map<String, Object> parameterValueMap;
    static String[] argumentNames;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        parameterValues = ctx.getParameterValues();
        parameterValueMap = new LinkedHashMap<>(ctx.getParameterValueMap());
        argumentNames = Arrays.stream(ctx.getArguments()).map(a -> a.getName()).toArray(String[]::new);
        return ctx.proceed();
    }
}

@Singleton
@Tracked
class MyBean {
    Dependency received;

    @PostConstruct
    void init(Dependency dependency) {
        received = dependency;
    }
}
''')
        Class<?> interceptorType = context.classLoader.loadClass('callbacks.arguments.TrackingInterceptor')
        Class<?> beanType = context.classLoader.loadClass('callbacks.arguments.MyBean')
        Class<?> dependencyType = context.classLoader.loadClass('callbacks.arguments.Dependency')

        when:
        def bean = context.getBean(beanType)
        def dependency = context.getBean(dependencyType)

        then: 'the callback received the injected argument'
        bean.received.is(dependency)

        and: 'the interceptor saw the same value as the parameter of the callback'
        interceptorType.argumentNames == ['dependency'] as String[]
        interceptorType.parameterValues.length == 1
        interceptorType.parameterValues[0].is(dependency)
        interceptorType.parameterValueMap == [dependency: dependency]

        cleanup:
        context.close()
    }

    void 'test package private and private callbacks are intercepted and invokable'() {
        given:
        ApplicationContext context = buildContext('''
package callbacks.visibility;

import io.micronaut.aop.*;
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
    static final List<String> names = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        names.add(ctx.getExecutableMethod().getMethodName());
        ctx.getExecutableMethod().invoke(ctx.getTarget());
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

        then: 'both callbacks are intercepted and each ran once via invoke and once via proceed'
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
    static final List<Class<?>> declaringTypes = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        declaringTypes.add(ctx.getExecutableMethod().getDeclaringType());
        ctx.getExecutableMethod().invoke(ctx.getTarget());
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
        bean.invoked == ['Base.init', 'Base.init', 'Sub.init', 'Sub.init']

        cleanup:
        context.close()
    }

    void 'test package private callbacks with the same signature in different packages are distinct'() {
        given:
        JavaFiles files = new JavaFiles()
            .add('callbacks.packagebase.Base', '''
package callbacks.packagebase;

import jakarta.annotation.PostConstruct;
import java.util.*;

public class Base {
    public final List<String> invoked = new ArrayList<>();

    @PostConstruct
    void init() {
        invoked.add("Base.init");
    }
}
''')
            .add('callbacks.packagechild.Sub', '''
package callbacks.packagechild;

import callbacks.packagebase.Base;
import io.micronaut.aop.*;
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
    static final List<Class<?>> declaringTypes = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        declaringTypes.add(ctx.getExecutableMethod().getDeclaringType());
        ctx.getExecutableMethod().invoke(ctx.getTarget());
        return ctx.proceed();
    }
}

@Singleton
@Tracked
public class Sub extends Base {
    @PostConstruct
    void init() {
        invoked.add("Sub.init");
    }
}
''')
        ApplicationContext context = buildContext(files)
        Class<?> interceptorType = context.classLoader.loadClass('callbacks.packagechild.TrackingInterceptor')
        Class<?> baseType = context.classLoader.loadClass('callbacks.packagebase.Base')
        Class<?> subType = context.classLoader.loadClass('callbacks.packagechild.Sub')

        when:
        def bean = context.getBean(subType)

        then:
        interceptorType.declaringTypes == [baseType, subType]
        bean.invoked == ['Base.init', 'Base.init', 'Sub.init', 'Sub.init']

        cleanup:
        context.close()
    }

    void 'test the callback of a proxied bean is the intercepted method of lifecycle advice only'() {
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
    static final Map<String, List<String>> METHODS = new LinkedHashMap<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        METHODS.computeIfAbsent(ctx.getKind().name(), k -> new ArrayList<>()).add(ctx.getExecutableMethod().getMethodName());
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
        interceptorType.METHODS == [POST_CONSTRUCT: ['init'], AROUND: ['work']]
        definition.executableMethods*.methodName == ['work']
        definition.postConstructExecutableMethods*.methodName == ['init']

        cleanup:
        context.close()
    }
}
