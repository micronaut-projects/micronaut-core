package io.micronaut.kotlin.processing.expressions

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildContext

class AnnotationLevelContextExpressionsSpec extends Specification {

    void "test annotation level context"() {
        given:
        ApplicationContext ctx = buildContext("""
            package test

            import io.micronaut.context.annotation.AnnotationExpressionContext
            import jakarta.inject.Singleton

            @Singleton
            @CustomAnnotation("#{ #getAnnotationLevelValue() }")
            class Expr

            @Singleton
            class CustomContext {
                fun getAnnotationLevelValue(): String = "annotationLevelValue"
            }

            @AnnotationExpressionContext(CustomContext::class)
            annotation class CustomAnnotation(val value: String)
        """)

        def type = ctx.classLoader.loadClass('test.Expr')
        def annType = ctx.classLoader.loadClass('test.CustomAnnotation')

        expect:
        ctx.getBeanDefinition(type).stringValue(annType).get() == 'annotationLevelValue'

        cleanup:
        ctx.close()
    }

    void "test annotation member level context"() {
        given:
        ApplicationContext ctx = buildContext("""
            package test

            import io.micronaut.context.annotation.AnnotationExpressionContext
            import jakarta.inject.Singleton

            @Singleton
            @CustomAnnotation(customValue = "#{ #getAnnotationLevelValue() }")
            class Expr

            @Singleton
            class CustomContext {
                fun getAnnotationLevelValue(): String = "annotationLevelValue"
            }

            annotation class CustomAnnotation(
                @get:AnnotationExpressionContext(CustomContext::class)
                val customValue: String
            )
        """)

        def type = ctx.classLoader.loadClass('test.Expr')
        def annType = ctx.classLoader.loadClass('test.CustomAnnotation')

        expect:
        ctx.getBeanDefinition(type).stringValue(annType, "customValue").get() == 'annotationLevelValue'

        cleanup:
        ctx.close()
    }
}
