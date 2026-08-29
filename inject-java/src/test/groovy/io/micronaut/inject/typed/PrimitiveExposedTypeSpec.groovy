package io.micronaut.inject.typed

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.exceptions.NoSuchBeanException

class PrimitiveExposedTypeSpec extends AbstractTypeElementSpec {

    void 'test a factory method can expose a primitive type and its wrapper'() {
        given:
        def context = buildContext('''
package primitivetypes;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;

@Factory
class Numbers {
    @Bean(typed = { double.class, Double.class })
    @Prototype
    double max() {
        return 10.5d;
    }
}
''')
        def definition = context.getBeanDefinition(double.class)

        expect:
        definition.exposedTypes == [double.class, Double.class] as Set
        context.getBean(double.class) == 10.5d
        context.getBean(Double.class) == 10.5d

        cleanup:
        context.close()
    }

    void 'test a factory field can expose a primitive type and its wrapper'() {
        given:
        def context = buildContext('''
package primitivetypes;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;

@Factory
class Counters {
    @Bean(typed = { int.class, Integer.class })
    @Prototype
    int count = 42;
}
''')
        def definition = context.getBeanDefinition(int.class)

        expect:
        definition.exposedTypes == [int.class, Integer.class] as Set
        context.getBean(int.class) == 42
        context.getBean(Integer.class) == 42

        cleanup:
        context.close()
    }

    void 'test a factory method exposing only the primitive type is not resolvable by the wrapper'() {
        given:
        def context = buildContext('''
package primitivetypes;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;

@Factory
class Flags {
    @Bean(typed = boolean.class)
    @Prototype
    boolean enabled() {
        return true;
    }
}
''')
        def definition = context.getBeanDefinition(boolean.class)

        expect:
        definition.exposedTypes == [boolean.class] as Set
        context.getBean(boolean.class)
        !context.containsBean(Boolean.class)

        cleanup:
        context.close()
    }

    void 'test a factory method producing a wrapper can expose the primitive type'() {
        given:
        def context = buildContext('''
package primitivetypes;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;

@Factory
class Longs {
    @Bean(typed = { long.class, Long.class })
    @Prototype
    Long total() {
        return 7L;
    }
}
''')
        def definition = context.getBeanDefinition(Long.class)

        expect:
        definition.exposedTypes == [long.class, Long.class] as Set
        context.getBean(long.class) == 7L
        context.getBean(Long.class) == 7L

        cleanup:
        context.close()
    }

    void 'test a factory method producing a primitive can expose only the wrapper'() {
        given:
        def context = buildContext('''
package primitivetypes;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;

@Factory
class Numbers {
    @Bean(typed = Double.class)
    @Prototype
    double max() {
        return 10.5d;
    }
}
''')
        def definition = context.getBeanDefinition(Double.class)

        expect:
        definition.exposedTypes == [Double.class] as Set
        context.getBean(Double.class) == 10.5d
        !context.containsBean(double.class)

        cleanup:
        context.close()
    }

    void 'test a factory method producing a wrapper can expose only the primitive'() {
        given:
        def context = buildContext('''
package primitivetypes;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;

@Factory
class Numbers {
    @Bean(typed = double.class)
    @Prototype
    Double max() {
        return 10.5d;
    }
}
''')
        def definition = context.getBeanDefinition(double.class)

        expect:
        definition.exposedTypes == [double.class] as Set
        context.getBean(double.class) == 10.5d
        !context.containsBean(Double.class)

        cleanup:
        context.close()
    }

    void 'test a primitive exposed type unrelated to the bean type fails at runtime, not compilation'() {
        given:"a bean that exposes a primitive it does not produce"
        def context = buildContext('''
package primitivetypes;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;

@Factory
class Numbers {
    @Bean(typed = int.class)
    @Prototype
    double max() {
        return 10.5d;
    }
}
''')

        expect:"compilation to succeed, the exposed type to be taken at its word"
        context.getBeanDefinition(int.class).exposedTypes == [int.class] as Set

        and:"the bean to no longer be resolvable by the type it actually produces"
        !context.containsBean(double.class)

        when:
        context.getBean(double.class)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        context.close()
    }
}
