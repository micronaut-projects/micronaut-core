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

        def prefix = '$' + type + 'Expr$Expr$method$test$Requires$value$'
        def names = ((URLClassLoader) ctx.classLoader).URLs
                .collect { new File(new File(it.toURI()), 'test') }
                .findAll { it.directory }
                .collectMany { dir -> dir.list().findAll { it.startsWith(prefix) } }
                .unique()
        def exprClass = (AbstractEvaluatedExpression) ctx.classLoader.loadClass("test." + (names[0] - '.class')).newInstance()
        String result = exprClass.evaluate(new DefaultExpressionEvaluationContext(null, ["arg0", value] as Object[], ctx.getBean(BeanContext), null))

        expect: "the class is named after the function, the annotation and the member"
        names.size() == 1
        result == '100abc'

        cleanup:
        ctx.close()

        where:
        type     | value
        'String' | '100'
        'Int'    | 100
    }
}
