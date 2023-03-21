package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec

class CollectionExpressionSpec extends AbstractEvaluatedExpressionsSpec {

    void "test list dereference"() {
        given:
        Object result = evaluateAgainstContext("#{list[1]}",
                """
            import java.util.*;
            @jakarta.inject.Singleton
            class Context {
                List<Integer> getList() {
                    return List.of(1, 2, 3);
                }
            }
        """)

        expect:
        result == 2
    }

    void "test primitive array dereference"() {
        given:
        Object result = evaluateAgainstContext("#{array[1]}",
                """
            import java.util.*;
            @jakarta.inject.Singleton
            class Context {
                int[] getArray() {
                    return new int[] {1,2,3};
                }
            }
        """)

        expect:
        result == 2
    }

    void "test map dereference"() {
        given:
        Object result = evaluateAgainstContext("#{map['foo']}",
                """
            import java.util.*;
            @jakarta.inject.Singleton
            class Context {
                Map<String, String> getMap() {
                    return Map.of(
                            "foo", "bar",
                            "baz", "stuff"
                    );
                }
            }
        """)

        expect:
        result == "bar"
    }
}
