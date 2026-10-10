package io.micronaut.expressions

import io.micronaut.annotation.processing.test.AbstractEvaluatedExpressionsSpec

class MethodOverloadExpressionsSpec extends AbstractEvaluatedExpressionsSpec {

    void "test the most specific overload is selected for element method calls"() {
        given:
        List<Object> results = evaluateMultipleAgainstContext("""
            @jakarta.inject.Singleton
            class Context {
                Overloads overloads() {
                    return new Overloads();
                }

                Integer boxed() {
                    return 1;
                }
            }

            class Overloads {
                String text(CharSequence value) {
                    return "CharSequence";
                }

                String text(String value) {
                    return "String";
                }

                String text(Object value) {
                    return "Object";
                }

                String number(int value) {
                    return "int";
                }

                String number(Integer value) {
                    return "Integer";
                }

                String number(Object value) {
                    return "Object";
                }

                String boxing(Integer value) {
                    return "Integer";
                }

                String boxing(Object value) {
                    return "Object";
                }

                String varargs(String value) {
                    return "fixed";
                }

                String varargs(String... values) {
                    return "varargs";
                }

                String varargsOnly(CharSequence... values) {
                    return "CharSequence...";
                }

                String varargsOnly(String... values) {
                    return "String...";
                }

                String mixed(String... values) {
                    return "String...";
                }

                String mixed(Object... values) {
                    return "Object...";
                }

                String primitives(int... values) {
                    return "int...";
                }

                String primitives(long... values) {
                    return "long...";
                }

                static String staticText(CharSequence value) {
                    return "CharSequence";
                }

                static String staticText(String value) {
                    return "String";
                }
            }
        """,
                "#{ overloads().text('x') }",
                "#{ overloads().text(overloads()) }",
                "#{ overloads().number(1) }",
                "#{ overloads().number(boxed()) }",
                "#{ overloads().number('x') }",
                "#{ overloads().boxing(1) }",
                "#{ overloads().varargs('x') }",
                "#{ overloads().varargs('x', 'y') }",
                "#{ overloads().varargsOnly('x', 'y') }",
                "#{ overloads().mixed('x', 'y') }",
                "#{ overloads().mixed('x', boxed()) }",
                "#{ overloads().primitives() }",
                "#{ T(test.Overloads).staticText('x') }"
        )

        expect:
        results == [
                "String",
                "Object",
                "int",
                "Integer",
                "Object",
                "Integer",
                "fixed",
                "varargs",
                "String...",
                "String...",
                "Object...",
                "int...",
                "String"
        ]
    }

    void "test overloads inherited from different interfaces"() {
        given:
        Object result = evaluateAgainstContext("#{ headers().contains('Upgrade') }", """
            @jakarta.inject.Singleton
            class Context {
                Headers headers() {
                    return new Headers();
                }
            }

            interface Values {
                default boolean contains(String name) {
                    return false;
                }
            }

            interface CharSequenceValues {
                default boolean contains(CharSequence name) {
                    return false;
                }
            }

            class Headers implements Values, CharSequenceValues {
                @Override
                public boolean contains(String name) {
                    return "Upgrade".equals(name);
                }
            }
        """)

        expect:
        result == true
    }

    void "test the covariant method inherited from unrelated interfaces is selected"() {
        given:
        Object result = evaluateAgainstContext("#{ supplier().get().length() }", """
            @jakarta.inject.Singleton
            class Context {
                StringSupplier supplier() {
                    return new StringSupplier() {
                        @Override
                        public String get() {
                            return "four";
                        }
                    };
                }
            }

            interface ObjectGetter {
                Object get();
            }

            interface StringGetter {
                String get();
            }

            interface StringSupplier extends ObjectGetter, StringGetter {
            }
        """)

        expect:
        result == 4
    }

    void "test the most specific overload is selected for context method calls"() {
        given:
        Object result = evaluateAgainstContext("#{ text('x') }", """
            @jakarta.inject.Singleton
            class Context {
                String text(CharSequence value) {
                    return "CharSequence";
                }

                String text(String value) {
                    return "String";
                }
            }
        """)

        expect:
        result == "String"
    }

    void "test ambiguous method call without a most specific overload"() {
        when:
        evaluateAgainstContext("#{ overloads().pair('x', 'y') }", """
            @jakarta.inject.Singleton
            class Context {
                Overloads overloads() {
                    return new Overloads();
                }
            }

            class Overloads {
                String pair(String first, Object second) {
                    return "first";
                }

                String pair(Object first, String second) {
                    return "second";
                }
            }
        """)

        then:
        def e = thrown(Exception)
        e.message.contains("Ambiguous method call. Found 2 matching methods")
    }

    void "test ambiguous context method call without a most specific overload"() {
        when:
        evaluateAgainstContext("#{ pair('x', 'y') }", """
            @jakarta.inject.Singleton
            class Context {
                String pair(String first, Object second) {
                    return "first";
                }

                String pair(Object first, String second) {
                    return "second";
                }
            }
        """)

        then:
        def e = thrown(Exception)
        e.message.contains("Ambiguous expression evaluation context reference. Found 2 matching methods")
    }

    void "test identical methods of unrelated evaluation context classes are ambiguous"() {
        given:
        ContextRegistrar.setClasses("test.FirstContext", "test.SecondContext")

        when:
        evaluateAgainstContext("#{ text('x') }", """
            @jakarta.inject.Singleton
            class FirstContext {
                String text(String value) {
                    return "first";
                }
            }

            @jakarta.inject.Singleton
            class SecondContext {
                String text(String value) {
                    return "second";
                }
            }
        """)

        then:
        def e = thrown(Exception)
        e.message.contains("Ambiguous expression evaluation context reference. Found 2 matching methods")

        cleanup:
        ContextRegistrar.reset()
    }
}
