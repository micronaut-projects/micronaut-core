package io.micronaut.expressions

import io.micronaut.ast.transform.test.AbstractEvaluatedExpressionsSpec

class ContextMethodCallsExpressionsSpec extends AbstractEvaluatedExpressionsSpec{

    void "test context method calls"() {
        given:
        Object expr1 = evaluateAgainstContext("#{ #getIntValue() }",
        """
            @EvaluatedExpressionContext
            class ExpressionContext {
                public int getIntValue() {
                    return 15
                }
            }
        """)

        Object expr2 = evaluateAgainstContext("#{ #getStringValue().toUpperCase() }",
        """
            @EvaluatedExpressionContext
            class ExpressionContext {
                String getStringValue() {
                    return "test"
                }
            }
        """)

        Object expr3 = evaluateAgainstContext("#{ #randomizer().nextInt(10) }",
        """
            import java.util.Random

            @EvaluatedExpressionContext
            class ExpressionContext {
                Random randomizer() {
                    return new Random()
                }
            }
        """)

        Object expr4 = evaluateAgainstContext("#{ #lowercase('TEST') }",
        """
            import java.util.Random

            @EvaluatedExpressionContext
            class ExpressionContext {
                String lowercase(String value) {
                    return value.toLowerCase()
                }
            }
        """)

        Object expr5 = evaluateAgainstContext("#{ #transform(#getName(), #getRepeat(), #toLower()) }",
        """
            import java.util.Random

            @EvaluatedExpressionContext
            class FirstContext {
                String transform(String value, int repeat, Boolean toLower) {
                    return (toLower ? value.toLowerCase() : value).repeat(repeat)
                }
            }

            @EvaluatedExpressionContext
            class SecondContext {
                String getName() {
                    return "TEST"
                }
            }

            @EvaluatedExpressionContext
            class ThirdContext {
                Integer getRepeat() {
                    return 2
                }
            }

            @EvaluatedExpressionContext
            class FourthContext {
                boolean toLower() {
                    return true
                }
            }
        """)

        Object expr6 = evaluateAgainstContext("#{ #getTestObject().name }",
                """
            import java.util.Random

            @EvaluatedExpressionContext
            class ExpressionContext {
                TestObject getTestObject() {
                    return new TestObject()
                }
            }

            class TestObject {
                String getName() {
                    return "name"
                }
            }
        """)

        Object expr7 = evaluateAgainstContext("#{ #values().get(#random(#values())) }",
                """
            import java.util.Random
            import java.util.List
            import java.util.Collection
            import java.util.concurrent.ThreadLocalRandom

            @EvaluatedExpressionContext
            class ExpressionContext {
                TestObject getTestObject() {
                    return new TestObject();
                }

                List<?> values() {
                    return List.of(1, 2, 3);
                }

                int random(Collection<?> values) {
                    return ThreadLocalRandom.current().nextInt(values.size());
                }
            }

            class TestObject {
                String getName() {
                    return "name";
                }
            }
        """)

        expect:
        expr1 instanceof Integer && expr1 == 15
        expr2 instanceof String && expr2 == "TEST"
        expr3 instanceof Integer && expr3 >= 0 && expr3 < 10
        expr4 instanceof String && expr4 == "test"
        expr5 instanceof String && expr5 == "testtest"
        expr6 instanceof String && expr6 == "name"
        expr7 instanceof Integer && (expr7 == 1 || expr7 == 2 || expr7 == 3)
    }
}
