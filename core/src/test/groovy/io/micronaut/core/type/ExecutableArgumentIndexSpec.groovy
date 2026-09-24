package io.micronaut.core.type

import spock.lang.Specification

class ExecutableArgumentIndexSpec extends Specification {

    def "default getArgument returns the argument with the name"() {
        given:
        def name = Argument.of(String, "name")
        def age = Argument.of(int, "age")
        def e = executable(name, age)

        expect:
        e.getArgument("name").get().is(name)
        e.getArgument("age").get().is(age)
        !e.getArgument("missing").isPresent()
        !executable().getArgument("x").isPresent()
    }

    def "default getArgument rejects null"() {
        when:
        executable(Argument.of(String, "name")).getArgument(null)

        then:
        thrown(NullPointerException)
    }

    private static Executable<Object, Object> executable(Argument<?>... arguments) {
        new Executable<Object, Object>() {
            @Override
            Class<Object> getDeclaringType() { Object }

            @Override
            Argument<?>[] getArguments() { arguments }

            @Override
            Object invoke(Object instance, Object... args) { null }
        }
    }

    def "default argumentIndexOf scans the arguments"() {
        given:
        def e = executable(Argument.of(String, "name"), Argument.of(int, "age"), Argument.of(String, "city"))

        expect:
        e.argumentIndexOf("name") == 0
        e.argumentIndexOf("age") == 1
        e.argumentIndexOf("city") == 2
        e.argumentIndexOf("missing") == -1
    }

    def "default argumentIndexOf on an executable without arguments"() {
        expect:
        executable().argumentIndexOf("x") == -1
    }

    def "default argumentIndexOf rejects null"() {
        when:
        executable(Argument.of(String, "name")).argumentIndexOf(null)

        then:
        thrown(NullPointerException)
    }
}
