package io.micronaut.context

import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.function.Function

class RuntimeBeanDefinitionResolutionContextSpec extends Specification {

    void "test runtime bean factory receives the resolution context of the injection point"() {
        given:
        ApplicationContext context = ApplicationContext.run(["spec.name": getClass().simpleName])
        Function<BeanResolutionContext, RuntimeBean> factory = { BeanResolutionContext resolutionContext ->
            def segment = resolutionContext.getPath().currentSegment().orElse(null)
            new RuntimeBean(
                segment?.declaringType?.beanType,
                segment?.argument?.name,
                resolutionContext.getPath().isEmpty()
            )
        }
        context.registerBeanDefinition(
            RuntimeBeanDefinition.builder(RuntimeBean, factory).build()
        )

        when:
        Consumer consumer = context.getBean(Consumer)

        then: "the factory saw the injection point of the bean it was created for"
        consumer.runtimeBean.declaringBeanType == Consumer
        consumer.runtimeBean.argumentName == "runtimeBean"
        !consumer.runtimeBean.pathWasEmpty

        cleanup:
        context.close()
    }

    void "test runtime bean factory receives the resolution context for a top level lookup"() {
        given:
        ApplicationContext context = ApplicationContext.run(["spec.name": getClass().simpleName])
        Function<BeanResolutionContext, RuntimeBean> factory = { BeanResolutionContext resolutionContext ->
            def segment = resolutionContext.getPath().currentSegment().orElse(null)
            new RuntimeBean(
                segment?.declaringType?.beanType,
                segment?.argument?.name,
                resolutionContext.getPath().isEmpty()
            )
        }
        context.registerBeanDefinition(
            RuntimeBeanDefinition.builder(Argument.of(RuntimeBean), factory).build()
        )

        when:
        RuntimeBean bean = context.getBean(RuntimeBean)

        then: "a direct lookup has an empty path or one rooted at the bean itself"
        bean.pathWasEmpty || bean.declaringBeanType == RuntimeBean

        cleanup:
        context.close()
    }

    static class RuntimeBean {
        final Class<?> declaringBeanType
        final String argumentName
        final boolean pathWasEmpty

        RuntimeBean(Class<?> declaringBeanType, String argumentName, boolean pathWasEmpty) {
            this.declaringBeanType = declaringBeanType
            this.argumentName = argumentName
            this.pathWasEmpty = pathWasEmpty
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
