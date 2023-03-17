package io.micronaut.expressions

import io.micronaut.ast.transform.test.AbstractEvaluatedExpressionsSpec;

class ContextPropertyAccessExpressionsSpec extends AbstractEvaluatedExpressionsSpec
{
    void "test context property access"() {
        given:
        Object expr1 = evaluateAgainstContext("#{ #intValue }",
                """
            @jakarta.inject.Singleton
            class Context {
                int getIntValue() {
                    return 15
                }
            }
        """)

        Object expr2 = evaluateAgainstContext("#{ #boolean }",
                """
            @jakarta.inject.Singleton
            class Context {
                Boolean isBoolean() {
                    return false
                }
            }
        """)

        Object expr3 = evaluateAgainstContext("#{ #stringValue }",
                """
            @jakarta.inject.Singleton
            class Context {
                String getStringValue() {
                    return "test value"
                }
            }
        """)

        Object expr4 = evaluateAgainstContext("#{ #customClass.customProperty }",
                """
            @jakarta.inject.Singleton
            class Context {
                CustomClass getCustomClass() {
                    return new CustomClass()
                }
            }

            class CustomClass {
                String customProperty = "custom property"
            }
        """)

        expect:
        expr1 instanceof Integer && expr1 == 15
        expr2 instanceof Boolean && expr2 == false
        expr3 instanceof String && expr3 == "test value"
        expr4 instanceof String && expr4 == "custom property"
    }
}
