package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.AliasFor
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.core.annotation.Retainable
import io.micronaut.core.beans.BeanIntrospection
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
import java.lang.annotation.Repeatable;
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
        !minimumLength.getStereotypes()*.annotationName.contains(Retainable.name)
        !maximumLength.getStereotypes()*.annotationName.contains(Retainable.name)

        cleanup:
        this.strategy = null

        where:
        strategy << ['remapper', 'transformer']
    }

    @Unroll
    void "annotations nested in members and repeatable containers retain what they compose (#kind)"() {
        given:
        def source = '''
package nestedretention;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.Retainable;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.CLASS;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

class Test {
    @Range(from = 3)
    @Range(from = 3)
    @Range(from = 7)
    void repeated() {
    }

    @Holder(
        ranges = {@Range(from = 4), @Range(from = 4)},
        single = @Bounded(min = 5),
        groups = @Group({@Range(from = 6), @Range}),
        list = @Range.List(@Range(from = 8))
    )
    void members() {
    }

    @Span(to = 10)
    void spanned() {
    }
}

@Retainable
@Retention(RUNTIME)
@interface Limit {
    int min() default 0;

    int max() default 100;
}

@Retainable
@Retention(CLASS)
@interface Hidden {
}

@Limit(min = 1)
@Hidden
@Retention(RUNTIME)
@Repeatable(Bounded.List.class)
@interface Bounded {
    @AliasFor(annotation = Limit.class, member = "min", applyDefault = true)
    int min() default 1;

    @Retention(RUNTIME)
    @interface List {
        Bounded[] value();
    }
}

@Bounded(min = 2)
@Retention(RUNTIME)
@Repeatable(Range.List.class)
@interface Range {
    @AliasFor(annotation = Bounded.class, member = "min", applyDefault = true)
    int from() default 2;

    @Retention(RUNTIME)
    @interface List {
        Range[] value();
    }
}

@Range(from = 9)
@Retention(RUNTIME)
@interface Span {
    @AliasFor(annotation = Range.class, member = "from", applyDefault = true)
    int to() default 9;
}

@Retention(RUNTIME)
@interface Group {
    Range[] value();
}

@Retention(RUNTIME)
@interface Holder {
    Range[] ranges() default {};

    Bounded single();

    Group[] groups() default {};

    Range.List list();
}
'''
        AnnotationMetadata repeated = buildMethodAnnotationMetadata(source, 'repeated')
        AnnotationMetadata members = buildMethodAnnotationMetadata(source, 'members')
        AnnotationMetadata spanned = buildMethodAnnotationMetadata(source, 'spanned')
        boolean written = kind == 'written'
        if (written) {
            repeated = writeAndLoadMetadata('nestedretention.Test', repeated)
            members = writeAndLoadMetadata('nestedretention.Test', members)
            spanned = writeAndLoadMetadata('nestedretention.Test', spanned)
        }
        AnnotationValue<?> holder = members.getAnnotation('nestedretention.Holder')


        expect: 'each occurrence in a repeatable container retains two levels of composition, aliases applied'
        // equal repeated occurrences are stored once, as before; an array member keeps both
        repeated.getAnnotationValuesByName('nestedretention.Range').collect { tree(it) } ==
                [range(3, written), range(7, written)]

        and: 'annotations in member arrays, single members, nested arrays and nested containers retain the same tree, defaults included'
        holder.getAnnotations('ranges').collect { tree(it) } == [range(4, written), range(4, written)]
        tree(holder.getAnnotation('single').get()) == bounded(5, written)
        holder.getAnnotations('groups').collect { group -> group.getAnnotations('value').collect { tree(it) } } ==
                [[range(6, written), defaultRange(written)]]
        holder.getAnnotation('list').get().getAnnotations('value').collect { tree(it) } == [range(8, written)]

        and: 'the reserved member is not an attribute of a nested annotation'
        holder.getAnnotation('single').get().values == [min: 5]
        holder.getAnnotations('ranges')*.values == [[from: 4], [from: 4]]

        and: 'an override through three levels of aliases reaches the leaf through the composed annotations'
        tree(spanned.getAnnotation('nestedretention.Span')) ==
                [name: 'Span', values: [to: 10], stereotypes: [range(10, written)]]

        and: 'the flat view is unchanged'
        repeated.getAnnotationValuesByName('nestedretention.Range')*.values == [[from: 3], [from: 7]]
        !members.hasStereotype('nestedretention.Range')
        !members.hasStereotype('nestedretention.Limit')

        where:
        kind << ['compiled', 'written']
    }

    @Unroll
    void "constraints in a container retain what they compose (#kind)"() {
        given:
        this.strategy = 'remapper'
        AnnotationMetadata metadata = buildMethodArgumentAnnotationMetadata('''
package containerretention;

import jakarta.validation.Constraint;
import jakarta.validation.OverridesAttribute;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

class Test {
    void register(@MinimumLength(min = 4) @MinimumLength(min = 6) @Bounds String username) {
    }
}

@Constraint(validatedBy = {})
@Size(min = 5)
@Retention(RUNTIME)
@Repeatable(MinimumLength.List.class)
@interface MinimumLength {
    String message() default "too short";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    @OverridesAttribute(constraint = Size.class, name = "min")
    int min() default 3;

    @Retention(RUNTIME)
    @interface List {
        MinimumLength[] value();
    }
}

@Constraint(validatedBy = {})
@Size.List({@Size(min = 1), @Size(max = 5)})
@Retention(RUNTIME)
@interface Bounds {
    String message() default "out of bounds";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
''', 'register', 'username')
        if (kind == 'written') {
            metadata = writeAndLoadMetadata('containerretention.Test', metadata)
        }
        List<AnnotationValue<?>> minimumLengths = metadata.getAnnotationValuesByName('containerretention.MinimumLength')
        AnnotationValue<?> bounds = metadata.getAnnotation('containerretention.Bounds')

        expect: 'each constraint in the container retains its constraint marker and its overridden composition'
        minimumLengths*.values == [[min: 4], [min: 6]]
        minimumLengths.collect { stereotypesNamed(it, 'jakarta.validation.constraints.Size')*.values } == [[[min: 4]], [[min: 6]]]
        minimumLengths.every { it.getStereotypes()*.annotationName.contains(Constraint.name) }

        and: 'constraints a constraint composes through a container retain their own constraint marker'
        stereotypesNamed(bounds, 'jakarta.validation.constraints.Size')*.values == [[min: 1], [max: 5]]
        stereotypesNamed(bounds, 'jakarta.validation.constraints.Size').every {
            it.getStereotypes()*.annotationName.contains(Constraint.name)
        }

        cleanup:
        this.strategy = null

        where:
        kind << ['compiled', 'written']
    }

    void "annotations in a container that is not repeatable retain what they compose when a remapper set their stereotypes"() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('retainedcontainer.Test$Holder', '''
package retainedcontainer;

import io.micronaut.core.annotation.Introspected;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

class Test {
    @Retention(RUNTIME)
    @Target(FIELD)
    @Contract
    @Required
    @Single
    @interface Bundled {
        String message() default "bundle";

        @Retention(RUNTIME)
        @Target(FIELD)
        @interface List {
            Bundled[] value();
        }
    }

    @Retention(RUNTIME)
    @Target(FIELD)
    @Contract
    @Required
    @Single
    @Repeatable(Repeated.List.class)
    @interface Repeated {
        String message() default "repeated";

        @Retention(RUNTIME)
        @Target(FIELD)
        @interface List {
            Repeated[] value();
        }
    }

    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    static class Holder {
        @Bundled.List({@Bundled(message = "first"), @Bundled(message = "second")})
        public String value;

        @Repeated(message = "first")
        @Repeated(message = "second")
        public String repeated;
    }
}

@Retention(RUNTIME)
@Target(ANNOTATION_TYPE)
@interface Contract {
}

@Retention(RUNTIME)
@Target(ANNOTATION_TYPE)
@interface Single {
}

@Retention(RUNTIME)
@Target({ FIELD, ANNOTATION_TYPE })
@Contract
@interface Required {
}
''')
        List<AnnotationValue<?>> bundled = introspection.getRequiredProperty('value', String)
                .getAnnotation('retainedcontainer.Test$Bundled$List')
                .getAnnotations('value')
        List<AnnotationValue<?>> repeated = introspection.getRequiredProperty('repeated', String)
                .getAnnotationValuesByName('retainedcontainer.Test$Repeated')

        expect: 'each occurrence read back from the introspection keeps its own values'
        bundled*.values == [[message: 'first'], [message: 'second']]
        repeated*.values == [[message: 'first'], [message: 'second']]

        and: 'each occurrence retains the contract, the marker and the composed annotation the remapper set, as in a repeatable container'
        (bundled + repeated).collect { it.getStereotypes()*.annotationName.toSet() } == [
                ['retainedcontainer.Contract', 'retainedcontainer.Single', 'retainedcontainer.Required'] as Set
        ] * 4

        and: 'the composed annotation retains its own contract'
        (bundled + repeated).every {
            stereotypesNamed(it, 'retainedcontainer.Required')*.getStereotypes()*.annotationName == [['retainedcontainer.Contract']]
        }

        and: 'the marker is not retained'
        (bundled + repeated).every { !it.getStereotypes()*.annotationName.contains(Retainable.name) }
    }

    /**
     * Marks the contract and the marker retainable, and sets the contract, the marker and the annotations carrying
     * the contract as the stereotypes of each annotation held in a container, before the metadata builder sees it,
     * as the validation integration does for constraints.
     */
    static class ContainerContractRemapper implements AnnotationRemapper {

        @Override
        String getPackageName() {
            return 'retainedcontainer'
        }

        @Override
        List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
            if (annotation.annotationName in ['retainedcontainer.Contract', 'retainedcontainer.Single']) {
                return [makeRetainable(annotation)]
            }
            List<AnnotationValue<?>> contained = annotation.getAnnotations(AnnotationMetadata.VALUE_MEMBER)
            if (contained.isEmpty()) {
                return [annotation]
            }
            AnnotationValue<?>[] rewritten = contained.collect { value ->
                visitorContext.getClassElement(value.annotationName).map { type ->
                    AnnotationValue<?> contract = type.getAnnotationMetadata().getAnnotation('retainedcontainer.Contract')
                    if (contract == null) {
                        return value
                    }
                    def builder = value.mutate().stereotype(contract)
                    type.getAnnotationMetadata().findAnnotation('retainedcontainer.Single').ifPresent { builder.stereotype(it) }
                    for (String name : type.getAnnotationNamesByStereotype('retainedcontainer.Contract')) {
                        type.getAnnotationValuesByName(name).each { builder.stereotype(it) }
                    }
                    return builder.build()
                }.orElse(value)
            } as AnnotationValue<?>[]
            return [annotation.mutate().member(AnnotationMetadata.VALUE_MEMBER, rewritten).build()]
        }
    }

    private static Map<String, Object> range(int from, boolean written) {
        return [name: 'Range', values: [from: from], stereotypes: [bounded(from, written)]]
    }

    private static Map<String, Object> defaultRange(boolean written) {
        // the default carried down by the alias, the occurrence itself declares no value
        return [name: 'Range', values: [:], stereotypes: [bounded(2, written)]]
    }

    private static Map<String, Object> bounded(int min, boolean written) {
        List<Map<String, Object>> stereotypes = [[name: 'Limit', values: [min: min], stereotypes: []]]
        if (!written) {
            // A retained annotation that is not available at runtime is not written
            stereotypes << [name: 'Hidden', values: [:], stereotypes: []]
        }
        return [name: 'Bounded', values: [min: min], stereotypes: stereotypes]
    }

    /**
     * The composition each fixture annotation declares. An alias also introduces the annotations it reaches through
     * the composition as occurrences of their own, as it does for an annotation declared on an element; the tree
     * compared is the declared composition.
     */
    private static final Map<String, List<String>> COMPOSED = [Span: ['Range'], Range: ['Bounded'], Bounded: ['Limit', 'Hidden']]

    private static Map<String, Object> tree(AnnotationValue<?> annotation) {
        String name = annotation.annotationName.substring(annotation.annotationName.lastIndexOf('.') + 1)
        List<String> composed = COMPOSED[name] ?: []
        return [
                name       : name,
                values     : annotation.values,
                stereotypes: (annotation.getStereotypes() ?: [])
                        .findAll { composed.contains(it.annotationName.substring(it.annotationName.lastIndexOf('.') + 1)) }
                        .collect { tree(it) }
        ]
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
                .stereotype(AnnotationValue.builder(Retainable).build())
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
