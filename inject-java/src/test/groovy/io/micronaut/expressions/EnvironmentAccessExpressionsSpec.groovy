package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec
import io.micronaut.context.env.PropertySource

class EnvironmentAccessExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test environment access"() {
        given:
        def ctx = buildContext("""
            package test;

            import io.micronaut.context.annotation.Value;

            @jakarta.inject.Singleton
            class Expr {

                @Value("#{ env['first.property'] }")
                public String firstProperty;

                @Value("#{ env['second' + '.' + 'property'] }")
                public String secondProperty;

                @Value("#{ env [ 'third.property' ].toUpperCase() }")
                public String thirdProperty;

                @Value("#{ env['nullable.property']?.toUpperCase() }")
                public String nullableProperty;

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
