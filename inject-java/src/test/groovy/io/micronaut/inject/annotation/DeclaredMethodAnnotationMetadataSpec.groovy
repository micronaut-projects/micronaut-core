package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement

class DeclaredMethodAnnotationMetadataSpec extends AbstractTypeElementSpec {

    void "test the declared method annotations exclude the ones of the overridden method"() {
        given:
        ClassElement classElement = buildClassElement('''
package test;

import java.lang.annotation.*;

class Test implements Contract {
    @Override
    public void place(String name) {
    }
}

interface Contract {
    @MyAnn("interface")
    void place(@MyAnn("interface-param") String name);
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Repeatable(MyAnns.class)
@Inherited
@interface MyAnn {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Inherited
@interface MyAnns {
    MyAnn[] value();
}
''')
        MethodElement method = classElement.findMethod("place").get()
        ParameterElement parameter = method.parameters[0]

        expect: "the method annotations include the ones of the overridden method"
        method.methodAnnotationMetadata.getAnnotationValuesByName("test.MyAnn")
                .collect { it.stringValue().get() } == ["interface"]
        method.methodAnnotationMetadata.hasAnnotation("test.MyAnns")

        and: "the declared method annotations are the ones of this declaration, which declares nothing"
        method.declaredMethodAnnotationMetadata.getAnnotationValuesByName("test.MyAnn").isEmpty()
        !method.declaredMethodAnnotationMetadata.hasAnnotation("test.MyAnns")
        method.declaredMethodAnnotationMetadata.isEmpty()

        and: "the same applies to the parameters"
        parameter.annotationMetadata.getAnnotationValuesByName("test.MyAnn")
                .collect { it.stringValue().get() } == ["interface-param"]
        parameter.annotationMetadata.declaredMetadata.getAnnotationValuesByName("test.MyAnn").isEmpty()
    }

    void "test the declared method annotations retain the repeatable annotations of the declaration"() {
        given:
        ClassElement classElement = buildClassElement('''
package test;

import java.lang.annotation.*;

@MyAnn("class")
class Test implements Contract {
    @Override
    @MyAnn("impl-one")
    @MyAnn("impl-two")
    public void place(@MyAnn("impl-param") String name) {
    }
}

interface Contract {
    @MyAnn("interface")
    void place(@MyAnn("interface-param") String name);
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Repeatable(MyAnns.class)
@Inherited
@interface MyAnn {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Inherited
@interface MyAnns {
    MyAnn[] value();
}
''')
        MethodElement method = classElement.findMethod("place").get()
        ParameterElement parameter = method.parameters[0]

        expect: "the method annotations include the ones of the overridden method"
        method.methodAnnotationMetadata.getAnnotationValuesByName("test.MyAnn")
                .collect { it.stringValue().get() } as Set == ["impl-one", "impl-two", "interface"] as Set

        and: "the declared method annotations are the repeated ones of this declaration"
        method.declaredMethodAnnotationMetadata.getAnnotationValuesByName("test.MyAnn")
                .collect { it.stringValue().get() } == ["impl-one", "impl-two"]
        method.declaredMethodAnnotationMetadata.hasAnnotation("test.MyAnns")

        and: "the class annotations are not included"
        !method.declaredMethodAnnotationMetadata.getAnnotationValuesByName("test.MyAnn")
                .collect { it.stringValue().get() }.contains("class")

        and: "the same applies to the parameters"
        parameter.annotationMetadata.declaredMetadata.getAnnotationValuesByName("test.MyAnn")
                .collect { it.stringValue().get() } == ["impl-param"]
    }

    void "test the declared annotations of a generated introspection exclude the ones of the overridden method"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import java.lang.annotation.*;

@Introspected
@MyAnn("class")
class Test implements Contract {
    @Override
    @Executable
    @MyAnn("impl-one")
    @MyAnn("impl-two")
    public void place(String name) {
    }
}

interface Contract {
    @MyAnn("interface")
    void place(String name);
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Repeatable(MyAnns.class)
@Inherited
@interface MyAnn {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
@Inherited
@interface MyAnns {
    MyAnn[] value();
}
''')
        def method = introspection.getBeanMethods().first()
        // the first call narrows the class and the method metadata to the method metadata,
        // the second one to the annotations of the declaration
        def declared = method.annotationMetadata.declaredMetadata.declaredMetadata

        expect: "the annotations of the method include the ones of the overridden method"
        method.annotationMetadata.getAnnotationValuesByName("test.MyAnn")
                .collect { it.stringValue().get() } as Set == ["impl-one", "impl-two", "interface", "class"] as Set

        and: "the declared annotations are the repeated ones of the declaration"
        declared.getAnnotationValuesByName("test.MyAnn")
                .collect { it.stringValue().get() } == ["impl-one", "impl-two"]

        and: "narrowing an already declared only metadata changes nothing"
        declared.declaredMetadata.getAnnotationValuesByName("test.MyAnn")
                .collect { it.stringValue().get() } == ["impl-one", "impl-two"]
    }
}
