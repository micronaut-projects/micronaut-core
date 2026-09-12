package io.micronaut.aop.constructor

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

/**
 * Constructor interception wraps only the constructor call: member injection and {@code @PostConstruct} of the
 * target run on the instance the interceptor chain returned, after every construction interceptor has completed.
 */
class AroundConstructInjectionOrderSpec extends AbstractTypeElementSpec {

    private static final List<String> EXPECTED = [
            'A before proceed',
            'B before proceed',
            'target constructor',
            'B after proceed',
            'A after proceed',
            'target setter injection, field injected: true',
            'target postConstruct'
    ]

    @Unroll
    void 'test injection and post-construct run after the construction interceptors of #description'() {
        given:
        ApplicationContext context = buildContext(source(pkg, binding, bean))

        when:
        def target = getBean(context, pkg + '.Service')

        then:
        eventsOfTarget(context, pkg) == EXPECTED
        (target instanceof Intercepted) == proxied
        target.field != null

        cleanup:
        context.close()

        where:
        description                     | pkg               | binding                         | proxied | bean
        'a bean without a proxy'        | 'ctororder.plain' | ''                              | false   | '''
@Prototype
@Staged
class Service {
    @Inject Dependency field;

    Service(Dependency constructorDependency) {
        Events.LOG.add("target constructor");
    }

    @Inject
    void setDependency(Dependency dependency) {
        Events.LOG.add("target setter injection, field injected: " + (field != null));
    }

    @PostConstruct
    void init() {
        Events.LOG.add("target postConstruct");
    }
}
'''
        'private members'               | 'ctororder.privatemembers' | ''                     | false   | '''
@Prototype
@Staged
class Service {
    @Inject private Dependency field;

    @Inject
    Service() {
        Events.LOG.add("target constructor");
    }

    @Inject
    private void setDependency(Dependency dependency) {
        Events.LOG.add("target setter injection, field injected: " + (field != null));
    }

    @PostConstruct
    private void init() {
        Events.LOG.add("target postConstruct");
    }

    Dependency getField() {
        return field;
    }
}
'''
        'an around proxy'               | 'ctororder.proxy' | '@Around'                       | true    | '''
@Prototype
@Staged
class Service {
    @Inject Dependency field;

    Service(Dependency constructorDependency) {
        Events.LOG.add("target constructor");
    }

    @Inject
    void setDependency(Dependency dependency) {
        Events.LOG.add("target setter injection, field injected: " + (field != null));
    }

    @PostConstruct
    void init() {
        Events.LOG.add("target postConstruct");
    }

    public Dependency getField() {
        return field;
    }
}
'''
        'a proxy target'                | 'ctororder.proxytarget' | '@Around(proxyTarget = true)' | true | '''
@Prototype
@Staged
class Service {
    @Inject Dependency field;

    Service(Dependency constructorDependency) {
        Events.LOG.add("target constructor");
    }

    @Inject
    void setDependency(Dependency dependency) {
        Events.LOG.add("target setter injection, field injected: " + (field != null));
    }

    @PostConstruct
    void init() {
        Events.LOG.add("target postConstruct");
    }

    public Dependency getField() {
        return field;
    }
}
'''
        'an introduction'               | 'ctororder.introduction' | '@Introduction'          | true    | '''
@Prototype
@Staged
abstract class Service {
    @Inject Dependency field;

    Service(Dependency constructorDependency) {
        Events.LOG.add("target constructor");
    }

    @Inject
    void setDependency(Dependency dependency) {
        Events.LOG.add("target setter injection, field injected: " + (field != null));
    }

    @PostConstruct
    void init() {
        Events.LOG.add("target postConstruct");
    }

    public Dependency getField() {
        return field;
    }

    abstract String introduced();
}

@Singleton
@InterceptorBinding(Staged.class)
class Introducer implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return "introduced";
    }
}
'''
    }

    @Unroll
    void 'test injection and post-construct run after the construction interceptors of a parametrized bean #description'() {
        given:
        ApplicationContext context = buildContext(source(pkg, binding, '''
@Prototype
@Staged
class Service {
    @Inject Dependency field;
    final String name;

    Service(@Parameter String name) {
        this.name = name;
        Events.LOG.add("target constructor");
    }

    @Inject
    void setDependency(Dependency dependency) {
        Events.LOG.add("target setter injection, field injected: " + (field != null));
    }

    @PostConstruct
    void init() {
        Events.LOG.add("target postConstruct");
    }

    public Dependency getField() {
        return field;
    }

    public String getName() {
        return name;
    }
}
'''))

        when:
        def target = context.createBean(context.classLoader.loadClass(pkg + '.Service'), 'value')

        then:
        events(context, pkg) == EXPECTED
        (target instanceof Intercepted) == proxied
        target.field != null
        target.name == 'value'

        cleanup:
        context.close()

        where:
        description         | pkg                          | binding   | proxied
        'without a proxy'   | 'ctororder.parametrized'      | ''        | false
        'with an around proxy' | 'ctororder.parametrizedproxy' | '@Around' | true
    }

    void 'test an interceptor that throws after proceed prevents injection and post-construct'() {
        given:
        ApplicationContext context = buildContext(source('ctororder.rejected', '', '''
@Prototype
@Staged
class Service {
    @Inject Dependency field;

    Service() {
        Events.LOG.add("target constructor");
    }

    @Inject
    void setDependency(Dependency dependency) {
        Events.LOG.add("target setter injection, field injected: " + (field != null));
    }

    @PostConstruct
    void init() {
        Events.LOG.add("target postConstruct");
    }
}
''', '''
        if (id().equals("A")) {
            throw new IllegalStateException("rejected by A");
        }
'''))

        when:
        getBean(context, 'ctororder.rejected.Service')

        then:
        IllegalStateException e = thrown()
        e.message == 'rejected by A'
        events(context, 'ctororder.rejected') == [
                'A before proceed',
                'B before proceed',
                'target constructor',
                'B after proceed'
        ]

        cleanup:
        context.close()
    }

    void 'test only the instance the chain returns is injected and initialized'() {
        given:
        ApplicationContext context = buildContext(source('ctororder.twice', '', '''
@Prototype
@Staged
class Service {
    static int created;
    final int number = ++created;
    @Inject Dependency field;

    Service() {
        Events.LOG.add("target constructor " + number);
    }

    @Inject
    void setDependency(Dependency dependency) {
        Events.LOG.add("target setter injection " + number);
    }

    @PostConstruct
    void init() {
        Events.LOG.add("target postConstruct " + number);
    }
}
''', '''
        if (id().equals("B")) {
            context.proceed();
            bean = context.proceed();
        }
'''))

        when:
        def target = getBean(context, 'ctororder.twice.Service')

        then:
        target.number == 3
        target.field != null
        events(context, 'ctororder.twice') == [
                'A before proceed',
                'B before proceed',
                'target constructor 1',
                'target constructor 2',
                'target constructor 3',
                'B after proceed',
                'A after proceed',
                'target setter injection 3',
                'target postConstruct 3'
        ]

        cleanup:
        context.close()
    }

    void 'test post-construct interception runs after the construction chain and shares the interceptor'() {
        given:
        ApplicationContext context = buildContext('''
package ctororder.lifecycle;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

class Events {
    static final List<String> LOG = new ArrayList<>();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Managed {
}

@Singleton
class Dependency {
}

@Prototype
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.POST_CONSTRUCT)
class Tracking implements Interceptor<Object, Object> {
    final String id = "tracking@" + System.identityHashCode(this);

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        String kind = context instanceof ConstructorInvocationContext
            ? "AROUND_CONSTRUCT"
            : ((MethodInvocationContext<?, ?>) context).getKind().name();
        Events.LOG.add(kind + " before proceed " + id);
        Object result = context.proceed();
        Events.LOG.add(kind + " after proceed " + id);
        return result;
    }
}

@Prototype
@Managed
class Service {
    @Inject Dependency field;

    Service() {
        Events.LOG.add("target constructor");
    }

    @Inject
    void setDependency(Dependency dependency) {
        Events.LOG.add("target setter injection, field injected: " + (field != null));
    }

    @PostConstruct
    void init() {
        Events.LOG.add("target postConstruct");
    }
}
''')

        when:
        getBean(context, 'ctororder.lifecycle.Service')
        List<String> log = events(context, 'ctororder.lifecycle')
        String id = log[0].substring(log[0].indexOf('tracking@'))

        then:
        log == [
                'AROUND_CONSTRUCT before proceed ' + id,
                'target constructor',
                'AROUND_CONSTRUCT after proceed ' + id,
                'target setter injection, field injected: true',
                'POST_CONSTRUCT before proceed ' + id,
                'target postConstruct',
                'POST_CONSTRUCT after proceed ' + id
        ]

        cleanup:
        context.close()
    }

    void 'test construction interception of a factory produced bean completes'() {
        given:
        ApplicationContext context = buildContext(source('ctororder.factory', '', '''
@Factory
class ServiceFactory {
    @Prototype
    @Staged
    Service service(Dependency dependency) {
        Events.LOG.add("target constructor");
        return new Service();
    }
}

class Service {
}
'''))

        when:
        def target = getBean(context, 'ctororder.factory.Service')

        then:
        target != null
        events(context, 'ctororder.factory') == [
                'A before proceed',
                'B before proceed',
                'target constructor',
                'B after proceed',
                'A after proceed'
        ]

        cleanup:
        context.close()
    }


    void 'test an @InjectScope constructor argument is released when the constructor has run'() {
        given:
        ApplicationContext context = buildContext(source('ctororder.injectscope', '', '''
@Prototype
class Scoped {
    @PreDestroy
    void close() {
        Events.LOG.add("scoped destroyed");
    }
}

@Prototype
@Staged
class Service {
    @Inject Dependency field;

    Service(@InjectScope Scoped scoped) {
        Events.LOG.add("target constructor");
    }

    @Inject
    void setDependency(Dependency dependency) {
        Events.LOG.add("target setter injection, field injected: " + (field != null));
    }

    @PostConstruct
    void init() {
        Events.LOG.add("target postConstruct");
    }
}
'''))

        when:
        getBean(context, 'ctororder.injectscope.Service')

        then: 'the argument is released as soon as the constructor has run, before the chain returns'
        events(context, 'ctororder.injectscope') == [
                'A before proceed',
                'B before proceed',
                'target constructor',
                'scoped destroyed',
                'B after proceed',
                'A after proceed',
                'target setter injection, field injected: true',
                'target postConstruct'
        ]

        cleanup:
        context.close()
    }

    void 'test an @InjectScope constructor argument is released when an interceptor rejects the instance'() {
        given:
        ApplicationContext context = buildContext(source('ctororder.injectscoperejected', '', '''
@Prototype
class Scoped {
    @PreDestroy
    void close() {
        Events.LOG.add("scoped destroyed");
    }
}

@Prototype
@Staged
class Service {
    @Inject Dependency field;

    Service(@InjectScope Scoped scoped) {
        Events.LOG.add("target constructor");
    }

    @Inject
    void setDependency(Dependency dependency) {
        Events.LOG.add("target setter injection, field injected: " + (field != null));
    }

    @PostConstruct
    void init() {
        Events.LOG.add("target postConstruct");
    }
}
''', '''
        if (id().equals("A")) {
            throw new IllegalStateException("rejected by A");
        }
'''))

        when:
        getBean(context, 'ctororder.injectscoperejected.Service')

        then: 'the instance is never injected, but the argument is still released'
        IllegalStateException e = thrown()
        e.message == 'rejected by A'
        events(context, 'ctororder.injectscoperejected') == [
                'A before proceed',
                'B before proceed',
                'target constructor',
                'scoped destroyed',
                'B after proceed'
        ]

        cleanup:
        context.close()
    }

    void 'test a bean constructed inside another bean construction shares its interceptor with post-construct'() {
        given:
        ApplicationContext context = buildContext('''
package ctororder.nested;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

class Events {
    static final List<String> LOG = new ArrayList<>();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Managed {
}

@Prototype
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.AROUND_CONSTRUCT)
@InterceptorBinding(value = Managed.class, kind = InterceptorKind.POST_CONSTRUCT)
class Tracking implements Interceptor<Object, Object> {
    final String id = "tracking@" + System.identityHashCode(this);

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        String kind = context instanceof ConstructorInvocationContext
            ? "AROUND_CONSTRUCT"
            : ((MethodInvocationContext<?, ?>) context).getKind().name();
        Events.LOG.add(kind + " " + id);
        return context.proceed();
    }
}

@Prototype
@Managed
class Inner {
    @PostConstruct
    void init() {
        Events.LOG.add("inner postConstruct");
    }
}

@Singleton
class Outer {
    Outer(Inner inner) {
        Events.LOG.add("outer constructor");
    }
}
''')

        when: 'the inner bean is constructed while the outer bean is being constructed'
        getBean(context, 'ctororder.nested.Outer')
        List<String> log = events(context, 'ctororder.nested')

        then: 'both phases of the inner bean use the same interceptor instance'
        log[0].startsWith('AROUND_CONSTRUCT tracking@')
        log[1] == log[0].replace('AROUND_CONSTRUCT', 'POST_CONSTRUCT')
        log[2] == 'inner postConstruct'
        log[3] == 'outer constructor'
        log.size() == 4

        cleanup:
        context.close()
    }


    void 'test a proxied bean with members exposes only its own constructor arguments to the interceptor'() {
        given:
        ApplicationContext context = buildContext('''
package ctororder.proxyarguments;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.type.Argument;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Around
@AroundConstruct
@interface Tracked {
}

@Singleton
class Dependency {
}

@Prototype
@Tracked
class Service {
    @Inject Dependency field;

    Service(Dependency constructorDependency) {
    }

    @Inject
    void setDependency(Dependency dependency) {
    }

    @PostConstruct
    void init() {
    }

    public Dependency getField() {
        return field;
    }
}

@Singleton
@InterceptorBinding(value = Tracked.class, kind = InterceptorKind.AROUND_CONSTRUCT)
class Capturing implements ConstructorInterceptor<Object> {
    static List<String> argumentNames;
    static int parameterCount;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        argumentNames = Arrays.stream(context.getArguments()).map(Argument::getName).toList();
        parameterCount = context.getParameterValues().length;
        return context.proceed();
    }
}
''')

        when: 'the proxy definition runs the chain, which trims the internal proxy parameters'
        def target = getBean(context, 'ctororder.proxyarguments.Service')
        Class<?> capturing = context.classLoader.loadClass('ctororder.proxyarguments.Capturing')

        then: 'the interceptor sees the constructor of the intercepted type, not the proxy constructor'
        target instanceof Intercepted
        target.field != null
        capturing.argumentNames == ['constructorDependency']
        capturing.parameterCount == 1

        cleanup:
        context.close()
    }

    /**
     * The events of the intercepted construction. For a proxy target the proxy is created too: it calls the target's
     * constructor as its super constructor and has its own members injected, neither of which is intercepted.
     */
    private static List<String> eventsOfTarget(ApplicationContext context, String pkg) {
        List<String> log = events(context, pkg)
        if (pkg.endsWith('proxytarget')) {
            int start = log.indexOf('A before proceed')
            List<String> fromChain = log.subList(start, log.size())
            return fromChain.subList(0, fromChain.indexOf('target postConstruct') + 1)
        }
        return log
    }

    private static List<String> events(ApplicationContext context, String pkg) {
        return new ArrayList<>(context.classLoader.loadClass(pkg + '.Events').LOG as List<String>)
    }

    private static String source(String pkg, String binding, @Language("java") String bean, String afterProceed = '') {
        return """
package $pkg;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.order.Ordered;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

class Events {
    static final List<String> LOG = new ArrayList<>();
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
$binding
@AroundConstruct
@interface Staged {
}

@Singleton
class Dependency {
}

abstract class Recording implements ConstructorInterceptor<Object>, Ordered {
    abstract String id();

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        Events.LOG.add(id() + " before proceed");
        Object bean = context.proceed();
        $afterProceed
        Events.LOG.add(id() + " after proceed");
        return bean;
    }
}

@Singleton
@InterceptorBean(Staged.class)
class A extends Recording {
    String id() { return "A"; }
    public int getOrder() { return 1; }
}

@Singleton
@InterceptorBean(Staged.class)
class B extends Recording {
    String id() { return "B"; }
    public int getOrder() { return 2; }
}

$bean
"""
    }
}
