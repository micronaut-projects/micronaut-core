package io.micronaut.reflection

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.type.Argument
import io.micronaut.inject.MethodReference
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

    void "an executable method of a bean definition is taken only when it describes the method named"() {
        given: "a bean of a generic super class declaring two methods of one name, the list one declared first"
        def method = ExecBase.getDeclaredMethod("save", Object)
        def store = context.getBean(ExecStore)

        expect: "the locator answers for the super type, falling back to a match on the name alone"
        context.findExecutableMethod(ExecBase, "save", Object).isPresent()

        when:
        def executable = ReflectionExecutables.executableMethod(context, BeanIntrospector.SHARED, method)
        executable.invoke(store, "single")

        then: "the method named is the one invoked, not the other method of the same name"
        executable.arguments.length == 1
        executable.arguments[0].type != List
        store.saved == ["one:single"]

        when: "the other method is the one named"
        def other = ReflectionExecutables.executableMethod(context, BeanIntrospector.SHARED,
                ExecBase.getDeclaredMethod("save", List))
        other.invoke(store, ["a", "b"])

        then: "the executable method that does describe it is taken as it is"
        other.arguments[0].type == List
        store.saved == ["one:single", "many:2"]
    }

    void "the method of a name and argument types is the most specific declaration that applies"() {
        expect: "a declaration of the very types wins"
        ReflectionExecutables.findMethod(ExecHandlers, "on", Number).get() == ExecHandlers.getDeclaredMethod("on", Number)

        and: "of the two declarations that apply to an Integer, the most specific one, whatever order the \
methods of the type are reported in"
        ReflectionExecutables.findMethod(ExecHandlers, "on", Integer).get() == ExecHandlers.getDeclaredMethod("on", Number)

        and: "the erasure of the type variable when it is the only declaration that applies"
        ReflectionExecutables.findMethod(ExecHandlers, "on", String).get() == ExecHandlers.getDeclaredMethod("on", Object)
    }

    void "a static method is found too, so a reference that cannot report its target method still resolves"() {
        given: "a reference of a static method whose own target method lookup fails"
        def reference = [
                getDeclaringType: { ExecHandlers },
                getMethodName   : { "register" },
                getArguments    : { [Argument.of(String)] as Argument[] },
                getArgumentTypes: { [String] as Class[] },
                getTargetMethod : { throw new NoSuchMethodError("no target method") }
        ] as MethodReference

        expect:
        ReflectionExecutables.findMethod(ExecHandlers, "register", String).get() ==
                ExecHandlers.getDeclaredMethod("register", String)
        ReflectionExecutables.targetMethod(reference) == ExecHandlers.getDeclaredMethod("register", String)
    }

    void "the exception a method throws is the exception the caller catches"() {
        given:
        def bean = new ExecThrowing()
        def unchecked = ReflectionExecutables.executableMethod(context, BeanIntrospector.SHARED,
                ExecThrowing.getDeclaredMethod("unchecked"))
        def checked = ReflectionExecutables.executableMethod(context, BeanIntrospector.SHARED,
                ExecThrowing.getDeclaredMethod("checked"))

        when: "an unchecked exception"
        unchecked.invoke(bean)

        then: "the exception of the method, not the InvocationException a reflective invocation wraps it in"
        def raised = thrown(IllegalStateException)
        raised.message == "unchecked"

        when: "a checked exception, which a generated dispatcher lets through as it is as well"
        def escaped = raise { checked.invoke(bean) }

        then:
        escaped instanceof IOException
        escaped.message == "checked"
    }

    void "two executable methods over one method are equal, the way the generated ones are"() {
        given:
        def method = Plain.getMethod("shout", String)
        def executable = ReflectionExecutableMethod.of(method)

        expect: "the declaring type, the name and the erased argument types decide, as in AbstractExecutableMethod"
        executable == ReflectionExecutableMethod.of(method)
        executable.hashCode() == ReflectionExecutableMethod.of(method).hashCode()
        executable != ReflectionExecutableMethod.of(ExecThrowing.getMethod("unchecked"))

        and: "the method it invokes is the method it reports as its target"
        executable.method.is(executable.targetMethod)
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
        constructor instanceof ReflectionBeanConstructor
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

    /**
     * The exception an invocation lets through, a dynamic Groovy call site wrapping a checked one of a
     * reflective invocation unwrapped.
     */
    private static Throwable raise(Closure<?> invocation) {
        try {
            invocation.call()
        } catch (GroovyRuntimeException e) {
            return e.cause == null ? e : e.cause
        } catch (Throwable e) {
            return e
        }
        throw new AssertionError("The invocation threw nothing" as Object)
    }

    static class Plain {
        String shout(String value) {
            value.toUpperCase()
        }
    }

    static class ExecBase<T> {
        List<String> saved = []

        @Executable
        void save(List<T> items) {
            saved << "many:${items.size()}".toString()
        }

        @Executable
        void save(T item) {
            saved << "one:$item".toString()
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ReflectionExecutablesSpec")
    static class ExecStore extends ExecBase<String> {
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
