package io.micronaut.expressions

import io.micronaut.ast.transform.test.AbstractEvaluatedExpressionsSpec

class BeanContextAccessExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test bean context access"() {
        given:
        def ctx = buildContext("""
            package test

            import io.micronaut.context.annotation.Value

            @jakarta.inject.Singleton
            class AccessedBean {

                String firstValue() {
                    return "firstValue"
                }

                String secondValue() {
                    return "secondValue"
                }

            }

            @jakarta.inject.Singleton
            class Expr {

                @Value("#{ ctx[T(test.AccessedBean)].firstValue() }")
                String firstValue

                @Value("#{ ctx[test.AccessedBean].secondValue() }")
                String secondValue

            }
        """)

        def type = ctx.classLoader.loadClass('test.Expr')
        def bean = ctx.getBean(type)

        expect:
        bean.firstValue == 'firstValue'
        bean.secondValue == 'secondValue'

        cleanup:
        ctx.close()
    }

}
