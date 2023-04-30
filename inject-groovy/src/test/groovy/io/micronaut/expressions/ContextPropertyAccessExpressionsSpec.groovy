package io.micronaut.expressions

import io.micronaut.ast.transform.test.AbstractEvaluatedExpressionsSpec
import spock.lang.Ignore

@Ignore('''
    FLAKY: We already test the Java code-path, and this should be re-instated at some point, but until the flakiness is resolved we are disabling it.
    It's our opinion that this is due to a Groovy bug where the classloader sometimes sees a different class depending on the order of the compilation.
''')
class ContextPropertyAccessExpressionsSpec extends AbstractEvaluatedExpressionsSpec
{
    @Ignore("already tested in java and flakey in Groovy")
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
