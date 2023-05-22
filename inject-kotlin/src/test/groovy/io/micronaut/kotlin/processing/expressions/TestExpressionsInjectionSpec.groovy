package io.micronaut.kotlin.processing.expressions

import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildContext

class TestExpressionsInjectionSpec extends Specification {
    void "test expression constructor injection"() {
        given:
        def ctx = buildContext("""
            package test;

            import jakarta.inject.Singleton;
            import io.micronaut.context.annotation.Value;

            @Singleton
            class Expr(@Value("#{ 25 }") val num : Int)
        """)

        def type = ctx.classLoader.loadClass('test.Expr')
        def bean = ctx.getBean(type)

        expect:
        bean.num == 25

        cleanup:
        ctx.close()
    }
}
