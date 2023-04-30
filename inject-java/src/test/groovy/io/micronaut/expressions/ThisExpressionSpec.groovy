package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec
import spock.lang.PendingFeature

class ThisExpressionSpec extends AbstractEvaluatedExpressionsSpec {
    @PendingFeature(reason = "At some point it would be nice to support resolving this in injection points but requires signficant changes")
    void "test this access for field"() {
        given:
        def ctx = buildContext("""
            package test;

            import io.micronaut.context.annotation.Value;

            @jakarta.inject.Singleton
            class Expr {

                @Value("#{ 'test1' }")
                private String firstValue;

                @Value("#{ this.firstValue + 'ok' }")
                public String secondValue;

                public String getFirstValue() {
                    return firstValue;
                }
            }
        """)

        def type = ctx.classLoader.loadClass('test.Expr')
        def bean = ctx.getBean(type)

        expect:
        bean.firstValue == 'test1'
        bean.secondValue == 'test1ok'

        cleanup:
        ctx.close()
    }
}
