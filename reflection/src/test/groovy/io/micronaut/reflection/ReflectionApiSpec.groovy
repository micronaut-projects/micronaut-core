package io.micronaut.reflection

import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.type.Argument
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import spock.lang.Specification

import java.lang.annotation.Annotation
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
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
        def registration = ReflectionIntrospectionPolicy.configure(configuration.allowReflection)

        then:
        configuration.allowReflection == ["io.micronaut.reflection.*"]
        ReflectionIntrospectionPolicy.isAllowed(Book)
        new ReflectionBeanIntrospectionFallback().findIntrospection(Book).present

        when: "the contribution is withdrawn, as it is when the context that made it stops"
        registration.close()

        then:
        !ReflectionIntrospectionPolicy.isAllowed(Book)
        new ReflectionBeanIntrospectionFallback().findIntrospection(Book).empty

        when: "a configuration is answered no pattern at all"
        configuration.allowReflection = null

        then: "it holds none, so it contributes none"
        configuration.allowReflection == []
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
        new MutableAnnotationMetadata().getDefaultValues(Restricted.name).get("name") == "unnamed"

        when: "an annotation whose class, enum and nested annotation members are left at their defaults"
        def converted = ReflectionAnnotations.values(Book.getAnnotation(Tag))

        then: "each is compared with its default in the converted form, so each is left out too"
        converted.keySet() == ["value"].toSet()
        converted.get("value") == "entity"
    }

    void "the values of the first metadata merged win where both declare the same annotation"() {
        given:
        def first = ReflectionAnnotations.declaring(Restricted, [name: "first"])
        def second = ReflectionAnnotations.declaring(Restricted, [name: "second", level: 9])

        when:
        def merged = ReflectionAnnotations.merge(first, second)

        then: "the member both carry is the one of the first, as the contract says"
        merged.stringValue(Restricted, "name").get() == "first"

        and: "a member only the second carries is added, and both count as declared"
        merged.intValue(Restricted, "level").get() == 9
        merged.hasDeclaredAnnotation(Restricted)
    }

    void "an inherited annotation an interface of the hierarchy declares is present but not declared"() {
        when: "an interface the class implements directly"
        def direct = ReflectionAnnotations.metadataOf(AnnHierarchy.Direct)

        then: "the annotation is there, inherited rather than declared"
        direct.hasAnnotation(AnnInherited)
        !direct.hasDeclaredAnnotation(AnnInherited)
        direct.stringValue(AnnInherited).get() == "direct"

        and: "an annotation that is not meta-annotated @Inherited stays on the interface"
        !direct.hasAnnotation(AnnNotInherited)

        when: "a super interface of the interface the class implements"
        def deep = ReflectionAnnotations.metadataOf(AnnHierarchy.Deep)

        then:
        deep.stringValue(AnnInherited).get() == "super"

        when: "an interface a super class implements"
        def sub = ReflectionAnnotations.metadataOf(AnnHierarchy.Sub)

        then:
        sub.stringValue(AnnInherited).get() == "base"

        when: "the class declares the annotation the interface it implements declares too"
        def declared = ReflectionAnnotations.metadataOf(AnnHierarchy.Declared)

        then: "the declared one wins and the inherited one does not duplicate it"
        declared.hasDeclaredAnnotation(AnnInherited)
        declared.stringValue(AnnInherited).get() == "declared"
    }

    void "an annotation of an element whose member cannot be read is skipped rather than losing the element"() {
        given: "an annotation that throws when its member is read, as one naming an absent class does"
        def broken = (AnnBroken) Proxy.newProxyInstance(
                AnnBroken.classLoader,
                [AnnBroken] as Class[],
                new InvocationHandler() {
                    Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.name) {
                            case "annotationType":
                                return AnnBroken
                            case "toString":
                                return "@AnnBroken"
                            case "hashCode":
                                return 0
                            case "equals":
                                return proxy.is(args[0])
                            default:
                                throw new TypeNotPresentException("io.micronaut.reflection.AnnAbsent", null)
                        }
                    }
                })
        def element = new AnnotatedElement() {
            Annotation getAnnotation(Class annotationClass) {
                getDeclaredAnnotations().find { annotationClass.isInstance(it) }
            }

            Annotation[] getAnnotations() {
                getDeclaredAnnotations()
            }

            Annotation[] getDeclaredAnnotations() {
                [broken, Tagged.getAnnotation(Tags)] as Annotation[]
            }
        }

        when:
        def metadata = new MutableAnnotationMetadata()
        ReflectionAnnotations.add(metadata, element)

        then: "the annotation that cannot be read is left out, and the others of the element are read"
        !metadata.hasAnnotation(AnnBroken)
        metadata.getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["ledger", "book"]

        when: "the caller asks for that one annotation instead"
        ReflectionAnnotations.values(broken)

        then: "it is told, since there is nothing else to give it"
        thrown(IllegalStateException)
    }

    void "a nested annotation is recorded the way the annotation holding it is"() {
        when: "an annotation whose members hold annotations, one of them an array of them"
        def values = ReflectionAnnotations.values(AnnNesting.Holder.getAnnotation(AnnNesting.Outer))

        then: "the member of the nested annotation left at its default is left out, as one of the outer is"
        values.get("nested").stringValue().get() == "written"
        values.get("nested").getValues().keySet() == ["value", AnnotationUtil.NON_BINDING_ATTRIBUTE].toSet()

        and: "and its non binding members are recorded, which the shared conversion does not do either"
        values.get("nested").get(AnnotationUtil.NON_BINDING_ATTRIBUTE, String[]).get() ==
                ["comment", AnnotationUtil.NON_BINDING_ATTRIBUTE] as String[]

        and: "a member holding an array of annotations is walked element by element"
        values.get("several")*.stringValue()*.get() == ["first", "second"]
        values.get("several")[0].getValues().keySet() == ["value", AnnotationUtil.NON_BINDING_ATTRIBUTE].toSet()
        values.get("several")[1].stringValue("comment").get() == "said"
    }

    void "the defaults of a nested annotation are registered, as the defaults of the outer one are"() {
        when: "an annotation holding one of a type nothing else has converted"
        def values = ReflectionAnnotations.values(AnnNesting.RegisteredHolder.getAnnotation(AnnNesting.Outer))

        then: "the member equal to its default is left out of the nested values"
        values.get("registered").getValues().keySet() == ["value"].toSet()

        and: "and the defaults of the nested type are registered, as the defaults of a type on an element are"
        new MutableAnnotationMetadata().getDefaultValues(AnnNesting.Registered.name).get("level") == 7

        and: "so an accessor of the nested value serves the member it leaves out"
        values.get("registered").get("level", Integer).get() == 7
    }

    void "a customizer runs for a nested annotation as it does for one of an element"() {
        when: "an annotation holding the annotation the registered customizer supports"
        def values = ReflectionAnnotations.values(AnnNesting.CustomizedHolder.getAnnotation(AnnNesting.Outer))

        then: "the customizer derived the member of the nested annotation too"
        values.get("customized").stringValue().get() == "inner"
        values.get("customized").stringValue("derived").get() == "from-inner"
    }

    void "an array member value is a copy, so mutating it does not reach the annotation"() {
        given: "an annotation implemented by hand, which answers the same array every time where the proxy the compiler builds clones it"
        def shared = ["a", "b"] as String[]
        def annotation = (AnnArrays) Proxy.newProxyInstance(
                AnnArrays.classLoader,
                [AnnArrays] as Class[],
                new InvocationHandler() {
                    Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.name) {
                            case "annotationType":
                                return AnnArrays
                            case "labels":
                                return shared
                            case "toString":
                                return "@AnnArrays"
                            case "hashCode":
                                return 0
                            case "equals":
                                return proxy.is(args[0])
                            default:
                                return null
                        }
                    }
                })

        when: "the caller mutates the array it was handed"
        def values = ReflectionAnnotations.values(annotation)
        values.get("labels")[0] = "mutated"

        then: "the array of the annotation is untouched, and a second read gives what the first one gave"
        shared == ["a", "b"] as String[]
        ReflectionAnnotations.values(annotation).get("labels") == ["a", "b"] as String[]
    }

    @Restricted
    static class Defaulted {
    }
}
