package io.micronaut.context

import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.inject.ArgumentInjectionPoint
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.function.Function

class RuntimeBeanDefinitionResolutionContextSpec extends Specification {

    private static Function<RuntimeBeanDefinition.CreationContext, RuntimeBean> factory() {
        return { RuntimeBeanDefinition.CreationContext creationContext ->
            def injectionPoint = creationContext.injectionPoint.orElse(null)
            new RuntimeBean(
                injectionPoint?.declaringBean?.beanType,
                injectionPoint instanceof ArgumentInjectionPoint ? injectionPoint.argument.name : null,
                injectionPoint == null,
                creationContext.beanContext
            )
        }
    }

    void "test runtime bean factory receives the injection point of the bean it is created for"() {
        given:
        ApplicationContext context = ApplicationContext.run(["spec.name": getClass().simpleName])
        context.registerBeanDefinition(
            RuntimeBeanDefinition.builder(RuntimeBean, factory()).build()
        )

        when:
        Consumer consumer = context.getBean(Consumer)

        then: "the factory saw the injection point of the bean it was created for"
        consumer.runtimeBean.declaringBeanType == Consumer
        consumer.runtimeBean.argumentName == "runtimeBean"
        !consumer.runtimeBean.noInjectionPoint
        consumer.runtimeBean.beanContext.is(context)

        cleanup:
        context.close()
    }

    void "test runtime bean factory has no injection point for a top level lookup"() {
        given:
        ApplicationContext context = ApplicationContext.run(["spec.name": getClass().simpleName])
        context.registerBeanDefinition(
            RuntimeBeanDefinition.builder(Argument.of(RuntimeBean), factory()).build()
        )

        when:
        RuntimeBean bean = context.getBean(RuntimeBean)

        then: "a direct lookup is not made for an injection point"
        bean.noInjectionPoint
        bean.declaringBeanType == null

        cleanup:
        context.close()
    }

    static class RuntimeBean {
        final Class<?> declaringBeanType
        final String argumentName
        final boolean noInjectionPoint
        final BeanContext beanContext

        RuntimeBean(Class<?> declaringBeanType, String argumentName, boolean noInjectionPoint, BeanContext beanContext) {
            this.declaringBeanType = declaringBeanType
            this.argumentName = argumentName
            this.noInjectionPoint = noInjectionPoint
            this.beanContext = beanContext
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RuntimeBeanDefinitionResolutionContextSpec")
    static class Consumer {
        final RuntimeBean runtimeBean

        Consumer(RuntimeBean runtimeBean) {
            this.runtimeBean = runtimeBean
        }
    }
}
