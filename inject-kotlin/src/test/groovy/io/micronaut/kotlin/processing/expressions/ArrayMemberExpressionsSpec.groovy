package io.micronaut.kotlin.processing.expressions

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildBeanDefinition
import static io.micronaut.annotation.processing.test.KotlinCompiler.buildContext

class ArrayMemberExpressionsSpec extends Specification {

    void "test expressions in an array annotation member are evaluated"() {
        given:
        ApplicationContext ctx = buildContext("""
            package test

            import jakarta.inject.Singleton

            @Singleton
            @Tags(["k", "#{ 'v'.toUpperCase() }", "#{ env['test.tag'] }"])
            class Expr

            annotation class Tags(val value: Array<String>)
        """, false, ['test.tag': 'fromEnv'])

        def type = ctx.classLoader.loadClass('test.Expr')
        def annType = ctx.classLoader.loadClass('test.Tags')
        def definition = ctx.getBeanDefinition(type)

        expect:
        definition.annotationMetadata.hasEvaluatedExpressions()
        definition.stringValues(annType) == ['k', 'V', 'fromEnv'] as String[]

        cleanup:
        ctx.close()
    }

    void "test expressions in a vararg annotation member are evaluated"() {
        given:
        ApplicationContext ctx = buildContext("""
            package test

            import jakarta.inject.Singleton

            @Singleton
            @Tags("k", "#{ 1 + 1 }")
            class Expr

            annotation class Tags(vararg val value: String)
        """)

        def type = ctx.classLoader.loadClass('test.Expr')
        def annType = ctx.classLoader.loadClass('test.Tags')

        expect:
        ctx.getBeanDefinition(type).stringValues(annType) == ['k', '2'] as String[]

        cleanup:
        ctx.close()
    }

    void "test expressions in a named array member of a Java annotation are compiled as expressions"() {
        given:
        def definition = buildBeanDefinition('test.Expr', """
            package test

            import io.micronaut.context.annotation.Requires
            import jakarta.inject.Singleton

            @Singleton
            @Requires(env = ["#{ 'te' + 'st' }"])
            class Expr
        """)

        expect:
        definition.annotationMetadata.hasEvaluatedExpressions()
        definition.stringValues(Requires, 'env') == ['test'] as String[]
    }

    void "test array annotation member without expressions is not an expression"() {
        given:
        ApplicationContext ctx = buildContext("""
            package test

            import jakarta.inject.Singleton

            @Singleton
            @Tags(["k", "v"])
            class Expr

            annotation class Tags(val value: Array<String>)
        """)

        def type = ctx.classLoader.loadClass('test.Expr')
        def annType = ctx.classLoader.loadClass('test.Tags')
        def definition = ctx.getBeanDefinition(type)

        expect:
        !definition.annotationMetadata.hasEvaluatedExpressions()
        definition.stringValues(annType) == ['k', 'v'] as String[]

        cleanup:
        ctx.close()
    }
}
