package io.micronaut.expressions

import io.micronaut.ast.transform.test.AbstractEvaluatedExpressionsSpec
import spock.lang.Ignore

@Ignore('''
    FLAKY: We already test the Java code-path, and this should be re-instated at some point, but until the flakiness is resolved we are disabling it.
    It's our opinion that this is due to a Groovy bug where the classloader sometimes sees a different class depending on the order of the compilation.
''')
class ArrayMethodsExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test primitive and wrapper varargs methods"() {
        given:
        Object expr1 = evaluateAgainstContext("#{ #countValues(1, 2, 3) }",
                """
            @jakarta.inject.Singleton
            class Context {
                int countValues(int... array) {
                    return array.length
                }
            }
        """)

        Object expr2 = evaluateAgainstContext("#{ #countValues(1) }",
                """
            @jakarta.inject.Singleton
            class Context {
                int countValues(int... array) {
                    return array.length
                }
            }
        """)

        Object expr3 = evaluateAgainstContext("#{ #countValues() }",
                """
            @jakarta.inject.Singleton
            class Context {
                int countValues(int... array) {
                    return array.length
                }
            }
        """)

        Object expr4 = evaluateAgainstContext("#{ #countValues(1, 2, T(Integer).valueOf('3')) }",
                """
            @jakarta.inject.Singleton
            class Context {
                int countValues(Integer... array) {
                    return array.length
                }
            }
        """)

        expect:
        expr1 instanceof Integer && expr1 == 3
        expr2 instanceof Integer && expr2 == 1
        expr3 instanceof Integer && expr3 == 0
        expr4 instanceof Integer && expr4 == 3
    }

    void "test string varargs methods"() {
        given:
        Object expr1 = evaluateAgainstContext("#{ #countValues('a', 'b', 'c') }",
                """
            @jakarta.inject.Singleton
            class Context {
                int countValues(String... values) {
                    return values.length
                }
            }
        """)

        Object expr2 = evaluateAgainstContext("#{ #countValues('a') }",
                """
            @jakarta.inject.Singleton
            class Context {
                int countValues(String... values) {
                    return values.length
                }
            }
        """)

        Object expr3 = evaluateAgainstContext("#{ #countValues() }",
                """
            @jakarta.inject.Singleton
            class Context {
                int countValues(String... values) {
                    return values.length
                }
            }
        """)

        expect:
        expr1 instanceof Integer && expr1 == 3
        expr2 instanceof Integer && expr2 == 1
        expr3 instanceof Integer && expr3 == 0
    }

    void "test mixed types varargs methods"() {
        given:
        Object expr1 = evaluateAgainstContext("#{ #multiplyLength(3, '1', 8, null) }",
                """
            @jakarta.inject.Singleton
            class Context {
                int multiplyLength(int time, Object... values) {
                    return values.length * time
                }
            }
        """)

        expect:
        expr1 instanceof Integer && expr1 == 9
    }

    void "test arrays as varargs"() {
        given:
        Object expr1 = evaluateAgainstContext("#{ #multiplyLength(3, '1', 8, null) }",
                """
            @jakarta.inject.Singleton
            class Context {
                int multiplyLength(Integer time, Object[] values) {
                    return values.length * time
                }
            }
        """)

        expect:
        expr1 instanceof Integer && expr1 == 9
    }

    void "test non-varargs arrays"() {
        given:
        Object expr1 = evaluateAgainstContext("#{ #countLength(#values()) }",
                """
            @jakarta.inject.Singleton
            class Context {
                String[] values() {
                    return [ "a", "b", "c" ]
                }

                int countLength(Object[] array) {
                    return array.length
                }
            }
        """)

        Object expr2 = evaluateAgainstContext("#{ #multiplyLength(#values(), 3) }",
                """
            @jakarta.inject.Singleton
            class Context {
                String[] values() {
                    return ["a", "b"]
                }

                int multiplyLength(String[] array, int times) {
                    return array.length * times
                }
            }
        """)

        Object expr3 = evaluateAgainstContext("#{ #multiplyLength(#values(), 1) }",
                """
            @jakarta.inject.Singleton
            class Context {
                int[] values() {
                    return new int[]{1, 2}
                }

                int multiplyLength(int[] array, int times) {
                    return array.length * times
                }
            }
        """)

        expect:
        expr1 instanceof Integer && expr1 == 3
        expr2 instanceof Integer && expr2 == 6
        expr3 instanceof Integer && expr3 == 2
    }

    void "test multi-dimensional arrays"() {
        given:
        Object expr1 = evaluateAgainstContext("#{ #countLength(#values()) }",
                """
            import java.util.Arrays

            @jakarta.inject.Singleton
            class Context {
                String[][] values() {
                    return [ ["a", "b", "c"], ["a", "b"] ]
                }

                int countLength(Object[][] array) {
                    return Arrays.stream(array)
                        .map(a -> a.length)
                        .reduce(0, Integer::sum)
                }
            }
        """)

        Object expr2 = evaluateAgainstContext("#{ #countLength(#values()) }",
                """
            import java.util.Arrays

            @jakarta.inject.Singleton
            class Context {
                int[][] values() {
                    return [[1, 2, 3], [1, 2, 3, 3]]
                }

                int countLength(int[][] array) {
                    return Arrays.stream(array)
                        .map(a -> a.length)
                        .reduce(0, Integer::sum)
                }
            }
        """)


        expect:
        expr1 instanceof Integer && expr1 == 5
        expr2 instanceof Integer && expr2 == 7
    }
}
