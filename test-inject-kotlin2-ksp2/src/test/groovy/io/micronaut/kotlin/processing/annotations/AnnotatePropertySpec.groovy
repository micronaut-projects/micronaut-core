package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import jakarta.annotation.Nonnull
import jakarta.annotation.Nullable

class AnnotatePropertySpec extends AbstractKotlinCompilerSpec {

    void 'test annotating 1'() {
        when:
            def introspection = buildBeanIntrospection('annotateprop.AnnotatePropertyBean1', '''
package annotateprop;

import io.micronaut.core.annotation.Introspected


@Introspected
class AnnotatePropertyBean1(val firstName: String, val lastName: String?)

''')
        then:
            validate(introspection)
    }

    void 'test annotating 2'() {
        when:
            def introspection = buildBeanIntrospection('annotateprop.AnnotatePropertyBean2', '''
package annotateprop;

import io.micronaut.core.annotation.Introspected


@Introspected
class AnnotatePropertyBean2(var firstName: String, var lastName: String?)

''')
        then:
            validate(introspection)
    }

    void validate(BeanIntrospection introspection) {
        def property1 = introspection.getRequiredProperty("firstName", String)

        assert property1.name == "firstName"
        assert property1.hasAnnotation(MyAnnotation)
        assert property1.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation)
        def property2 = introspection.getRequiredProperty("lastName", String)

        assert property2.name == "lastName"
        assert !property2.hasAnnotation(MyAnnotation)
        assert !property2.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation)
    }

    static class AnnotatePropertyVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement classElement, VisitorContext context) {
            if (classElement.getSimpleName().startsWith("AnnotatePropertyBean")) {

                def properties = classElement.getBeanProperties()
                assert properties.size() == 2

                def property1 = properties[0]
                assert property1.name == "firstName"

                // Set comparison
                assert property1.getAnnotationMetadata().getAnnotationNames() == [Nonnull.class.name] as Set<String>
                assert property1.type.getAnnotationMetadata().getAnnotationNames().isEmpty()
                assert property1.genericType.getAnnotationMetadata().getAnnotationNames().isEmpty()

                property1.annotate(MyAnnotation)

                assert property1.getAnnotationMetadata().getAnnotationNames() == [MyAnnotation.class.name, Nonnull.class.name] as Set<String>
                assert property1.type.getAnnotationMetadata().getAnnotationNames().isEmpty()
                assert property1.genericType.getAnnotationMetadata().getAnnotationNames().isEmpty()

                def field1 = property1.getField().orElse(null)
                if (field1) {
                    assert field1.getAnnotationNames() == [MyAnnotation.class.name, Nonnull.class.name] as Set<String>
                    assert field1.type.getAnnotationMetadata().getAnnotationNames().isEmpty()
                    assert field1.genericType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                }
                def getter1 = property1.getReadMember().orElse(null) as MethodElement
                if (getter1) {
                    assert getter1.getMethodAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                    assert getter1.hasAnnotation(MyAnnotation.class.name)
                    assert getter1.returnType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                    assert getter1.genericReturnType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                }
                def setter1 = property1.getWriteMember().orElse(null) as MethodElement
                if (setter1) {
                    assert setter1.getMethodAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                    assert setter1.hasAnnotation(MyAnnotation.class.name)
                    assert setter1.returnType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                    assert setter1.genericReturnType.getAnnotationMetadata().getAnnotationNames().isEmpty()

                    def parameter = setter1.parameters[0]
                    // TODO: delegate to the parameter
//                    assert parameter.getAnnotationNames().asList() == [MyAnnotation.class.name]
                    assert parameter.getAnnotationNames() == [Nonnull.class.name] as Set<String>
                    assert parameter.type.getAnnotationMetadata().getAnnotationNames().isEmpty()
                    assert parameter.genericType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                }

                def property2 = properties[1]
                assert property2.name == "lastName"
                assert property2.getAnnotationMetadata().getAnnotationNames() == [Nullable.class.name] as Set<String>
                assert property2.type.getAnnotationMetadata().getAnnotationNames().isEmpty()
                assert property2.genericType.getAnnotationMetadata().getAnnotationNames().isEmpty()

                def field2 = property2.getField().orElse(null)
                if (field2) {
                    assert field2.getAnnotationMetadata().getAnnotationNames() == [Nullable.class.name] as Set<String>
                    assert field2.type.getAnnotationMetadata().getAnnotationNames().isEmpty()
                    assert field2.genericType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                }
                def getter2 = property2.getReadMember().orElse(null) as MethodElement
                if (getter2) {
                    assert getter2.getMethodAnnotationMetadata().getAnnotationNames().isEmpty()
                    assert getter2.returnType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                    assert getter2.genericReturnType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                }
                def setter2 = property2.getWriteMember().orElse(null) as MethodElement
                if (setter2) {
                    assert setter2.getMethodAnnotationMetadata().isEmpty()
                    assert setter2.returnType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                    assert setter2.genericReturnType.getAnnotationMetadata().getAnnotationNames().isEmpty()

                    def parameter = setter2.parameters[0]
                    assert parameter.getAnnotationNames() == [Nullable.class.name] as Set<String>
                    assert parameter.type.getAnnotationMetadata().getAnnotationNames().isEmpty()
                    assert parameter.genericType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                }

                // Validate the cache is working

                def newClassElement = context.getClassElement(classElement.getName()).get()
                def newProperty = newClassElement.getBeanProperties()[0]
                assert newProperty.getAnnotationMetadata().getAnnotationNames() == [Nonnull.class.name, MyAnnotation.class.name] as Set<String>
                assert newProperty.type.getAnnotationMetadata().getAnnotationNames().isEmpty()
                assert newProperty.genericType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                def field1new = newProperty.getField().orElse(null)
                if (field1new) {
                    assert field1new.getAnnotationMetadata().getAnnotationNames() == [Nonnull.class.name, MyAnnotation.class.name] as Set<String>
                    assert field1new.type.getAnnotationMetadata().getAnnotationNames().isEmpty()
                    assert field1new.genericType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                }
                def getter1new = newProperty.getReadMember().orElse(null) as MethodElement
                if (getter1new) {
                    assert getter1new.getMethodAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                    assert getter1new.hasAnnotation(MyAnnotation.class.name)
                    assert getter1new.returnType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                    assert getter1new.genericReturnType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                }
                def setter1new = newProperty.getWriteMember().orElse(null) as MethodElement
                if (setter1new) {
                    assert setter1new.getMethodAnnotationMetadata().getAnnotationNames().asList() == [MyAnnotation.class.name]
                    assert setter1new.hasAnnotation(MyAnnotation.class.name)
                    assert setter1new.returnType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                    assert setter1new.genericReturnType.getAnnotationMetadata().getAnnotationNames().isEmpty()

                    def parameter = setter1new.parameters[0]
                    // TODO: delegate to the parameter
//                    assert parameter.getAnnotationNames().asList() == [MyAnnotation.class.name]
                    assert parameter.getAnnotationNames() == [Nonnull.class.name] as Set<String>
                    assert parameter.type.getAnnotationMetadata().getAnnotationNames().isEmpty()
                    assert parameter.genericType.getAnnotationMetadata().getAnnotationNames().isEmpty()
                }
            }
        }
    }

}
