package io.micronaut.graal.reflect

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationValueProvider
import io.micronaut.core.graal.GraalReflectionConfigurer

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

class DynamicProxyReflectionConfigSpec extends AbstractTypeElementSpec {

    void "an annotation with DYNAMIC_PROXY registers the pair synthesize() actually creates"() {
        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import java.lang.annotation.*;

@ReflectionConfig(type = Guarded.class, accessType = TypeHint.AccessType.DYNAMIC_PROXY)
class Test {
}

@Retention(RetentionPolicy.RUNTIME)
@interface Guarded {
    String value() default "";
    int max() default 1;
}
''')
        def context = new RecordingContext(configurer.getClass().classLoader)

        when:
        configurer.configure(context)
        def guarded = context.findClassByName('test.Guarded')

        then:
        context.proxies == [
                [guarded],
                [guarded, AnnotationValueProvider]
        ]
    }

    void "a repeatable annotation and its container both register the pair"() {
        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import java.lang.annotation.*;

@ReflectionConfig(type = Role.class, accessType = TypeHint.AccessType.DYNAMIC_PROXY)
@ReflectionConfig(type = Roles.class, accessType = TypeHint.AccessType.DYNAMIC_PROXY)
class Test {
}

@Repeatable(Roles.class)
@Retention(RetentionPolicy.RUNTIME)
@interface Role {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@interface Roles {
    Role[] value();
}
''')
        def context = new RecordingContext(configurer.getClass().classLoader)

        when:
        configurer.configure(context)
        def role = context.findClassByName('test.Role')
        def roles = context.findClassByName('test.Roles')

        then:
        context.proxies.contains([role, AnnotationValueProvider])
        context.proxies.contains([roles, AnnotationValueProvider])
    }

    void "a non-annotation type with DYNAMIC_PROXY registers only itself"() {
        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@ReflectionConfig(type = Bar.class, accessType = TypeHint.AccessType.DYNAMIC_PROXY)
class Test {
}

interface Bar {}
''')
        def context = new RecordingContext(configurer.getClass().classLoader)

        when:
        configurer.configure(context)

        then:
        context.proxies == [[context.findClassByName('test.Bar')]]
    }

    void "an annotation without DYNAMIC_PROXY registers no proxy"() {
        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import java.lang.annotation.*;

@ReflectionConfig(type = Guarded.class, accessType = TypeHint.AccessType.ALL_DECLARED_METHODS)
class Test {
}

@Retention(RetentionPolicy.RUNTIME)
@interface Guarded {
}
''')
        def context = new RecordingContext(configurer.getClass().classLoader)

        when:
        configurer.configure(context)

        then:
        context.proxies.isEmpty()
    }

    private static class RecordingContext implements GraalReflectionConfigurer.ReflectionConfigurationContext {
        final List<List<Class<?>>> proxies = []
        private final ClassLoader classLoader

        RecordingContext(ClassLoader classLoader) {
            this.classLoader = classLoader
        }

        @Override
        Class<?> findClassByName(String name) {
            try {
                return classLoader.loadClass(name)
            } catch (ClassNotFoundException e) {
                return null
            }
        }

        @Override
        void register(Class<?>... types) {
        }

        @Override
        void register(Method... methods) {
        }

        @Override
        void register(Field... fields) {
        }

        @Override
        void register(Constructor<?>... constructors) {
        }

        @Override
        void registerDynamicProxy(Class<?>... interfaces) {
            proxies.add(interfaces.toList())
        }
    }
}
