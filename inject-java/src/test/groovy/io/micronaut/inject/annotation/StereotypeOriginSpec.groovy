package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import spock.lang.Unroll

/**
 * The association between a composing annotation occurrence and the annotation that introduced it is lost when
 * the annotation tree is flattened into the name-keyed stereotype indexes. {@code @RetainStereotypes} keeps it.
 */
class StereotypeOriginSpec extends AbstractTypeElementSpec {

    private static final String IMPORTS = '''
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.RetainStereotypes;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;
'''

    private static final String COMPOSED = '''
@RetainStereotypes
@Size(min = 5)
@NotNull
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedA {
    @AliasFor(annotation = Size.class, member = "min", applyDefault = true)
    int min() default 5;
}

@RetainStereotypes
@Size(max = 50)
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedB {
    @AliasFor(annotation = Size.class, member = "max", applyDefault = true)
    int max() default 50;
}
'''

    @Unroll
    void "the flat stereotype index cannot attribute repeatable occurrences (#kind)"() {
        given:
        def annotationMetadata = metadata(kind == "written")

        expect: "the overridden values are correct and kept apart"
        annotationMetadata.getAnnotationValuesByName("jakarta.validation.constraints.Size")
                .collect { it.getValues() } == [[min: 3], [max: 9]]

        and: "but the index is keyed by the repeatable container, and is a union over both occurrences"
        annotationMetadata.getAnnotationNamesByStereotype("jakarta.validation.constraints.Size") == []
        annotationMetadata.getAnnotationNamesByStereotype('jakarta.validation.constraints.Size$List') as Set ==
                ["originspec.ComposedA", "originspec.ComposedB"] as Set

        and: "a plainly composed annotation is attributed, also under its container"
        annotationMetadata.getAnnotationNamesByStereotype('jakarta.validation.constraints.NotNull$List') ==
                ["originspec.ComposedA"]

        where:
        kind << ["compiled", "written"]
    }

    @Unroll
    void "@RetainStereotypes attributes each occurrence to the annotation that introduced it (#kind)"() {
        given:
        def annotationMetadata = metadata(kind == "written")

        when:
        AnnotationValue<?> composedA = annotationMetadata.getAnnotation("originspec.ComposedA")
        AnnotationValue<?> composedB = annotationMetadata.getAnnotation("originspec.ComposedB")

        then: "ComposedA reports the Size occurrence it introduced, with its member override applied"
        sizeOf(composedA) == [[min: 3]]

        and: "ComposedB reports the other one"
        sizeOf(composedB) == [[max: 9]]

        and: "the marker itself is not reported as a composing annotation"
        composedA.getStereotypes()*.getAnnotationName().every { !it.startsWith("io.micronaut.core.annotation") }

        and: "the composing annotations that carry no override are reported too"
        composedA.getStereotypes()*.getAnnotationName().contains("jakarta.validation.constraints.NotNull")
        !composedB.getStereotypes()*.getAnnotationName().contains("jakarta.validation.constraints.NotNull")

        and: "a composing annotation that does not itself opt in stops the tree in the written metadata"
        kind != "written" || composedA.getStereotypes()
                .find { it.getAnnotationName() == "jakarta.validation.constraints.Size" }
                .getStereotypes() == null

        where:
        kind << ["compiled", "written"]
    }

    void "the retained tree nests; a transitive override does not cascade"() {
        given:
        def annotationMetadata = writeAndLoadMetadata('nestedspec.Test', buildTypeAnnotationMetadata('''
package nestedspec;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.RetainStereotypes;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;

@Outer(shortest = 3)
class Test {
}

@RetainStereotypes
@Inner(min = 1)
@Retention(RetentionPolicy.RUNTIME)
@interface Outer {
    @AliasFor(annotationName = "nestedspec.Inner", member = "min", applyDefault = true)
    int shortest() default 1;
}

@RetainStereotypes
@Size(min = 2)
@Retention(RetentionPolicy.RUNTIME)
@interface Inner {
    @AliasFor(annotation = Size.class, member = "min", applyDefault = true)
    int min() default 2;
}
'''))

        when:
        def outer = annotationMetadata.getAnnotation("nestedspec.Outer")
        def inner = outer.getStereotypes().find { it.getAnnotationName() == "nestedspec.Inner" }

        then: "the override reaches the intermediate annotation"
        inner.getValues() == [min: 3]

        and: "but it does not cascade to what the intermediate annotation composes, matching the flat index"
        inner.getStereotypes()
                .findAll { it.getAnnotationName() == "jakarta.validation.constraints.Size" }
                .collect { it.getValues() } == [[min: 1]]
        annotationMetadata.getAnnotationValuesByName("jakarta.validation.constraints.Size")
                .collect { it.getValues() } == [[min: 1]]
    }

    void "the marker opts in transitively, so a meta-annotation can opt a whole family in"() {
        given: "MyConstraint stands in for an annotation a transformer has added the marker to"
        def annotationMetadata = writeAndLoadMetadata('familyspec.Test', buildTypeAnnotationMetadata('''
package familyspec;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.RetainStereotypes;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;

@Composed(min = 3)
class Test {
}

@RetainStereotypes
@Retention(RetentionPolicy.RUNTIME)
@interface MyConstraint {
}

@MyConstraint
@Size(min = 5)
@Retention(RetentionPolicy.RUNTIME)
@interface Composed {
    @AliasFor(annotation = Size.class, member = "min", applyDefault = true)
    int min() default 5;
}
'''))

        expect: "Composed retains without declaring the marker itself"
        annotationMetadata.getAnnotation("familyspec.Composed").getStereotypes()
                .findAll { it.getAnnotationName() == "jakarta.validation.constraints.Size" }
                .collect { it.getValues() } == [[min: 3]]
    }

    void "annotations that do not opt in retain nothing"() {
        given:
        def annotationMetadata = writeAndLoadMetadata('plainspec.Test', buildTypeAnnotationMetadata('''
package plainspec;

import io.micronaut.context.annotation.AliasFor;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;

@Plain(min = 3)
class Test {
}

@Size(min = 5)
@Retention(RetentionPolicy.RUNTIME)
@interface Plain {
    @AliasFor(annotation = Size.class, member = "min", applyDefault = true)
    int min() default 5;
}
'''))

        expect:
        annotationMetadata.getAnnotation("plainspec.Plain").getStereotypes() == null
        annotationMetadata.getAnnotationValuesByName("jakarta.validation.constraints.Size")
                .collect { it.getValues() } == [[min: 3]]
    }

    void "a repeated composed annotation attributes each of its own occurrences"() {
        given:
        def annotationMetadata = writeAndLoadMetadata('repeatspec.Test', buildTypeAnnotationMetadata('''
package repeatspec;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.RetainStereotypes;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;

@Bounded(min = 3)
@Bounded(min = 7)
class Test {
}

@RetainStereotypes
@Size(min = 5)
@Repeatable(Bounded.List.class)
@Retention(RetentionPolicy.RUNTIME)
@interface Bounded {
    @AliasFor(annotation = Size.class, member = "min", applyDefault = true)
    int min() default 5;

    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        Bounded[] value();
    }
}
'''))

        when:
        def occurrences = annotationMetadata.getAnnotationValuesByName("repeatspec.Bounded")

        then: "each occurrence of the composed annotation reports the Size it introduced"
        occurrences.collect { sizeOf(it) } == [[[min: 3]], [[min: 7]]]
    }

    private static List<Map> sizeOf(AnnotationValue<?> annotationValue) {
        annotationValue.getStereotypes()
                .findAll { it.getAnnotationName() == "jakarta.validation.constraints.Size" }
                .collect { it.getValues() }
    }

    private AnnotationMetadata metadata(boolean written) {
        def annotationMetadata = buildTypeAnnotationMetadata("""
package originspec;
${IMPORTS}
@ComposedA(min = 3)
@ComposedB(max = 9)
class Test {
}
${COMPOSED}
""")
        written ? writeAndLoadMetadata('originspec.Test', annotationMetadata) : annotationMetadata
    }
}
