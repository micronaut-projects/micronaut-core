package io.micronaut.kotlin.processing.expressions

import io.micronaut.context.BeanContext
import io.micronaut.context.expressions.AbstractEvaluatedExpression
import io.micronaut.context.expressions.DefaultExpressionEvaluationContext
import spock.lang.Specification;
import static io.micronaut.annotation.processing.test.KotlinCompiler.buildContext

class MethodArgumentEvaluationContextExpressionsSpec extends Specification {

    String script(String secondParamType) {
        """
            package test

            import io.micronaut.context.annotation.Executable
            import io.micronaut.context.annotation.Requires
            import jakarta.inject.Singleton

            @Singleton
            class ${secondParamType}Expr {

                @Executable
                @Requires(value = "#{ #second + 'abc' }")
                fun test(first: String, second: $secondParamType) {
                }
            }
        """
    }

    void "test method argument access with #type"() {
        given:
        def ctx = buildContext(script(type))

        def exprClass = (AbstractEvaluatedExpression) ctx.classLoader.loadClass("test.\$${type}Expr\$Expr0").newInstance()
        String result = exprClass.evaluate(new DefaultExpressionEvaluationContext(null, ["arg0", value] as Object[], ctx.getBean(BeanContext), null))

        expect:
        result == '100abc'

        cleanup:
        ctx.close()

        where:
        type     | value
        'String' | '100'
        'Int'    | 100
    }
}
