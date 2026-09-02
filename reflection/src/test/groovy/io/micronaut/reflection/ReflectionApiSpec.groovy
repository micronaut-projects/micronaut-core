package io.micronaut.reflection

import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.type.Argument
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import spock.lang.Specification

import java.util.function.Supplier

/**
 * The parts of the public API the other specs reach only through the framework.
 */
class ReflectionApiSpec extends Specification {

    def cleanup() {
        ReflectionIntrospectionPolicy.reset()
    }

    void "the annotations a repeatable container holds are read from it"() {
        given:
        def container = Tagged.getAnnotation(Tags)

        expect:
        ReflectionAnnotations.contained(container)*.annotationType() == [Tag, Tag]
        ReflectionAnnotations.contained(container)*.value() == ["ledger", "book"]

        and: "an annotation that holds nothing is not a container"
        ReflectionAnnotations.contained(RestrictedHolder.getAnnotation(Restricted)) == []
    }

    void "the type arguments a type gives to a super type are resolved"() {
        expect:
        ReflectionArguments.resolveGenericToArgument(Tagged, Comparable).typeParameters*.type == [Tagged]
        ReflectionArguments.resolveGenericToArgument(Book, Comparable) == null
    }

    void "an execution handle invokes the method on the bean it was made for"() {
        given:
        def context = ApplicationContext.run()
        def method = Book.getMethod("getTitle")
        def book = new Book("handled", 1)

        when: "the handle over the best metadata available"
        def handle = ReflectionExecutables.executionHandle(context, BeanIntrospector.SHARED, book, method)

        then:
        handle.invoke() == "handled"
        handle.declaringType == Book
        handle.methodName == "getTitle"
        handle.target.is(book)

        when: "the handle that reflects, whatever the context knows"
        def reflective = ReflectionExecutables.executionHandle(book, method)

        then:
        reflective.invoke() == "handled"
        reflective.executableMethod instanceof ReflectionExecutableMethod

        cleanup:
        context.close()
    }

    void "an executable method is resolved against the beans of a context"() {
        given:
        def context = ApplicationContext.run()
        def method = Book.getMethod("getTitle")

        expect:
        ReflectionExecutables.executableMethod(context, method).invoke(new Book("t", 1)) == "t"

        cleanup:
        context.close()
    }

    void "the introspector delegates what a reflective introspection cannot enumerate"() {
        given:
        def delegate = BeanIntrospector.SHARED
        def introspector = new ReflectionBeanIntrospector(delegate)

        expect: "the enumerations are the delegate's own: a reflective introspection cannot be discovered"
        introspector.findIntrospections { it.beanType == Shelf } == delegate.findIntrospections { it.beanType == Shelf }
        introspector.findIntrospectedTypes { true } == delegate.findIntrospectedTypes { true }

        and: "a type the delegate does not know is described all the same"
        introspector.findIntrospection(Book).get() instanceof ReflectionBeanIntrospection
    }

    void "the levels of an argument are merged, the local one winning"() {
        given:
        def local = ReflectionArguments.of(Book.getDeclaredField("tags"))
        def inherited = Argument.of(List, "tags", Argument.of(String))

        when:
        def merged = MethodHierarchy.mergeArgument([inherited, local])

        then:
        merged.type == List
        merged.name == "tags"
        merged.typeParameters[0].annotationMetadata.stringValue(Tag).get() == "elem"

        and: "one level alone is that level"
        MethodHierarchy.mergeArgument([local]).typeParameters[0].annotationMetadata.stringValue(Tag).get() == "elem"

        and: "the metadata of several levels is a hierarchy of them"
        MethodHierarchy.mergeMetadata([
                ReflectionAnnotations.declaring(Restricted, [level: 1]),
                ReflectionAnnotations.declaring(Portable)
        ]) instanceof AnnotationMetadataHierarchy
    }

    void "a reflective executable method reports what it is and invokes unsafely"() {
        given:
        def method = ReflectionExecutableMethod.of(Book.getMethod("setTitle", String))
        def book = new Book("before", 1)

        expect:
        method.argumentTypes == [String] as Class[]
        !method.suspend
        !method.abstract
        !method.hasPropertyExpressions()

        when: "invoked without the argument check the contract makes"
        method.invokeUnsafe(book, "after")

        then:
        book.title == "after"

        when: "configured for an environment, as a bean definition configures its methods"
        def context = ApplicationContext.run()
        method.configure(context.environment)

        then:
        method.annotationMetadata != null

        cleanup:
        context.close()
    }

    void "an introspected executable method invokes through the bean method"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(Book)
        def beanMethod = introspection.beanMethods.find { it.name == "getTitle" }
        def method = new IntrospectedExecutableMethod<>(Book, beanMethod, Book.getMethod("getTitle"))

        expect:
        method.invokeUnsafe(new Book("unsafe", 1)) == "unsafe"
        method.beanMethod.is(beanMethod)
        method.targetMethod == Book.getMethod("getTitle")
        method.argumentTypes.length == 0
    }

    void "the executable methods of a definition answer with the method they dispatch to"() {
        given:
        def definition = ReflectionBeanDefinition.builder(Book).executableMethods { it.name == "getTitle" }.build()
        def executable = definition.findMethod("getTitle").get()

        expect: "the definition dispatches by index, and reports the method at that index"
        executable.targetMethod == Book.getMethod("getTitle")
        executable.invoke(new Book("dispatched", 1)) == "dispatched"
        definition.executableMethods*.name == ["getTitle"]
    }

    void "the life cycle methods of another container are named rather than annotated"() {
        given:
        def context = ApplicationContext.run()
        context.registerBeanDefinition(
                ReflectionBeanDefinition.builder(io.micronaut.reflection.Named)
                        .singleton(true)
                        .postConstruct("open")
                        .preDestroy("close")
                        .build())

        when:
        def bean = context.getBean(io.micronaut.reflection.Named)

        then:
        bean.events == ["open"]

        when:
        context.close()

        then:
        bean.events == ["open", "close"]
    }

    void "the annotations of another container are added to the ones of the class, both declared"() {
        given: "a class that declares its own scope, and a container that means it to be primary"
        def definition = ReflectionBeanDefinition.builder(Warehouse)
                .additionalAnnotationMetadata(ReflectionAnnotations.declaring(io.micronaut.context.annotation.Primary))
                .build()

        expect: "the class is still read"
        definition.singleton
        definition.scope.get() == jakarta.inject.Singleton

        and: "and the added annotation counts as declared, which is how the framework reads it"
        definition.primary
        definition.annotationMetadata.hasDeclaredAnnotation(io.micronaut.context.annotation.Primary)

        and: "the metadata of two are merged the same way on their own"
        ReflectionAnnotations.merge(ReflectionAnnotations.declaring(Portable), ReflectionAnnotations.declaring(Restricted))
                .with { it.hasDeclaredAnnotation(Portable) && it.hasDeclaredAnnotation(Restricted) }
        ReflectionAnnotations.merge(ReflectionAnnotations.declaring(Portable), AnnotationMetadata.EMPTY_METADATA)
                .hasDeclaredAnnotation(Portable)
        ReflectionAnnotations.merge(AnnotationMetadata.EMPTY_METADATA, ReflectionAnnotations.declaring(Portable))
                .hasDeclaredAnnotation(Portable)
    }

    void "a record is instantiated by its canonical constructor, not by a wider one it declares"() {
        given:
        def definition = ReflectionBeanDefinition.of(Delegating)

        expect:
        definition.targetConstructor.parameterCount == 1
        definition.constructor.arguments*.name == ["label"]

        and: "the introspection selects it as well"
        ReflectionBeanIntrospection.of(Delegating).constructorArguments*.name == ["label"]
        ReflectionBeanIntrospection.of(Delegating).instantiate("only") == new Delegating("only")
    }

    void "an introspection carries the annotations the caller means it to have"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(Book, ReflectionAnnotations.declaring(Portable))

        expect: "the class is still read, and the added annotation is there"
        introspection.annotationMetadata.hasDeclaredAnnotation(Portable)
        introspection.beanProperties*.name.contains("title")

        and: "an introspection with nothing added is the class alone"
        !ReflectionBeanIntrospection.of(Book).annotationMetadata.hasDeclaredAnnotation(Portable)
    }

    void "a reflective bean definition reports the order of its class and is loaded by the context"() {
        given:
        def definition = ReflectionBeanDefinition.of(Ordered)
        def context = ApplicationContext.run()

        expect:
        definition.order == 42
        definition.load().is(definition)
        definition.load(context).is(definition)

        cleanup:
        context.close()
    }

    void "the life cycle methods of a reflective definition run when the context creates and destroys the bean"() {
        given:
        def context = ApplicationContext.run()
        def definition = ReflectionBeanDefinition.of(Lifecycle)
        context.registerBeanDefinition(definition)

        when:
        def bean = context.getBean(Lifecycle)

        then: "initialize ran"
        bean.events == ["start"]

        when: "the context is closed, which destroys the singleton"
        context.close()

        then: "dispose ran"
        bean.events == ["start", "stop"]
    }

    void "the configuration and the policy allow the same types"() {
        given:
        def configuration = new ReflectionIntrospectionConfiguration()

        when:
        configuration.allowReflection = ["io.micronaut.reflection.*"]
        ReflectionIntrospectionPolicy.configure(configuration.allowReflection)

        then:
        configuration.allowReflection == ["io.micronaut.reflection.*"]
        ReflectionIntrospectionPolicy.isAllowed(Book)
        new ReflectionBeanIntrospectionFallback().findIntrospection(Book).present

        when: "the configuration is cleared, as it is when the context stops"
        configuration.allowReflection = null
        ReflectionIntrospectionPolicy.configure(configuration.allowReflection)

        then:
        configuration.allowReflection == []
        !ReflectionIntrospectionPolicy.isAllowed(Book)
        new ReflectionBeanIntrospectionFallback().findIntrospection(Book).empty
    }

    void "the values of an annotation register the defaults of its type"() {
        when: "the values are read, a member equal to its default among them"
        def values = ReflectionAnnotations.values(RestrictedHolder.getAnnotation(Restricted))

        then: "the member is left out, as the processors leave it out"
        values.keySet() == ["level", "name"].toSet()

        when: "an annotation written bare"
        def bare = ReflectionAnnotations.values(Defaulted.getAnnotation(Restricted))

        then: "nothing is stored, and the defaults of the type are registered for the accessors to serve"
        bare.isEmpty()
        new io.micronaut.inject.annotation.MutableAnnotationMetadata().getDefaultValues(Restricted.name).get("name") == "unnamed"
    }

    @Restricted
    static class Defaulted {
    }
}
