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

    void 'test a qualified per target interceptor of an @EachBean learns the definition of each of two targets - #parameter'() {
        given:
        ApplicationContext context = buildContext('intercepted.MyBean', eachBeanInterceptorSource(around, kinds, parameter),
            true, PROPERTIES + ['seeds.a.value': 'a', 'seeds.b.value': 'b'])
        Class<?> beanType = context.classLoader.loadClass('intercepted.MyBean')
        Class<?> seen = context.classLoader.loadClass('intercepted.Seen')
        Map<String, BeanDefinition<?>> definitions = seen.DEFINITIONS
        List<Boolean> dependencyTargets = seen.DEPENDENCY_TARGETS

        when:
        def one = context.getBean(beanType, Qualifiers.byName('one'))
        def two = context.getBean(beanType, Qualifiers.byName('two'))

        then:
        one.work() == 'one'
        two.work() == 'two'
        definitions.keySet() == ['a/one', 'b/one', 'a/two', 'b/two'] as Set
        definitions.values().every { definition -> definition != null && definition.beanType == beanType }
        definitions['a/one'].declaredQualifier == Qualifiers.byName('one')
        definitions['b/one'].declaredQualifier == Qualifiers.byName('one')
        definitions['a/two'].declaredQualifier == Qualifiers.byName('two')
        definitions['b/two'].declaredQualifier == Qualifiers.byName('two')

        and: 'the dependencies of the interceptors, qualified ones too, are not told the bean'
        dependencyTargets.size() == 8
        dependencyTargets.every { !it }

        cleanup:
        context.close()

        where:
        around                                                                        | kinds                                                        | parameter
        ''                                                                            | '@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)' | 'InterceptionTarget target'
        ''                                                                            | '@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)' | '@Nullable InterceptionTarget target'
        '@Around(proxyTarget = true, lazyInterceptorsPerTarget = true)'               | '@InterceptorBinding(kind = InterceptorKind.AROUND)'         | 'InterceptionTarget target'
        '@Around(proxyTarget = true, lazy = true, lazyInterceptorsPerTarget = true)'  | '@InterceptorBinding(kind = InterceptorKind.AROUND)'         | 'InterceptionTarget target'
    }

    private static String eachBeanInterceptorSource(String around, String kinds, String parameter) {
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
import org.jspecify.annotations.Nullable;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
$kinds
@interface Seeded {
}

class Seen {
    // the name of the seed of the interceptor / the name of the target -> the definition the interceptor was created for
    static final Map<String, BeanDefinition<?>> DEFINITIONS = Collections.synchronizedMap(new LinkedHashMap<>());
    // whether a dependency of an interceptor was told an intercepted bean, for each dependency created
    static final List<Boolean> DEPENDENCY_TARGETS = Collections.synchronizedList(new ArrayList<>());
}

@EachProperty("seeds")
class Seed {
    final String name;
    String value;

    Seed(@Parameter String name) {
        this.name = name;
    }

    public String getValue() { return value; }

    public void setValue(String value) { this.value = value; }
}

@Prototype
class Dependency {
    Dependency(@Nullable InterceptionTarget target) {
        Seen.DEPENDENCY_TARGETS.add(target != null);
    }
}

@EachBean(Seed.class)
@Prototype
class SeedDependency {
    SeedDependency(Seed seed, @Nullable InterceptionTarget target) {
        Seen.DEPENDENCY_TARGETS.add(target != null);
    }
}

@EachBean(Seed.class)
@Prototype
${kinds.replace('@InterceptorBinding(', '@InterceptorBinding(value = Seeded.class, ')}
class SeedInterceptor implements MethodInterceptor<Object, Object> {
    final Seed seed;
    final BeanDefinition<?> definition;

    SeedInterceptor(Seed seed, $parameter, Dependency dependency, @Parameter SeedDependency seedDependency) {
        this.seed = seed;
        this.definition = target == null ? null : target.definition();
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Seen.DEFINITIONS.put(seed.name + "/" + ((MyBean) context.getTarget()).name, definition);
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
@Seeded
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
}
