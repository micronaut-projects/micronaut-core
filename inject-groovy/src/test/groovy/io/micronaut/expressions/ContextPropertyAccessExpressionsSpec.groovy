package io.micronaut.expressions

import io.micronaut.ast.transform.test.AbstractEvaluatedExpressionsSpec
import io.micronaut.context.exceptions.ExpressionEvaluationException;

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

    void "test multi-level context property access"() {
        given:
        Object expr = evaluateAgainstContext("#{ #foo.bar.name }",
                """
            @jakarta.inject.Singleton
            class Context {
                Foo getFoo() {
                    return new Foo();
                }
            }

            class Foo {
                Bar bar = new Bar()
            }

            class Bar {
                String name = "test"
            }
        """)

        expect:
        expr instanceof String && expr == "test"
    }

    void "test multi-level context property access safe navigation"() {
        given:
        Object expr = evaluateAgainstContext("#{ #foo?.bar?.name }",
                """
            @jakarta.inject.Singleton
            class Context {
                Foo getFoo() {
                    return new Foo();
                }
            }

            class Foo {
                Bar bar
            }

            class Bar {
                String name = "test"
            }
        """)

        expect:
        expr == null
    }

    void "test multi-level context property access non-safe navigation"() {
        when:
        Object expr = evaluateAgainstContext("#{ #foo.bar.name }",
                """
            @jakarta.inject.Singleton
            class Context {
                Foo getFoo() {
                    return new Foo();
                }
            }

            class Foo {
                Bar bar
            }

            class Bar {
                String name = "test"
            }
        """)

        then:
        def e = thrown(ExpressionEvaluationException)
        e.message.startsWith('Can not evaluate expression [null]')
    }
}
