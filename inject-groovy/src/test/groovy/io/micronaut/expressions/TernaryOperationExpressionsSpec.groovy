package io.micronaut.expressions

import io.micronaut.ast.transform.test.AbstractEvaluatedExpressionsSpec

class TernaryOperationExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test ternary operator"() {
        given:
        List<Object> results = evaluateMultiple(
                "#{ 15 > 10 ? 'a' : 'b' }",
                "#{ 15 == 10 ? 'a' : 'b' }",
                "#{ 10 > 9 ? 'a' + 'b' : 'b' + 'a' }"
        )

        expect:
        results[0] instanceof String && results[0] == 'a'
        results[1] instanceof String && results[1] == 'b'
        results[2] instanceof String && results[2] == 'ab'
    }

    void "test ternary type resolution"() {
        given:
        List<Object> results = evaluateMultiple(
                "#{ (15 > 10 ? 'a' : 'b').length() }",
                "#{ 15 > 10 ? 15L : 'test' }",
                "#{ (15 > 10 ? 15L : 10) + 8}",
                "#{ 10 > 15 ? 15L : true}"
        )

        expect:
        results[0] instanceof Integer && results[0] == 1
        results[1] instanceof Long && results[1] == 15
        results[2] instanceof Long && results[2] == 23
        results[3] instanceof Boolean && results[3] == true
    }
}
