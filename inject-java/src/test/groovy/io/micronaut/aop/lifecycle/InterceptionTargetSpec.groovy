package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers

/**
 * A non-singleton interceptor injects {@link io.micronaut.context.InterceptionTarget} to learn the definition of the
 * bean it was created for, including its qualifier, so that it can tell apart two targets of one class.
 */
class InterceptionTargetSpec extends AbstractTypeElementSpec {

    private static final Map<String, Object> PROPERTIES = ['targets.one.value': '1', 'targets.two.value': '2']

    private static String source(String around, String kinds) {
        """
package intercepted;

import io.micronaut.aop.*;
import io.micronaut.context.InterceptionTarget;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
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
    // the name of the target -> the definition the interceptor that intercepted it was created for
    static final Map<String, BeanDefinition<?>> DEFINITIONS = Collections.synchronizedMap(new LinkedHashMap<>());
}

@Prototype
${kinds.replace('@InterceptorBinding(', '@InterceptorBinding(value = Probed.class, ')}
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
    String value;

    TargetConfig(@Parameter String name) {
        this.name = name;
    }

    public String getValue() { return value; }

    public void setValue(String value) { this.value = value; }
}

@EachBean(TargetConfig.class)
$around
@Probed
class MyBean {
    final String name;

    MyBean(TargetConfig config) {
        this.name = config.name;
    }

    @PostConstruct void init() {}

    String work() { return name; }
}
"""
    }

    void 'test a per target interceptor learns the definition of each of two targets of one class'() {
        given:
        ApplicationContext context = buildContext('intercepted.MyBean', source(around, kinds), true, PROPERTIES)
        Class<?> beanType = context.classLoader.loadClass('intercepted.MyBean')
        Map<String, BeanDefinition<?>> seen = context.classLoader.loadClass('intercepted.Seen').DEFINITIONS

        when:
        def one = context.getBean(beanType, Qualifiers.byName('one'))
        def two = context.getBean(beanType, Qualifiers.byName('two'))

        then:
        one.work() == 'one'
        two.work() == 'two'
        seen.keySet() == ['one', 'two'] as Set
        seen.every { name, definition -> definition.beanType == beanType }
        seen['one'].declaredQualifier == Qualifiers.byName('one')
        seen['two'].declaredQualifier == Qualifiers.byName('two')

        cleanup:
        context.close()

        where:
        around                                                                        | kinds
        '@Around(proxyTarget = true, lazyInterceptorsPerTarget = true)'               | '@InterceptorBinding(kind = InterceptorKind.AROUND)'
        '@Around(proxyTarget = true, lazy = true, lazyInterceptorsPerTarget = true)'  | '@InterceptorBinding(kind = InterceptorKind.AROUND)'
        '@Around(proxyTarget = true, lazyInterceptorsPerTarget = true)'               | '@InterceptorBinding(kind = InterceptorKind.AROUND)\n@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)'
        '@Around(proxyTarget = true, lazy = true, lazyInterceptorsPerTarget = true)'  | '@InterceptorBinding(kind = InterceptorKind.AROUND)\n@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)'
    }

    void 'test without lazyInterceptorsPerTarget the interceptors injected into a proxy have no target'() {
        given:
        ApplicationContext context = buildContext('intercepted.MyBean',
            source('@Around(proxyTarget = true)', '@InterceptorBinding(kind = InterceptorKind.AROUND)'),
            true, PROPERTIES)

        when:
        context.getBean(context.classLoader.loadClass('intercepted.MyBean'), Qualifiers.byName('one'))

        then:
        def e = thrown(DependencyInjectionException)
        e.message.contains('io.micronaut.context.InterceptionTarget')

        cleanup:
        context.close()
    }

    void 'test an interceptor obtained by a lookup has no intercepted bean'() {
        given:
        ApplicationContext context = buildContext('intercepted.MyBean',
            source('@Around(proxyTarget = true, lazyInterceptorsPerTarget = true)', '@InterceptorBinding(kind = InterceptorKind.AROUND)'),
            true, PROPERTIES)

        when:
        context.getBean(context.classLoader.loadClass('intercepted.ProbingInterceptor'))

        then:
        def e = thrown(DependencyInjectionException)
        e.cause instanceof NoSuchBeanException
        e.cause.message.contains('io.micronaut.context.InterceptionTarget')

        cleanup:
        context.close()
    }

    void 'test a nullable injection point receives null outside an interception'() {
        given:
        ApplicationContext context = buildContext('nullable.Probe', '''
package nullable;

import io.micronaut.context.InterceptionTarget;
import io.micronaut.context.annotation.Prototype;
import org.jspecify.annotations.Nullable;

@Prototype
class Probe {
    final InterceptionTarget target;

    Probe(@Nullable InterceptionTarget target) {
        this.target = target;
    }
}
''')

        expect:
        context.getBean(context.classLoader.loadClass('nullable.Probe')).target == null

        cleanup:
        context.close()
    }
}
