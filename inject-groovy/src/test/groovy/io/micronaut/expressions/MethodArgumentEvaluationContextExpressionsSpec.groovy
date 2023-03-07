package io.micronaut.expressions;

import io.micronaut.ast.transform.test.AbstractEvaluatedExpressionsSpec

class MethodArgumentEvaluationContextExpressionsSpec extends AbstractEvaluatedExpressionsSpec
{
    void "test method argument access"() {
        given:
        Object result = evaluateSingle("test.Expr", """
            package test
            import io.micronaut.context.annotation.Executable
            import io.micronaut.context.annotation.Requires
            import jakarta.inject.Singleton

            @Singleton
            class Expr {

                @Executable
                @Requires(value = "#{ #second + \'abc\' }")
                void test(String first, String second) {
                }
            }


        """, ["arg0", "arg1"] as Object[]);

        expect:
        result instanceof String && result == 'arg1abc'
    }
}
