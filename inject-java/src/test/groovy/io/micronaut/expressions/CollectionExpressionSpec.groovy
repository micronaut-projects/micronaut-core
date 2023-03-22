package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec
import org.intellij.lang.annotations.Language

class CollectionExpressionSpec extends AbstractEvaluatedExpressionsSpec {

    void "test list dereference"() {
        given:
        @Language("java") def context = """
            import java.util.*;
            @jakarta.inject.Singleton
            class Context {
                List<Integer> getList() {
                    return List.of(1, 2, 3);
                }

                int index() {
                    return 1;
                }

                List<Foo> foos() {
                    List<Foo> list = new ArrayList<>();
                    list.add(new Foo("one"));
                    list.add(new Foo("two"));
                    list.add(null);
                    return list;
                }
            }

            record Foo(String name) {}
        """

        Object result = evaluateAgainstContext("#{list[1]}", context)
        Object result2 = evaluateAgainstContext("#{list[index()]}", context)
        Object result3 = evaluateAgainstContext("#{not empty list}", context)
        Object result4 = evaluateAgainstContext("#{foos()[1].name()}", context)
        Object result5 = evaluateAgainstContext("#{foos()[2]?.name()}", context)

        expect:
        result == 2
        result2 == 2
        result3 == true
        result4 == 'two'
        result5 == null
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
        @Language("java") def context = """
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
        """
        Object result = evaluateAgainstContext("#{map['foo']}",context)
        Object result2 = evaluateAgainstContext("#{not empty map}",context)

        expect:
        result == "bar"
        result2 == true
    }
}
