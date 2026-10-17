package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.AnnotationRemapper
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * An annotation remapper sees, on an occurrence of an annotation, the stereotypes of the annotation type: the
 * meta-annotations the type declares and the ones a visitor added to the type in the same compilation. The
 * occurrence is on an injection point, whose metadata is built when the bean is written, after the visitors ran.
 */
class RemapperStereotypesSpec extends AbstractKotlinCompilerSpec {

    void 'a remapper sees the declared and the added stereotypes of an annotation occurrence'() {
        given:
        RecordingRemapper.stereotypes.clear()

        when:
        def definition = buildBeanDefinition('remapstereo.Fruit', '''
package remapstereo

import jakarta.inject.Qualifier
import jakarta.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Ripe

@Retention(AnnotationRetention.RUNTIME)
annotation class Extra

class Tree

@Singleton
class Fruit {

    @jakarta.inject.Inject
    fun setTree(@Ripe tree: Tree) {
    }
}
''')

        then: 'every build of the occurrence saw the stereotypes'
        !RecordingRemapper.stereotypes.isEmpty()
        RecordingRemapper.stereotypes.every { it.contains('jakarta.inject.Qualifier') }
        RecordingRemapper.stereotypes.every { it.contains('remapstereo.Extra') }

        and: 'the injection point composes the added stereotype'
        definition.injectedMethods.size() == 1
        definition.injectedMethods[0].arguments[0].annotationMetadata.hasAnnotation('remapstereo.Ripe')
        definition.injectedMethods[0].arguments[0].annotationMetadata.hasStereotype('remapstereo.Extra')
    }

    static class RecordingRemapper implements AnnotationRemapper {

        static final List<List<String>> stereotypes = []

        @Override
        String getPackageName() {
            return 'remapstereo'
        }

        @Override
        List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
            if (annotation.annotationName == 'remapstereo.Ripe') {
                stereotypes.add(annotation.stereotypes == null ? null : annotation.stereotypes*.annotationName)
            }
            return [annotation]
        }
    }


    /**
     * Adds a stereotype to the annotation type through the class element of the type looked up by name, the way a
     * discovery phase marks an annotation interface. The type is mutated when the visitor starts, before any class
     * of the compilation is visited and before the metadata of the injection point is computed: KSP does not visit
     * annotation classes, and javac starts the isolating visitors before any visitor reads an element.
     */
    static class ExtraStereotypeVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        @Override
        void start(VisitorContext visitorContext) {
            visitorContext.getClassElement('remapstereo.Ripe').ifPresent { it.annotate('remapstereo.Extra') }
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
        }
    }
}
