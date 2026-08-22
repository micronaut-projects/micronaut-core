package io.micronaut.inject.reflection

import io.micronaut.context.AnnotationReflectionUtils
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.inject.annotation.ReflectionAnnotationMetadataBuilder
import spock.lang.Specification

class ReflectionBridgeSpec extends Specification {

    void "the metadata of a class has its declared annotations, their stereotypes, the repeated ones and the inherited ones"() {
        when:
        def metadata = ReflectionAnnotationMetadataBuilder.build(Book)

        then: "the annotations composing another annotation are flattened next to the declared one, as the generated metadata does"
        metadata.getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["entity", "first", "second"]
        metadata.getAnnotationNamesByStereotype(Stereo).contains(Tag.name)

        and: "the meta-annotation of the annotation is a stereotype with its values"
        metadata.hasStereotype(Stereo)
        metadata.stringValue(Stereo, "kind").get() == "tag"

        and: "an annotation inherited from the super class is present but not declared"
        metadata.hasAnnotation(Shape)
        !metadata.hasDeclaredAnnotation(Shape)
        metadata.stringValue(Shape).get() == "base"

        and: "the annotations composing an annotation are its stereotypes, repeated ones included; a stereotype lists the annotation carrying it directly, as the generated metadata does"
        metadata.hasDeclaredAnnotation(Composed)
        metadata.getAnnotationNamesByStereotype(Tags).contains(Composed.name)
        metadata.getAnnotationNamesByStereotype(Stereo) == [Tag.name]

        and: "the defaults of the members are known"
        metadata.getDefaultValues(Tag.name).get("priority") == 1
        metadata.getDefaultValues(Tag.name).get("level") == "LOW"
    }

    void "a repeated annotation keeps every occurrence with its values and its defaults"() {
        when:
        def metadata = ReflectionAnnotationMetadataBuilder.build(Book.getDeclaredField("title"))
        def tags = metadata.getAnnotationValuesByType(Tag)

        then: "a member equal to its default is not a value, the defaults are carried separately, as the generated metadata does"
        tags*.stringValue()*.get() == ["title", "name"]
        !tags[0].values.containsKey("priority")
        tags[0].defaultValues.get("priority") == 1
        tags[1].intValue("priority").get() == 2
        !metadata.hasAnnotation(Shape)
    }

    void "class, enum and nested annotation members are stored as the generated metadata stores them"() {
        when:
        def metadata = ReflectionAnnotationMetadataBuilder.build(Book.getMethod("discount", double))
        def tag = metadata.getAnnotationValuesByType(Tag)[0]

        then: "a repeatable annotation written once is served as a repeated one, under its container"
        metadata.getAnnotationValuesByType(Tag).size() == 1

        and: "the members of a package-private annotation type are read"
        metadata.stringValue(Hidden).get() == "secret"
        tag.enumValue("level", Level).get() == Level.HIGH
        tag.classValue("type").get() == Number
        tag.getValues().get("type") instanceof AnnotationClassValue
        tag.getAnnotation("nested", Stereo).get().stringValue("kind").get() == "inner"
        tag.getValues().get("level") == "HIGH"
    }

    void "a member whose value is its default is served by the defaults, not stored"() {
        when:
        def metadata = ReflectionAnnotationMetadataBuilder.build(Book)
        def tag = metadata.getAnnotationValuesByType(Tag)[0]

        then:
        !tag.getValues().containsKey("priority")
        tag.defaultValues.get("priority") == 1
        tag.defaultValues.get("level") == "LOW"
        metadata.getDefaultValues(Tag.name).get("priority") == 1
    }

    void "the argument of a field carries the type-use annotations of its type arguments"() {
        when:
        def argument = AnnotationReflectionUtils.argumentOf(Book.getDeclaredField("tags"))

        then:
        argument.name == "tags"
        argument.type == List
        argument.typeParameters.length == 1
        argument.typeParameters[0].type == String
        argument.typeParameters[0].annotationMetadata.getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["elem"]
    }

    void "the arguments of a method carry the annotations of the parameters"() {
        when:
        def arguments = AnnotationReflectionUtils.argumentsOf(Book.getMethod("discount", double))

        then:
        arguments.length == 1
        arguments[0].type == double
        arguments[0].annotationMetadata.getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["pct"]
    }

    void "an executable method invokes the method and exposes its metadata"() {
        given:
        def method = Book.getMethod("discount", double)
        def executable = new ReflectionExecutableMethod<Book, Double>(Book, method)

        expect:
        executable.methodName == "discount"
        executable.declaringType == Book
        executable.targetMethod == method
        executable.returnType.type == double
        executable.arguments*.type == [double]
        executable.annotationMetadata.getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["disc"]
        executable.invoke(new Book("EL", 200), 10d) == 180d
    }

    void "a reflective introspection exposes the properties, the constructor and the methods"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(Book)
        def book = new Book("EL", 200)

        expect: "the properties of the type and of its super class, with their metadata"
        introspection.propertyNames.toList().containsAll(["title", "pages", "tags", "published", "baseName"])
        introspection.getRequiredProperty("title", String).get(book) == "EL"
        introspection.getRequiredProperty("title", String).annotationMetadata.getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["title", "name"]
        introspection.getRequiredProperty("pages", int).get(book) == 200
        introspection.getRequiredProperty("published", boolean).get(book) == false
        introspection.getRequiredProperty("baseName", String).get(book) == "base"

        and: "a property is written through its setter, a final field without a setter is read only"
        introspection.getRequiredProperty("title", String).set(book, "Expression")
        book.title == "Expression"
        introspection.getRequiredProperty("tags", List).readOnly
        introspection.getRequiredProperty("tags", List).asArgument().typeParameters[0].annotationMetadata.getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["elem"]

        and: "the type metadata"
        introspection.annotationMetadata.getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["entity", "first", "second"]
        introspection.hasStereotype(Stereo)

        and: "the constructor, with its metadata"
        introspection.constructorArguments*.type == [String, int]
        introspection.constructor.arguments*.type == [String, int]
        introspection.constructor.annotationMetadata.getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["ctor"]
        introspection.constructor.instantiate("Made", 4).pages == 4
        introspection.constructors*.arguments*.length == [2, 0]
        introspection.constructors[1].instantiate().pages == 0

        and: "an inherited method declares the class that declares it"
        introspection.beanMethods.find { it.name == "setBaseName" }.declaringType == Base
        introspection.beanMethods.find { it.name == "discount" }.declaringType == Book
        introspection.instantiate("Compiled", 3).title == "Compiled"
        introspection.builder().with(0, introspection.constructorArguments[0], "Built").with(1, introspection.constructorArguments[1], 7).build().pages == 7

        and: "the methods, without those of Object"
        introspection.beanMethods*.name.containsAll(["discount", "describe", "getTitle", "setBaseName"])
        !introspection.beanMethods*.name.contains("hashCode")
        introspection.beanMethods.find { it.name == "discount" }.invoke(book, 50d) == 100d
        introspection.beanMethods.find { it.name == "discount" }.annotationMetadata.getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["disc"]
    }

    void "the reflective introspector serves the generated introspections first and reflects only for the others"() {
        given:
        def introspector = new ReflectionBeanIntrospector(BeanIntrospector.SHARED)

        expect:
        !(introspector.findIntrospection(Generated).get() instanceof ReflectionBeanIntrospection)
        introspector.findIntrospection(Book).get() instanceof ReflectionBeanIntrospection
        introspector.findIntrospection(Book).get().is(introspector.findIntrospection(Book).get())
        introspector.findIntrospection(String).empty
        introspector.findIntrospection(Level).empty
        introspector.findIntrospection(Runnable).empty
        !new ReflectionBeanIntrospector(BeanIntrospector.SHARED, { false }).findIntrospection(Book).present
    }

    @Introspected
    static class Generated {
        String name
    }

    void "an executable method read reflectively carries the annotations of the method"() {
        when:
        def executable = new ReflectionExecutableMethod<>(Book, Book.getMethod("discount", double))

        then:
        executable.annotationMetadata.getAnnotationValuesByType(Tag).size() == 1
        executable.arguments.length == 1
    }

    void "an interface is introspected for its declarations and cannot be instantiated"() {
        when:
        def introspection = ReflectionBeanIntrospection.of(Shelf)

        then:
        introspection.findDeclaredMethod("getTitle").present
        introspection.findDeclaredMethod("getTitle").get().annotationMetadata.getAnnotationValuesByType(Tag).size() == 1
        introspection.findDeclaredMethod("missing").empty

        when:
        introspection.instantiate()

        then:
        thrown(io.micronaut.core.reflect.exception.InstantiationException)
    }
}
