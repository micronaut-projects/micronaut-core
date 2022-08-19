package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec

class ContextPropertyAccessExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test context property access"() {
        given:
        Object expr1 = evaluateAgainstContext("#{ #intValue }",
                """
            @EvaluatedExpressionContext
            class ExpressionContext {
                int getIntValue() {
                    return 15;
                }
            }
        """)

        Object expr2 = evaluateAgainstContext("#{ #boolean }",
                """
            @EvaluatedExpressionContext
            class ExpressionContext {
                Boolean isBoolean() {
                    return false;
                }
            }
        """)

        Object expr3 = evaluateAgainstContext("#{ #stringValue }",
                """
            @EvaluatedExpressionContext
            class ExpressionContext {
                String getStringValue() {
                    return "test value";
                }
            }
        """)

        Object expr4 = evaluateAgainstContext("#{ #customClass.customProperty }",
                """
            @EvaluatedExpressionContext
            class ExpressionContext {
                CustomClass getCustomClass() {
                    return new CustomClass();
                }
            }

            class CustomClass {
                String getCustomProperty() {
                    return "custom property";
                }
            }
        """)

        expect:
        expr1 instanceof Integer && expr1 == 15
        expr2 instanceof Boolean && expr2 == false
        expr3 instanceof String && expr3 == "test value"
        expr4 instanceof String && expr4 == "custom property"
    }
}
