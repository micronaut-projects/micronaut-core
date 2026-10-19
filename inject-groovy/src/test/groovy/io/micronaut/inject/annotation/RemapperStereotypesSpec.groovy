package io.micronaut.inject.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * An annotation remapper sees, on an occurrence of an annotation, the stereotypes of the annotation type: the
 * meta-annotations the type declares and the ones a visitor added to the type in the same compilation. The
 * occurrence is on an injection point, whose metadata is built when the bean is written, after the visitors ran.
 */
class RemapperStereotypesSpec extends AbstractBeanDefinitionSpec {

    void 'a remapper sees the declared and the added stereotypes of an annotation occurrence'() {
        given:
        RecordingRemapper.stereotypes.clear()

        when:
        def definition = buildBeanDefinition('remapstereo.Fruit', '''
package remapstereo

import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@interface Ripe {
}

@Retention(RetentionPolicy.RUNTIME)
@interface Extra {
}

class Tree {
}

@Singleton
class Fruit {

    @jakarta.inject.Inject
    void setTree(@Ripe Tree tree) {
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
     * Adds a stereotype to the annotation type while visiting it, the way a discovery phase marks an annotation
     * interface. It runs before the other visitors of the test suite, which visit every class and would compute
     * the metadata of the bean before the annotation type is mutated.
     */
    static class ExtraStereotypeVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        int getOrder() {
            return Integer.MAX_VALUE
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == 'remapstereo.Ripe') {
                element.annotate('remapstereo.Extra')
            }
        }
    }
}
