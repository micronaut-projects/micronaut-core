package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.proxy.InterceptedBean
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers

/**
 * A bean created with {@code createBean}, with arguments the caller supplies, tells the interceptors created for it
 * which bean they are created for, as a bean the context resolves does, see {@link io.micronaut.context.InterceptionTarget}.
 */
class CreateBeanInterceptionTargetSpec extends AbstractTypeElementSpec {

    private static String source(String around, String kinds) {
        """
package created;

import io.micronaut.aop.*;
import io.micronaut.context.InterceptionTarget;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.BeanDefinition;
import jakarta.annotation.PostConstruct;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
$kinds
@interface Probed {
}

class Seen {
    // every interceptor instance created -> the definition it was created for
    static final Map<Object, BeanDefinition<?>> CREATED = Collections.synchronizedMap(new IdentityHashMap<>());
    // the name of the target -> the interceptor instance that intercepted it
    static final Map<String, Object> INTERCEPTED = Collections.synchronizedMap(new LinkedHashMap<>());
}

@Prototype
${kinds.replace('@InterceptorBinding(', '@InterceptorBinding(value = Probed.class, ')}
class ProbingInterceptor implements MethodInterceptor<Object, Object> {

    ProbingInterceptor(InterceptionTarget target) {
        Seen.CREATED.put(this, target.definition());
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Seen.INTERCEPTED.put(((MyBean) context.getTarget()).name, this);
        return context.proceed();
    }
}

@Prototype
$around
@Probed
class MyBean {
    final String name;

    MyBean(@Parameter String name) {
        this.name = name;
    }

    @PostConstruct void init() {
        work();
    }

    String work() { return name; }
}
"""
    }

    void 'test an interceptor of a bean created with arguments learns the bean it is created for'() {
        given:
        ApplicationContext context = buildContext('created.MyBean', source(around, kinds), true)
        Class<?> beanType = context.classLoader.loadClass('created.MyBean')
        Map<Object, BeanDefinition<?>> created = context.classLoader.loadClass('created.Seen').CREATED
        Map<String, Object> intercepted = context.classLoader.loadClass('created.Seen').INTERCEPTED
        BeanDefinition<?> definition = context.getBeanDefinition(beanType)

        when:
        def one = byArray ? context.createBean(beanType, 'one') : context.createBean(beanType, [name: 'one'])
        def two = byArray ? context.createBean(beanType, 'two') : context.createBean(beanType, [name: 'two'])

        then:
        one.work() == 'one'
        two.work() == 'two'
        intercepted.keySet() == ['one', 'two'] as Set
        created.size() == 2
        created.keySet() == intercepted.values() as Set
        created.values().every { it == definition }
        created[intercepted['one']] == definition
        created[intercepted['two']] == definition
        intercepted['one'] !== intercepted['two']

        when: 'the interceptor the created subclass proxy holds is the one that learned the bean'
        List<Object> retained = proxied ? (one as InterceptedBean).$interceptorRegistrations()*.bean : [intercepted['one']]

        then:
        retained == [intercepted['one']]

        cleanup:
        context.close()

        where:
        around    | kinds                                                                                                           | byArray | proxied
        '@Around' | '@InterceptorBinding(kind = InterceptorKind.AROUND)'                                                            | true    | true
        '@Around' | '@InterceptorBinding(kind = InterceptorKind.AROUND)'                                                            | false   | true
        '@Around' | '@InterceptorBinding(kind = InterceptorKind.AROUND)\n@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)' | true    | true
        ''        | '@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)'                                                    | true    | false
    }

    void 'test an interceptor of an each bean created with arguments learns the qualified bean it is created for'() {
        given:
        ApplicationContext context = buildContext('each.MyBean', '''
package each;

import io.micronaut.aop.*;
import io.micronaut.context.InterceptionTarget;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.BeanDefinition;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND)
@interface Probed {
}

class Seen {
    static final Map<String, BeanDefinition<?>> DEFINITIONS = Collections.synchronizedMap(new LinkedHashMap<>());
}

@Prototype
@InterceptorBinding(value = Probed.class, kind = InterceptorKind.AROUND)
class ProbingInterceptor implements MethodInterceptor<Object, Object> {
    final BeanDefinition<?> definition;

    ProbingInterceptor(InterceptionTarget target) {
        this.definition = target.definition();
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Seen.DEFINITIONS.put(((MyBean) context.getTarget()).name, definition);
        return context.proceed();
    }
}

@EachProperty("targets")
class TargetConfig {
    final String name;

    TargetConfig(@Parameter String name) {
        this.name = name;
    }
}

@EachBean(TargetConfig.class)
@Around
@Probed
class MyBean {
    final String name;

    MyBean(TargetConfig config) {
        this.name = config.name;
    }

    String work() { return name; }
}
''', true, ['targets.one.value': '1', 'targets.two.value': '2'])
        Class<?> beanType = context.classLoader.loadClass('each.MyBean')
        Map<String, BeanDefinition<?>> seen = context.classLoader.loadClass('each.Seen').DEFINITIONS

        when:
        def one = context.createBean(beanType, Qualifiers.byName('one'))
        def two = context.createBean(beanType, Qualifiers.byName('two'))

        then:
        one.work() == 'one'
        two.work() == 'two'
        seen.keySet() == ['one', 'two'] as Set
        seen.every { name, definition -> beanType.isAssignableFrom(definition.beanType) }
        seen['one'].declaredQualifier == Qualifiers.byName('one')
        seen['two'].declaredQualifier == Qualifiers.byName('two')
        seen['one'] == context.getBeanDefinition(beanType, Qualifiers.byName('one'))
        seen['two'] == context.getBeanDefinition(beanType, Qualifiers.byName('two'))

        cleanup:
        context.close()
    }
}
