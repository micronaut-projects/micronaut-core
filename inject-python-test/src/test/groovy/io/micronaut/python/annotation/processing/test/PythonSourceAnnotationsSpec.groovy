package io.micronaut.python.annotation.processing.test

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.python.compiler.RepeatableAnnotation

import java.lang.annotation.RetentionPolicy

class PythonSourceAnnotationsSpec extends AbstractPythonTypeElementSpec {

    void "test the source view of a Python class, method, parameter and type use"() {
        expect:
        buildClassElement('''
from typing import Annotated
from jakarta.inject import Singleton
from micronaut.python.compiler import RepeatableAnnotation, PrimitiveTypesAnnotation, TestAnnotation

@RepeatableAnnotation("first")
@RepeatableAnnotation("second")
@Singleton
class Repeated:
    value: Annotated[str, TestAnnotation("f")]
    items: list[Annotated[str, TestAnnotation("e")]]

    @PrimitiveTypesAnnotation(intValue=7)
    def run(self, name: Annotated[str, TestAnnotation("p")], plain: str) -> Annotated[str, TestAnnotation("r")]:
        return name
''') { ClassElement element ->
            def source = element.getSourceAnnotations()
            assert source*.annotationName == [RepeatableAnnotation.name, RepeatableAnnotation.name, AnnotationUtil.SINGLETON]
            assert source[0].stringValue().get() == 'first'
            assert source[1].stringValue().get() == 'second'
            assert source.every { it.retentionPolicy == RetentionPolicy.RUNTIME }

            // the metadata folds the repetitions into the container and adds the scope stereotype
            assert element.getAnnotationMetadata().hasDeclaredStereotype(AnnotationUtil.SCOPE)
            assert element.getAnnotationValuesByType(RepeatableAnnotation).size() == 2
            assert !source.any { it.annotationName == AnnotationUtil.SCOPE }

            def method = element.findMethod('run').get()
            AnnotationValue<?> primitives = method.getSourceAnnotations().find { it.annotationName == 'io.micronaut.python.compiler.PrimitiveTypesAnnotation' }
            assert primitives != null
            assert primitives.intValue('intValue').getAsInt() == 7
            assert !primitives.getValues().containsKey('stringValue')
            assert primitives.getDefaultValues().containsKey('stringValue')
            assert method.getAnnotationMetadata().hasAnnotation(AnnotationUtil.SINGLETON)
            assert !method.getSourceAnnotations().any { it.annotationName == AnnotationUtil.SINGLETON }

            // Python attaches an Annotated[...] on a parameter or an attribute to that declaration, and to the
            // type use only inside a type argument
            def name = method.getParameters().find { it.name == 'name' }
            assert name.getSourceAnnotations()*.annotationName == ['io.micronaut.python.compiler.TestAnnotation']
            assert name.getSourceAnnotations()[0].stringValue().get() == 'p'
            assert name.getType().getTypeAnnotationMetadata().getSourceAnnotations().isEmpty()
            def value = element.getFields().find { it.name == 'value' }
            assert value.getSourceAnnotations()*.annotationName == ['io.micronaut.python.compiler.TestAnnotation']
            def itemsArgument = element.getFields().find { it.name == 'items' }.getGenericType().getFirstTypeArgument().get()
            assert itemsArgument.getTypeAnnotationMetadata().getSourceAnnotations()*.annotationName == ['io.micronaut.python.compiler.TestAnnotation']
            assert itemsArgument.getTypeAnnotationMetadata().getSourceAnnotations()[0].stringValue().get() == 'e'
            assert itemsArgument.getTypeAnnotationMetadata().getSourceAnnotations()[0].getDefaultValues().containsKey('value')
            assert method.getParameters().find { it.name == 'plain' }.getType().getTypeAnnotationMetadata().getSourceAnnotations().isEmpty()

            // what a visitor adds does not appear
            element.annotate('test.Added')
            assert element.getAnnotationMetadata().hasDeclaredAnnotation('test.Added')
            assert !element.getSourceAnnotations().any { it.annotationName == 'test.Added' }
            return element
        }
    }
}
