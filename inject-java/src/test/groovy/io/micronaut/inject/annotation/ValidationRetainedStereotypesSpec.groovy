package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.AliasFor
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.core.annotation.RetainStereotypes
import io.micronaut.inject.visitor.VisitorContext
import jakarta.validation.Constraint
import jakarta.validation.OverridesAttribute
import org.jspecify.annotations.NonNull
import spock.lang.Unroll

import java.lang.annotation.Annotation

class ValidationRetainedStereotypesSpec extends AbstractTypeElementSpec {

    private String strategy

    @Unroll
    void "a validation integration can transform overrides and retain constraint composition using an annotation #strategy"() {
        given:
        this.strategy = strategy
        AnnotationMetadata metadata = buildMethodArgumentAnnotationMetadata('''
package validationretention;

import jakarta.validation.Constraint;
import jakarta.validation.OverridesAttribute;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

class Test {
    void register(@MinimumLength @MaximumLength String username) {
    }
}

@Documented
@Constraint(validatedBy = {})
@Size(min = 5)
@NotNull
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER, TYPE_USE, ANNOTATION_TYPE })
@interface MinimumLength {
    String message() default "too short";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    @OverridesAttribute.List({
        @OverridesAttribute(constraint = Size.class)
    })
    int min() default 3;
}

@Documented
@Constraint(validatedBy = {})
@Size(max = 50)
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER, TYPE_USE, ANNOTATION_TYPE })
@interface MaximumLength {
    String message() default "too long";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    @OverridesAttribute(constraint = Size.class, name = "max")
    int max() default 9;
}
''', 'register', 'username')
        metadata = writeAndLoadMetadata('validationretention.Test', metadata)

        when:
        AnnotationValue<?> minimumLength = metadata.getAnnotation('validationretention.MinimumLength')
        AnnotationValue<?> maximumLength = metadata.getAnnotation('validationretention.MaximumLength')

        then: 'each application constraint retains its own transformed composing constraint'
        stereotypesNamed(minimumLength, 'jakarta.validation.constraints.Size')*.values == [[min: 3]]
        stereotypesNamed(maximumLength, 'jakarta.validation.constraints.Size')*.values == [[max: 9]]

        and: 'plain composition is attributed to the constraint that declares it'
        stereotypesNamed(minimumLength, 'jakarta.validation.constraints.NotNull').size() == 1
        stereotypesNamed(maximumLength, 'jakarta.validation.constraints.NotNull').isEmpty()

        and: 'the flat view still exposes both effective values'
        metadata.getAnnotationValuesByName('jakarta.validation.constraints.Size')*.values == [[min: 3], [max: 9]]

        and: 'the validation marker that opted the family in is retained as useful provenance'
        minimumLength.getStereotypes()*.annotationName.contains(Constraint.name)
        maximumLength.getStereotypes()*.annotationName.contains(Constraint.name)

        and: 'the Micronaut implementation marker does not leak into the public tree'
        !minimumLength.getStereotypes()*.annotationName.contains(RetainStereotypes.name)
        !maximumLength.getStereotypes()*.annotationName.contains(RetainStereotypes.name)

        cleanup:
        this.strategy = null

        where:
        strategy << ['remapper', 'transformer']
    }

    @Override
    protected List<AnnotationRemapper> getLocalAnnotationRemappers(@NonNull String packageName) {
        if (strategy == 'remapper' && packageName == 'jakarta.validation') {
            return [new ConstraintRemapper()]
        }
        return super.getLocalAnnotationRemappers(packageName)
    }

    @Override
    protected List<AnnotationTransformer<? extends Annotation>> getLocalAnnotationTransformers(@NonNull String annotationName) {
        if (annotationName == OverridesAttribute.name) {
            return [new OverridesAttributeTransformer()]
        }
        if (strategy == 'transformer' && annotationName == Constraint.name) {
            return [new ConstraintTransformer()]
        }
        return super.getLocalAnnotationTransformers(annotationName)
    }

    private static List<AnnotationValue<?>> stereotypesNamed(AnnotationValue<?> annotation, String annotationName) {
        return annotation.getStereotypes().findAll { it.annotationName == annotationName }
    }

    private static AnnotationValue<?> makeRetainable(AnnotationValue<?> annotation) {
        return annotation.mutate()
                .stereotype(AnnotationValue.builder(RetainStereotypes).build())
                .build()
    }

    private static class ConstraintRemapper implements AnnotationRemapper {

        @Override
        String getPackageName() {
            return 'jakarta.validation'
        }

        @Override
        List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
            if (annotation.annotationName == Constraint.name) {
                return [makeRetainable(annotation)]
            }
            return [annotation]
        }
    }

    private static class ConstraintTransformer implements NamedAnnotationTransformer {

        @Override
        String getName() {
            return Constraint.name
        }

        @Override
        List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
            return [makeRetainable(annotation)]
        }
    }

    private static class OverridesAttributeTransformer implements NamedAnnotationTransformer {

        @Override
        String getName() {
            return OverridesAttribute.name
        }

        @Override
        List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
            AnnotationValueBuilder<AliasFor> aliasFor = AnnotationValue.builder(AliasFor)
            annotation.annotationClassValue('constraint').ifPresent {
                aliasFor.member('annotationName', it.name)
            }
            annotation.stringValue('name').ifPresent {
                aliasFor.member('member', it)
            }
            annotation.intValue('constraintIndex').ifPresent {
                aliasFor.member('index', it)
            }
            aliasFor.member('applyDefault', true)
            return [aliasFor.build()]
        }
    }
}
