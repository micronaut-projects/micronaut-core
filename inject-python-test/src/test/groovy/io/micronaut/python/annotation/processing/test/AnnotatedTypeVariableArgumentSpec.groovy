package io.micronaut.python.annotation.processing.test

import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement

/**
 * An {@code Annotated[...]} type argument whose type is a PEP 695 type parameter keeps the type variable,
 * with the annotation as a type-use annotation on it.
 */
class AnnotatedTypeVariableArgumentSpec extends AbstractPythonTypeElementSpec {

    void "test annotated type variable as a type argument"() {
        expect:
        buildClassElement('''
from typing import Annotated
from jakarta.validation.constraints import NotNull
from micronaut.context.annotation import Bean, Executable

@Bean
class Repo[E]:
    @Executable
    def items(self, entities: list[Annotated[E, NotNull()]]) -> list[E]:
        return None
''', 'Repo') { ClassElement element ->
            def method = element.findMethod('items').get()
            def entities = method.getParameters().find { it.name == 'entities' }.getGenericType()
            def typeArgument = entities.getTypeArguments().get('E')
            assert typeArgument instanceof GenericPlaceholderElement
            assert ((GenericPlaceholderElement) typeArgument).variableName == 'E'
            assert typeArgument.getTypeAnnotationMetadata().hasAnnotation('jakarta.validation.constraints.NotNull$List')

            def returnArgument = method.getGenericReturnType().getTypeArguments().get('E')
            assert returnArgument instanceof GenericPlaceholderElement
            assert ((GenericPlaceholderElement) returnArgument).variableName == 'E'
            assert returnArgument.getTypeAnnotationMetadata().isEmpty()
            true
        }
    }

    void "test annotated type variable argument metadata in the bean definition"() {
        when:
        BeanDefinition definition = buildBeanDefinition("python", "Repo", '''
from typing import Annotated
from jakarta.validation.constraints import NotNull
from micronaut.context.annotation import Bean, Executable

@Bean
class Repo[E]:
    @Executable
    def items(self, entities: list[Annotated[E, NotNull()]]) -> list[E]:
        return None
''')
        def entities = definition.getRequiredMethod("items", List).arguments[0]

        then:
        entities.typeParameters.length == 1
        entities.typeParameters[0].name == 'E'
        entities.typeParameters[0].annotationMetadata.hasAnnotation('jakarta.validation.constraints.NotNull$List')
    }
}
