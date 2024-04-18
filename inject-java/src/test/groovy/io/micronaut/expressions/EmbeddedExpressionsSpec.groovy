package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec
import io.micronaut.context.annotation.Requires
import io.micronaut.inject.annotation.EvaluatedAnnotationValue

class EmbeddedExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test embedded expression"() {
        given:
        def ctx = buildContext("""
            package test;

            import io.micronaut.context.annotation.Requires;
            import io.micronaut.context.annotation.Value;

            @io.micronaut.expressions.EmbeddedExpressionAnnotation(
                    requirements = @Requires("#{1}"),
                    requirement = @Requires("#{2}")
            )
            @jakarta.inject.Singleton
            class MyBean {
            }
        """)

        def type = ctx.classLoader.loadClass('test.MyBean')
        def beanDefinition = ctx.getBeanDefinition(type)
        def av = beanDefinition.getAnnotation(EmbeddedExpressionAnnotation)
        def requirements = av.getAnnotations("requirements", Requires)

        expect:
            av instanceof EvaluatedAnnotationValue
            requirements[0] instanceof EvaluatedAnnotationValue
            requirements[0].longValue("value").getAsLong() == 1
            requirements[0].get("value", Long).get() == 1
            requirements[0].get("value", Object).get() == 1
            av.getAnnotations("requirements")[0].longValue("value").getAsLong() == 1
            av.getAnnotation("requirement", Requires).get().longValue("value").getAsLong() == 2
            av.getAnnotation("requirement", Requires).get().get("value", Long).get() == 2
            av.getAnnotation("requirement", Requires).get().get("value", Object).get() == 2

        cleanup:
        ctx.close()
    }

}
