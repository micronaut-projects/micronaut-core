package io.micronaut.inject.reflection

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.AbstractBeanConstructor
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ReflectionExecutablesSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(["spec.name": "ReflectionExecutablesSpec"])

    void "a method of a type with a bean definition resolves to the executable method of that definition"() {
        given:
        def method = Counted.getMethod("count", String)

        when:
        def executable = ReflectionExecutables.executableMethod(context, BeanIntrospector.SHARED, method)

        then: "the generated executable method, not a reflective one"
        !(executable instanceof ReflectionExecutableMethod)
        !(executable instanceof IntrospectedExecutableMethod)
        executable.declaringType == Counted
        executable.invoke(context.getBean(Counted), "abc") == 3
    }

    void "a method of an introspected type without a bean definition resolves through the bean method"() {
        given:
        def method = Described.getMethod("describe", String)

        when:
        def executable = ReflectionExecutables.executableMethod(context, BeanIntrospector.SHARED, method)

        then:
        executable instanceof IntrospectedExecutableMethod
        executable.declaringType == Described
        executable.getAnnotationMetadata().hasAnnotation(Executable)
        executable.invoke(new Described(), "x") == "described x"
    }

    void "a method of a type with neither a bean definition nor an introspection resolves reflectively"() {
        given:
        def method = Plain.getMethod("shout", String)

        when:
        def executable = ReflectionExecutables.executableMethod(context, BeanIntrospector.SHARED, method)

        then:
        executable instanceof ReflectionExecutableMethod
        executable.declaringType == Plain
        executable.invoke(new Plain(), "hi") == "HI"
    }

    void "a method declared by a super type resolves against the type declaring it, not against a sub type bean overriding it"() {
        given: "the method as the super type declares it, while only the sub type overriding it is a bean"
        def method = Greeter.getMethod("greet", String)

        expect: "the locator does answer for the super type, with the executable method of the overriding sub type"
        context.findExecutableMethod(Greeter, "greet", String).get().declaringType == LoudGreeter

        when:
        def executable = ReflectionExecutables.executableMethod(context, BeanIntrospector.SHARED, method)

        then: "that answer is rejected: the method named is the one the super type declares"
        executable.declaringType == Greeter
        executable.invoke(new Greeter(), "sam") == "hello sam"
    }

    void "a constructor of an introspected type resolves to the bean constructor of the introspection"() {
        given:
        def introspection = BeanIntrospection.getIntrospection(Described)

        when:
        def constructor = ReflectionExecutables.beanConstructor(introspection, Described.getConstructor())

        then:
        constructor.is(introspection.constructor)
    }

    void "a constructor of a type with no introspection resolves to a bean constructor over the reflective metadata"() {
        given:
        def ctor = Plain.getConstructor()

        when:
        def constructor = ReflectionExecutables.beanConstructor(null, ctor)

        then:
        constructor instanceof AbstractBeanConstructor
        constructor.declaringBeanType == Plain
        constructor.arguments.length == 0
        constructor.instantiate() instanceof Plain
    }

    void "a constructor the introspection does not describe is read from the constructor itself"() {
        given: "the introspection describes the two argument constructor"
        def introspection = BeanIntrospection.getIntrospection(Pair)
        def other = Pair.getConstructor(String)

        expect:
        introspection.constructorArguments.length == 2

        when:
        def constructor = ReflectionExecutables.beanConstructor(introspection, other)

        then: "the one the introspection describes is not returned for another constructor"
        !constructor.is(introspection.constructor)
        constructor.arguments*.type == [String]
        constructor.instantiate("only").first == "only"

        and: "the arguments of a described constructor are the ones of the introspection, of another one the reflective ones"
        ReflectionExecutables.constructorArguments(introspection, Pair.getConstructor(String, String))
                .is(introspection.constructorArguments)
        ReflectionExecutables.constructorArguments(introspection, other)*.type == [String]
    }

    void "a constructor a reflective introspection knows is one of its own bean constructors"() {
        given: "a reflective introspection, which describes every constructor of the type"
        def introspection = new ReflectionBeanIntrospector(BeanIntrospector.SHARED)
                .getIntrospection(Untouched)
        def other = Untouched.getConstructor(String)

        expect: "the introspection selects the two argument constructor, as the processor would"
        introspection.constructorArguments.length == 2

        when:
        def constructor = ReflectionExecutables.beanConstructor(introspection, other)

        then: "the other constructor is one the introspection carries, not one built over the raw metadata"
        constructor.class in introspection.constructors*.class
        !constructor.class.anonymousClass
        constructor.arguments*.type == [String]
        constructor.instantiate("only").first == "only"
    }

    static class Plain {
        String shout(String value) {
            value.toUpperCase()
        }
    }

    static class Greeter {
        @Executable
        String greet(String name) {
            "hello $name"
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ReflectionExecutablesSpec")
    static class LoudGreeter extends Greeter {
        @Override
        @Executable
        String greet(String name) {
            super.greet(name).toUpperCase()
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ReflectionExecutablesSpec")
    static class Counted {
        @Executable
        int count(String value) {
            value.length()
        }
    }

    @Introspected
    static class Described {
        @Executable
        String describe(String value) {
            "described $value"
        }
    }

    static class Untouched {
        final String first
        final String second

        Untouched(String first, String second) {
            this.first = first
            this.second = second
        }

        Untouched(String only) {
            this(only, null)
        }
    }

    @Introspected
    static class Pair {
        final String first
        final String second

        Pair(String first, String second) {
            this.first = first
            this.second = second
        }

        Pair(String only) {
            this(only, null)
        }
    }
}
