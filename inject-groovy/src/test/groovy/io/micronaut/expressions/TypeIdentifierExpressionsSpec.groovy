package io.micronaut.expressions

import io.micronaut.ast.transform.test.AbstractEvaluatedExpressionsSpec

class TypeIdentifierExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test static methods"() {
        given:
        List<Object> results = evaluateMultiple(
                "#{ T(Math).random() }",
                "#{ T(java.lang.Math).random() }",
                "#{ T(Integer).valueOf('10') }",
                "#{ T(String).join(',', '1', '2', '3') }",
                "#{ T(String).join(',', 'a', 'b').repeat(2) }"
        )

        expect:
        results[0] instanceof Double && results[0] >= 0 && results[0] < 1
        results[1] instanceof Double && results[1] >= 0 && results[1] < 1
        results[2] instanceof Integer && results[2] == 10
        results[3] instanceof String && results[3] == "1,2,3"
        results[4] instanceof String && results[4] == "a,ba,b"
    }

    void "test type identifier as argument"() {
        given:
        Object expr1 = evaluateAgainstContext("#{ #getType(T(java.lang.String)) }",
                """
            @jakarta.inject.Singleton
            class Context {
                Class<?> getType(Class<?> type) {
                    return type
                }
            }
        """)

        Object expr2 = evaluateAgainstContext("#{ #getType(T(String), T(Object)) }",
                """
            @jakarta.inject.Singleton
            class Context {
                Class<?> getType(Class<?>... types) {
                    return types[1]
                }
            }
        """)

        expect:
        expr1 instanceof Class && expr1 == String.class
        expr2 instanceof Class && expr2 == Object.class
    }

}
