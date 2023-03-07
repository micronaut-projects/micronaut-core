package io.micronaut.expressions

import io.micronaut.ast.transform.test.AbstractEvaluatedExpressionsSpec

class CompoundExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test compound expressions"() {
        given:
        List<Object> results = evaluateMultiple(
                "a #{1}#{'b'} #{3}",
                "#{1 + 2}#{2 + 3}",
                "#{ '5' } s",
                "a#{ null }b"
        )

        expect:
        results[0] instanceof String && results[0] == 'a 1b 3'
        results[1] instanceof String && results[1] == '35'
        results[2] instanceof String && results[2] == '5 s'
        results[3] instanceof String && results[3] == 'anullb'
    }

    void "test string expressions in arrays"() {
        Object result = evaluateSingle("test.Expr", """
            package test
            import io.micronaut.context.annotation.Requires
            import jakarta.inject.Singleton

            @Singleton
            @Requires(env = ["#{ 'a' }", "b", "#{ 'c' + 'd' }"])
            class Expr {
            }
        """)

        expect:
        result instanceof Object[] && Arrays.equals((Object[]) result, new Object[]{'a', 'b', 'cd'})
    }


    void "test mixed expressions in arrays"() {
        Object result = evaluateSingle("test.Expr", """
            package test;
            import io.micronaut.context.annotation.Requires;
            import jakarta.inject.Singleton;

            @Singleton
            @Requires(env = ["#{ 1 }", "b", "#{ 15l }", "#{ 'c' }"])
            class Expr {
            }
        """)

        expect:
        result instanceof Object[] && Arrays.equals((Object[]) result, new Object[]{1, 'b', 15l, 'c'})
    }

}
