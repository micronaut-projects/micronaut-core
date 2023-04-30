package io.micronaut.expressions

import io.micronaut.ast.transform.test.AbstractEvaluatedExpressionsSpec
import io.micronaut.context.env.PropertySource

class EnvironmentAccessExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test environment access"() {
        given:
        def ctx = buildContext("""
            package test

            import io.micronaut.context.annotation.Value

            @jakarta.inject.Singleton
            class Expr {

                @Value("#{ env['first.property'] }")
                String firstProperty

                @Value("#{ env['second' + '.' + 'property'] }")
                String secondProperty

                @Value("#{ env [ 'third.property' ].toUpperCase() }")
                String thirdProperty

                @Value("#{ env['nullable.property']?.toUpperCase() }")
                String nullableProperty

            }
        """)

        def type = ctx.classLoader.loadClass('test.Expr')

        ctx.environment.addPropertySource(PropertySource.of("test",
                ['first.property': 'firstValue',
                 'second.property': 'secondValue',
                 'third.property': 'thirdValue']))

        def bean = ctx.getBean(type)

        expect:
        bean.firstProperty == 'firstValue'
        bean.secondProperty == 'secondValue'
        bean.thirdProperty == 'THIRDVALUE'
        bean.nullableProperty == null

        cleanup:
        ctx.close()
    }
}
