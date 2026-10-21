package io.micronaut.inject.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import spock.lang.Unroll

class RetainableSpec extends AbstractBeanDefinitionSpec {

    @Unroll
    void "a retainable composed annotation is attributed to the annotation that introduced it (#kind)"() {
        given:
        def annotationMetadata = buildTypeAnnotationMetadata('retainspec.Test', '''
package retainspec;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.inject.annotation.Limit;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Limit(min = 5)
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedA {
    @AliasFor(annotation = Limit.class, member = "min", applyDefault = true)
    int min() default 5
}

@Limit(max = 50)
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedB {
    @AliasFor(annotation = Limit.class, member = "max", applyDefault = true)
    int max() default 50
}

@ComposedA(min = 3)
@ComposedB(max = 9)
class Test {
}
''')
        if (kind == "written") {
            annotationMetadata = writeAndLoadMetadata('retainspec.Test', annotationMetadata)
        }

        expect: "the flat index keeps one Limit for both composing annotations"
        annotationMetadata.getAnnotationNamesByStereotype(Limit.name) as Set ==
                ["retainspec.ComposedA", "retainspec.ComposedB"] as Set
        annotationMetadata.getAnnotation(Limit.name) != null

        and: "the retained tree keeps each occurrence apart"
        limitOf(annotationMetadata.getAnnotation("retainspec.ComposedA")) == [[min: 3]]
        limitOf(annotationMetadata.getAnnotation("retainspec.ComposedB")) == [[max: 9]]

        where:
        kind << ["compiled", "written"]
    }

    /**
     * The cascade lives in the shared builder, but each language frontend resolves an annotation type by name
     * differently, and that resolution is what carries an override down the subtree.
     */
    void "a transitive override cascades to what the intermediate annotation composes"() {
        given:
        def annotationMetadata = buildTypeAnnotationMetadata('cascadespec.Test', '''
package cascadespec

import io.micronaut.context.annotation.AliasFor
import io.micronaut.inject.annotation.Limit
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Inner(min = 1)
@Retention(RetentionPolicy.RUNTIME)
@interface Outer {
    @AliasFor(annotationName = "cascadespec.Inner", member = "min", applyDefault = true)
    int shortest() default 1
}

@Limit(min = 2)
@Retention(RetentionPolicy.RUNTIME)
@interface Inner {
    @AliasFor(annotation = Limit.class, member = "min", applyDefault = true)
    int min() default 2
}

@Outer(shortest = 3)
class Test {
}
''')

        when:
        def inner = annotationMetadata.getAnnotation("cascadespec.Outer")
                .getStereotypes()
                .find { it.getAnnotationName() == "cascadespec.Inner" }

        then: "the override reaches the intermediate annotation"
        inner.getValues() == [min: 3]

        and: "and cascades to what that annotation composes"
        limitOf(inner) == [[min: 3]]

        and: "the flat index agrees"
        annotationMetadata.getAnnotation(Limit.name).getValues() == [min: 3]
    }

    @Unroll
    void "annotations nested in members and repeatable containers retain what they compose (#kind)"() {
        given:
        def source = """
package nestedretention

import io.micronaut.inject.annotation.RetainedBounded
import io.micronaut.inject.annotation.RetainedGroup
import io.micronaut.inject.annotation.RetainedHolder
import io.micronaut.inject.annotation.RetainedRange
import io.micronaut.inject.annotation.RetainedRangeList

class Test {
    @RetainedRange(from = 3)
    @RetainedRange(from = 3)
    @RetainedRange(from = 7)
    void repeated() {
    }

    @RetainedHolder(
        ranges = [@RetainedRange(from = 4), @RetainedRange(from = 4)],
        single = @RetainedBounded(min = 5),
        groups = @RetainedGroup([@RetainedRange(from = 6), @RetainedRange]),
        list = @RetainedRangeList(@RetainedRange(from = 8))
    )
    void members() {
    }
}
"""
        AnnotationMetadata repeated = buildMethodAnnotationMetadata('nestedretention.Test', source, 'repeated')
        AnnotationMetadata members = buildMethodAnnotationMetadata('nestedretention.Test', source, 'members')
        if (kind == 'written') {
            repeated = writeAndLoadMetadata('nestedretention.Test', repeated)
            members = writeAndLoadMetadata('nestedretention.Test', members)
        }
        AnnotationValue<?> holder = members.getAnnotation(RetainedHolder.name)

        expect: 'each occurrence in a repeatable container retains two levels of composition, aliases applied'
        repeated.getAnnotationValuesByName(RetainedRange.name).collect { tree(it) } ==
                [range(3), range(7)]

        and: 'annotations in member arrays, single members, nested arrays and nested containers retain the same tree, defaults included'
        holder.getAnnotations('ranges').collect { tree(it) } == [range(4), range(4)]
        tree(holder.getAnnotation('single').get()) == bounded(5)
        holder.getAnnotations('groups').collect { group -> group.getAnnotations('value').collect { tree(it) } } ==
                [[range(6), defaultRange()]]
        holder.getAnnotation('list').get().getAnnotations('value').collect { tree(it) } == [range(8)]

        and: 'the reserved member is not an attribute of a nested annotation'
        holder.getAnnotation('single').get().values == [min: 5]
        holder.getAnnotations('ranges')*.values == [[from: 4], [from: 4]]

        and: 'the flat view is unchanged'
        !members.hasStereotype(RetainedRange.name)
        !members.hasStereotype(Limit.name)

        where:
        kind << ['compiled', 'written']
    }

    private static Map<String, Object> range(int from) {
        return [name: 'RetainedRange', values: [from: from], stereotypes: [bounded(from)]]
    }

    private static Map<String, Object> defaultRange() {
        // the default carried down by the alias, the occurrence itself declares no value
        return [name: 'RetainedRange', values: [:], stereotypes: [bounded(2)]]
    }

    private static Map<String, Object> bounded(int min) {
        return [name: 'RetainedBounded', values: [min: min], stereotypes: [[name: 'Limit', values: [min: min], stereotypes: []]]]
    }

    /**
     * The composition each fixture annotation declares. An alias also introduces the annotations it reaches through
     * the composition as occurrences of their own, as it does for an annotation declared on an element; the tree
     * compared is the declared composition.
     */
    private static final Map<String, List<String>> COMPOSED = [RetainedRange: ['RetainedBounded'], RetainedBounded: ['Limit']]

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

    private static List<Map> limitOf(AnnotationValue<?> annotationValue) {
        annotationValue.getStereotypes()
                .findAll { it.getAnnotationName() == Limit.name }
                .collect { it.getValues() }
    }
}
