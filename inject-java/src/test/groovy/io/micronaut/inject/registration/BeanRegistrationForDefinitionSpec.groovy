package io.micronaut.inject.registration

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanDefinitionRegistry
import io.micronaut.context.BeanProvider
import io.micronaut.context.BeanResolutionCustomizer
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class BeanRegistrationForDefinitionSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run("spec": getClass().getSimpleName())

    def setup() {
        SingletonService.created = 0
        PrototypeService.created = 0
        PrototypeService.destroyed = 0
        PrototypeDependency.destroyed = 0
        ScopedService.created = 0
    }

    void "a singleton definition resolves to the singleton the context holds"() {
        given:
        def definition = context.getBeanDefinition(SingletonService)

        when:
        def registration = context.getBeanRegistration(definition, Argument.of(SingletonService))

        then:
        registration.bean.is(context.getBean(SingletonService))
        registration.beanDefinition == definition
        context.getBeanRegistration(definition, Argument.of(SingletonService)).bean.is(registration.bean)
        SingletonService.created == 1
    }

    void "a prototype definition resolves to a new instance, which is destroyed with its dependents"() {
        given:
        def definition = context.getBeanDefinition(PrototypeService)

        when:
        def first = context.getBeanRegistration(definition, Argument.of(PrototypeService))
        def second = context.getBeanRegistration(definition, Argument.of(PrototypeService))

        then:
        !first.bean.is(second.bean)
        !first.bean.dependency.is(second.bean.dependency)
        first.beanDefinition == definition
        PrototypeService.created == 2

        when:
        context.destroyBean(first)

        then: "the registration carries the dependents created with the bean"
        PrototypeService.destroyed == 1
        PrototypeDependency.destroyed == 1
    }

    void "a definition of a custom scope resolves to the instance the scope holds"() {
        given:
        def scope = context.getBean(RegistrationScopeImpl)
        def definition = context.getBeanDefinition(ScopedService)

        when:
        def registration = context.getBeanRegistration(definition, Argument.of(ScopedService))

        then:
        scope.beans.size() == 1
        registration.bean.is(context.getBean(ScopedService))
        context.getBeanRegistration(definition, Argument.of(ScopedService)).bean.is(registration.bean)
        ScopedService.created == 1
        scope.beans.size() == 1
    }

    void "a generic definition resolves as a parameterized type it is a candidate for, although a lookup of that type is not unique"() {
        given:
        def definition = context.getBeanDefinition(StringBox)

        when:
        context.getBeanRegistration(Argument.of(Box), null)

        then:
        thrown(NonUniqueBeanException)

        when:
        def parameterized = context.getBeanRegistration(definition, Argument.of(Box, String))
        def raw = context.getBeanRegistration(definition, Argument.of(Box))

        then:
        parameterized.bean instanceof StringBox
        parameterized.bean.value() == "string"
        parameterized.beanDefinition == definition
        raw.bean instanceof StringBox
        !raw.bean.is(parameterized.bean)
    }

    void "a provider definition builds its bean for the type argument of the type it is resolved as"() {
        given:
        def definition = context.getBeanDefinition(Argument.of(BeanProvider, SingletonService))

        when:
        def registration = context.getBeanRegistration(definition, Argument.of(BeanProvider, SingletonService))

        then:
        registration.bean.get() instanceof SingletonService
        registration.bean.get().is(context.getBean(SingletonService))
    }

    void "a definition that is not a candidate for the type is refused"() {
        given:
        def definition = context.getBeanDefinition(StringBox)

        when:
        context.getBeanRegistration(definition, Argument.of(Box, Integer))

        then:
        def e = thrown(NoSuchBeanException)
        e.message.contains("is not a candidate for that type")

        when:
        context.getBeanRegistration(definition, Argument.of(ScopedService))

        then:
        thrown(NoSuchBeanException)
        ScopedService.created == 0
    }

    void "a nullable definition that produces no bean resolves to a registration without one"() {
        when:
        def registration = context.getBeanRegistration(context.getBeanDefinition(NullableBox), Argument.of(NullableBox))

        then:
        registration.bean == null
    }

    void "the resolution customizer replaces the bean of a definition that produces none"() {
        given:
        NullableBox replacement = new NullableBox()
        ApplicationContext customized = ApplicationContext.builder()
                .properties("spec": getClass().getSimpleName())
                .beanResolutionCustomizer(new BeanResolutionCustomizer() {
                    @Override
                    Optional<?> resolveNullBean(Argument<?> requestedBeanType, Argument<?> resolvedBeanType, BeanDefinition<?> beanDefinition) {
                        return requestedBeanType.type == NullableBox ? Optional.of(replacement) : Optional.empty()
                    }
                })
                .start()

        when:
        def registration = customized.getBeanRegistration(customized.getBeanDefinition(NullableBox), Argument.of(NullableBox))

        then:
        registration.bean.is(replacement)

        cleanup:
        customized.close()
    }

    void "a definition of an implementation is resolved as an interface from Java without a cast"() {
        expect:
        JavaCaller.resolveAsInterface(context) instanceof StringBox
        JavaCaller.resolveAsSameType(context, context.getBeanDefinition(StringBox), Argument.of(StringBox)) instanceof StringBox
    }

    void "a registry that does not implement it refuses rather than resolve under another scope key"() {
        given:
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) Proxy.newProxyInstance(
                getClass().classLoader,
                [BeanDefinitionRegistry] as Class[],
                { Object proxy, Method method, Object[] args ->
                    method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : method.invoke(context, args)
                } as InvocationHandler
        )

        when:
        registry.getBeanRegistration(context.getBeanDefinition(ScopedService), Argument.of(ScopedService))

        then:
        thrown(UnsupportedOperationException)
        ScopedService.created == 0
    }

    void "a definition of a custom scope resolves to the instance an earlier lookup put in the scope"() {
        given:
        ScopedService lookedUp = context.getBean(ScopedService)

        when:
        def registration = context.getBeanRegistration(context.getBeanDefinition(ScopedService), Argument.of(ScopedService))

        then:
        registration.bean.is(lookedUp)
        ScopedService.created == 1
    }
}
