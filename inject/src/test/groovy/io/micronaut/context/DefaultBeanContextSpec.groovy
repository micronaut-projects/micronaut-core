package io.micronaut.context


import io.micronaut.core.type.Argument
import io.micronaut.runtime.ApplicationConfiguration
import spock.lang.Specification

import java.lang.reflect.Method
import java.util.function.Function

class DefaultBeanContextSpec extends Specification {

    def "test null safe methods and special cases"() {
        given:
            DefaultBeanContext beanContext = new DefaultBeanContext()
            beanContext.start()

        expect:
            beanContext.resolveMetadata(null).isEmpty()
            !beanContext.refreshBean(null).isPresent()
            beanContext.getActiveBeanRegistrations((Qualifier) null).isEmpty()
            beanContext.getBeanRegistrations(null).isEmpty()
            beanContext.getBeanRegistrations((Class) null, null).isEmpty()
            !beanContext.findBeanRegistration(null).isPresent()
            !beanContext.findExecutionHandle(System, "xyz").isPresent()
            !beanContext.findExecutableMethod(null, "xyz", null).isPresent()
            !beanContext.findExecutionHandle(null, "xyz", null).isPresent()
            !beanContext.findBeanDefinition(Argument.OBJECT_ARGUMENT, null).isPresent()
            !beanContext.findBeanDefinition(Argument.OBJECT_ARGUMENT, null).isPresent()
            beanContext.getActiveBeanRegistration(null, null) == null
            beanContext.getBeanDefinitions((Qualifier) null).isEmpty()


        cleanup:
            beanContext.close()
    }

    def "test attributes"() {
        given:
            DefaultBeanContext beanContext = new DefaultBeanContext()
            beanContext.start()

        when:
            def attributes = beanContext.getAttributes()
        then:
            attributes.isEmpty()
            !beanContext.getAttribute("xyz").isPresent()
            !beanContext.getAttribute("xyz", String).isPresent()
            !beanContext.getAttribute("xyz", Integer).isPresent()

        when:
            beanContext.setAttribute("xyz", 123)
            beanContext.setAttribute(null, 222)

        then:
            !attributes.isEmpty()
            beanContext.getAttribute("xyz").get() == 123
            beanContext.getAttribute("xyz", String).get() == "123"
            beanContext.getAttribute("xyz", Integer).get() == 123

        when:
            beanContext.removeAttribute("xyz", Integer)
            beanContext.removeAttribute("fff", Integer)
        then:
            attributes.isEmpty()
            !beanContext.getAttribute("xyz").isPresent()
            !beanContext.getAttribute("xyz", String).isPresent()
            !beanContext.getAttribute("xyz", Integer).isPresent()

        cleanup:
            beanContext.close()
    }

    def "cached predicate"() {
        given:
        DefaultBeanContext beanContext = new DefaultBeanContext()
        beanContext.registerSingleton(new MyBean())

        when:
        def b = beanContext.findBeanCandidates(null, Argument.of(MyBean), null)
        then:
        b.size() == 1

        when:
        b = beanContext.findBeanCandidates(null, Argument.of(MyBean), b[0])
        then:
        b.size() == 0


        cleanup:
        beanContext.close()
    }

    def "resolving a bean from a closed context throws IllegalStateException"() {
        given:
            ApplicationContext context = ApplicationContext.run()

        expect:
            context.getBean(ApplicationConfiguration) != null

        when:
            context.close()
            context.getBean(ApplicationConfiguration)

        then:
            IllegalStateException e = thrown()
            e.message == "Cannot resolve beans until the context is running"
    }

    def "container conversion only expands arrays and containers convertible to Iterable"() {
        given:
            DefaultBeanContext beanContext = new DefaultBeanContext()
            beanContext.getConversionService().addConverter(ConvertibleContainer, Iterable, new Function<ConvertibleContainer, Iterable>() {
                @Override
                Iterable apply(ConvertibleContainer container) {
                    return container.values
                }
            })
            Method asIterable = DefaultBeanContext.getDeclaredMethod("asIterable", Object)
            asIterable.accessible = true

        expect:
            invokeAsIterable(asIterable, beanContext, new ConvertibleContainer(["one", "two"] as List)) == ["one", "two"]
            invokeAsIterable(asIterable, beanContext, new NonConvertibleContainer()) == null
            invokeAsIterable(asIterable, beanContext, ["one", "two"] as Object[]) == ["one", "two"]

        cleanup:
            beanContext.close()
    }

    static class MyBean {
    }

    static class ConvertibleContainer {
        final List<String> values

        ConvertibleContainer(List<String> values) {
            this.values = values
        }
    }

    static class NonConvertibleContainer {
    }

    private static Object invokeAsIterable(Method method, DefaultBeanContext beanContext, Object container) {
        method.invoke(beanContext, (Object) container)
    }
}
