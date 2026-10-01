package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.core.reflect.ClassUtils
import io.micronaut.core.type.Argument
import io.micronaut.inject.ArgumentInjectionPoint
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.CallableInjectionPoint
import io.micronaut.inject.ConstructorInjectionPoint
import io.micronaut.inject.FieldInjectionPoint
import io.micronaut.inject.InjectionPoint
import io.micronaut.inject.ValidatedBeanDefinition
import io.micronaut.inject.validation.BeanDefinitionValidator

/**
 * Regression for the field-injection gap left by https://github.com/micronaut-projects/micronaut-core/pull/12859.
 *
 * A plain {@code @Singleton} whose only constraints sit on {@code @Value}/{@code @Property}
 * <em>field</em> (or setter) injection points is compiled in injection-point-only mode: the generated
 * {@code validate} does nothing and the bean relies on {@code validateBeanArgument} being invoked
 * while each value is resolved. The runtime field paths used to skip that call, so such beans were
 * never validated at all.
 */
class ValidatedValueFieldSpec extends AbstractTypeElementSpec {

    private static final String FIELD_BEAN = '''
package test;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

@Singleton
class FieldBean {
    @Max(20)
    @NotNull
    @Value("${a.number}")
    Integer number;

    Integer getNumber() {
        return number;
    }
}
'''

    private static final String PROPERTY_FIELD_BEAN = '''
package test;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Max;

@Singleton
class PropertyFieldBean {
    @Max(20)
    @Property(name = "a.number")
    Integer number;

    Integer getNumber() {
        return number;
    }
}
'''

    private static final String SETTER_BEAN = '''
package test;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Max;

@Singleton
class SetterBean {
    private Integer number;

    @Inject
    void setNumber(@Max(20) @Value("${a.number}") Integer number) {
        this.number = number;
    }

    Integer getNumber() {
        return number;
    }
}
'''

    /**
     * Configuration properties are validated post-construct as well, which needs an introspection the
     * in-memory test class loader cannot provide, so only the invalid value is asserted for this bean.
     */
    private static final String CONFIGURATION_SETTER_BEAN = '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Value;
import jakarta.validation.constraints.Max;

@ConfigurationProperties("a")
class ConfigurationSetterBean {
    private Integer number;

    void setNumber(@Max(20) @Value("${a.number}") Integer number) {
        this.number = number;
    }

    Integer getNumber() {
        return number;
    }
}
'''

    private static final String CONSTRUCTOR_BEAN = '''
package test;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Max;

@Singleton
class ConstructorBean {
    private final Integer number;

    ConstructorBean(@Max(20) @Value("${a.number}") Integer number) {
        this.number = number;
    }

    Integer getNumber() {
        return number;
    }
}
'''

    void "singleton with validated @Value field is a ValidatedBeanDefinition without introspection"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.FieldBean', FIELD_BEAN)

        then:
        beanDefinition instanceof ValidatedBeanDefinition
        ClassUtils.forName('test.$FieldBean$Introspection', beanDefinition.beanType.classLoader).isEmpty()
    }

    void "bean with validated #description starts with a valid value"() {
        given:
        ApplicationContext context = buildContext(className, source, true, ['a.number': 10])

        when:
        def bean = getBean(context, className)

        then:
        bean.getNumber() == 10

        cleanup:
        context.close()

        where:
        description       | className                | source
        '@Value field'    | 'test.FieldBean'         | FIELD_BEAN
        '@Property field' | 'test.PropertyFieldBean' | PROPERTY_FIELD_BEAN
        '@Value setter'   | 'test.SetterBean'        | SETTER_BEAN
    }

    void "bean with validated #description rejects an invalid value"() {
        given:
        ApplicationContext context = buildContext(className, source, true, ['a.number': 40])

        when:
        getBean(context, className)

        then:
        BeanInstantiationException e = thrown()
        e.message.contains('must be less than or equal to 20')
        !e.message.toLowerCase().contains('no bean introspection present')

        cleanup:
        context.close()

        where:
        description                              | className                      | source
        '@Value field'                           | 'test.FieldBean'               | FIELD_BEAN
        '@Property field'                        | 'test.PropertyFieldBean'       | PROPERTY_FIELD_BEAN
        '@Value setter'                          | 'test.SetterBean'              | SETTER_BEAN
        '@Value configuration properties setter' | 'test.ConfigurationSetterBean' | CONFIGURATION_SETTER_BEAN
    }

    void "singleton with validated @Value constructor parameter rejects an invalid value"() {
        given:
        ApplicationContext context = buildContext('test.ConstructorBean', CONSTRUCTOR_BEAN, true, ['a.number': 40])

        when:
        getBean(context, 'test.ConstructorBean')

        then:
        DependencyInjectionException e = thrown()
        e.cause instanceof BeanInstantiationException
        e.cause.message.contains('must be less than or equal to 20')

        cleanup:
        context.close()
    }

    void "validateBeanArgument receives the #expectedInjectionPoint for a constrained #description"() {
        given:
        ApplicationContext context = buildContext(className, source, false, ['a.number': 40])
        RecordingValidator validator = new RecordingValidator()
        context.registerSingleton(BeanDefinitionValidator, validator)

        when:
        def bean = getBean(context, className)

        then:
        bean.getNumber() == 40
        validator.validatedBeans.isEmpty()
        validator.arguments.size() == 1
        isExpectedInjectionPoint(validator.arguments[0].injectionPoint)
        validator.arguments[0].argument.name == 'number'
        validator.arguments[0].value == 40

        cleanup:
        context.close()

        where:
        description                    | className              | source           | expectedInjectionPoint      | isExpectedInjectionPoint
        '@Value field'                 | 'test.FieldBean'       | FIELD_BEAN       | 'FieldInjectionPoint'       | { it instanceof FieldInjectionPoint }
        '@Value setter parameter'      | 'test.SetterBean'      | SETTER_BEAN      | 'setter ArgumentInjectionPoint' | { it instanceof ArgumentInjectionPoint && it instanceof CallableInjectionPoint && it.name == 'setNumber' }
        '@Value constructor parameter' | 'test.ConstructorBean' | CONSTRUCTOR_BEAN | 'ConstructorInjectionPoint' | { it instanceof ConstructorInjectionPoint }
    }

    static class RecordingValidator implements BeanDefinitionValidator {
        final List<ValidatedArgument> arguments = []
        final List<Object> validatedBeans = []

        @Override
        <T> void validateBeanArgument(BeanResolutionContext resolutionContext, InjectionPoint injectionPoint, Argument<T> argument, int index, T value) throws BeanInstantiationException {
            arguments << new ValidatedArgument(injectionPoint, argument, value)
        }

        @Override
        <T> void validateBean(BeanResolutionContext resolutionContext, BeanDefinition<T> definition, T bean) throws BeanInstantiationException {
            validatedBeans << bean
        }
    }

    static class ValidatedArgument {
        final InjectionPoint injectionPoint
        final Argument<?> argument
        final Object value

        ValidatedArgument(InjectionPoint injectionPoint, Argument<?> argument, Object value) {
            this.injectionPoint = injectionPoint
            this.argument = argument
            this.value = value
        }
    }
}
