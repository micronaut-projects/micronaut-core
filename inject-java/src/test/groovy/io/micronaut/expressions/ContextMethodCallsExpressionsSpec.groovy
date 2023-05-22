package io.micronaut.expressions;

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec;

class ContextMethodCallsExpressionsSpec extends AbstractEvaluatedExpressionsSpec
{
    void "test context method calls"() {
        given:
        Object expr1 = evaluateAgainstContext("#{ getIntValue() }",
                """
            @jakarta.inject.Singleton
            class Context {
                int getIntValue() {
                    return 15;
                }
            }
        """)

        Object expr2 = evaluateAgainstContext("#{ getStringValue().toUpperCase() }",
                """
            @jakarta.inject.Singleton
            class Context {
                String getStringValue() {
                    return "test";
                }
            }
        """)

        Object expr3 = evaluateAgainstContext("#{ randomizer().nextInt(10) }",
                """
            import java.util.Random;

            @jakarta.inject.Singleton
            class Context {
                Random randomizer() {
                    return new Random();
                }
            }
        """)

        Object expr4 = evaluateAgainstContext("#{ lowercase('TEST') }",
                """
            import java.util.Random;

            @jakarta.inject.Singleton
            class Context {
                String lowercase(String value) {
                    return value.toLowerCase();
                }
            }
        """)

        ContextRegistrar.setClasses(
                "test.FirstContext",
                "test.SecondContext",
                "test.ThirdContext",
                "test.FourthContext"
        )
        Object expr5 = evaluateAgainstContext("#{ transform(getName(), getRepeat(), toLower()) }",
                """
            import java.util.Random;

            @jakarta.inject.Singleton
            class FirstContext {
                String transform(String value, int repeat, Boolean toLower) {
                    return (toLower ? value.toLowerCase() : value).repeat(repeat);
                }
            }

            @jakarta.inject.Singleton
            class SecondContext {
                String getName() {
                    return "TEST";
                }
            }

            @jakarta.inject.Singleton
            class ThirdContext {
                Integer getRepeat() {
                    return 2;
                }
            }

            @jakarta.inject.Singleton
            class FourthContext {
                boolean toLower() {
                    return true;
                }
            }
        """)
        ContextRegistrar.reset()

        Object expr6 = evaluateAgainstContext("#{ getTestObject().name }",
                """
            import java.util.Random;

            @jakarta.inject.Singleton
            class Context {
                TestObject getTestObject() {
                    return new TestObject();
                }
            }

            class TestObject {
                String getName() {
                    return "name";
                }
            }
        """)

        Object expr7 = evaluateAgainstContext("#{ values().get(random(values())) }",
                """
            import java.util.Random;
            import java.util.List;
            import java.util.Collection;
            import java.util.concurrent.ThreadLocalRandom;

            @jakarta.inject.Singleton
            class Context {
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
