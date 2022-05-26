package io.micronaut.inject.annotation.repeatable

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.NonNull
import io.micronaut.inject.annotation.AnnotationTransformer
import io.micronaut.inject.annotation.NamedAnnotationTransformer
import io.micronaut.inject.visitor.VisitorContext

import java.lang.annotation.Annotation

class TransformToRepeatableSpec extends AbstractTypeElementSpec {
    void 'test map single repeatable'() {
        given:
        def annotationMetadata = buildTypeAnnotationMetadata('''
package test;

import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.*;

@MapSingleRepeatable
@jakarta.inject.Singleton
class MapSingle {

}

@Retention(RUNTIME)
@interface MapSingleRepeatable {}
''')
        expect:
        annotationMetadata != null
        annotationMetadata.hasAnnotation(Requirements)
    }

    @Override
    protected List<AnnotationTransformer<? extends Annotation>> getLocalAnnotationTransformers(@NonNull String annotationName) {
        switch (annotationName) {
            case "test.MapSingleRepeatable":
                return [new SingleRepeatableTransformer()]
            default:
                return super.getLocalAnnotationTransformers(annotationName)
        }
    }

    static class SingleRepeatableTransformer implements NamedAnnotationTransformer {

        @Override
        String getName() {
            return "test.MapSingleRepeatable"
        }

        @Override
        List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
            return [
                    AnnotationValue.builder(Requires).member("property", "foo.bar").build()
            ]
        }
    }
}
