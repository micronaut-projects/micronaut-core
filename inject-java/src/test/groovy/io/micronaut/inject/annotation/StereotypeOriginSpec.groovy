package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import spock.lang.PendingFeature
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

    private static final String NESTED = '''
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
'''

    void "the retained tree nests"() {
        given:
        def annotationMetadata = writeAndLoadMetadata('nestedspec.Test', buildTypeAnnotationMetadata(NESTED))

        when:
        def outer = annotationMetadata.getAnnotation("nestedspec.Outer")
        def inner = outer.getStereotypes().find { it.getAnnotationName() == "nestedspec.Inner" }

        then: "the subtree of the intermediate annotation is reachable through it"
        inner != null
        inner.getStereotypes()*.getAnnotationName().contains("jakarta.validation.constraints.Size")

        and: "the override reaches the intermediate annotation"
        inner.getValues() == [min: 3]
    }

    /**
     * {@code applyIntroducedAliases} rewrites the members of an intermediate annotation after that annotation's
     * own subtree has been computed, and does not re-run down the tree. So {@code @Outer(shortest = 3)},
     * overriding {@code Inner.min}, which itself overrides {@code Size.min}, reaches {@code @Inner(min = 3)} but
     * leaves {@code @Size(min = 1)}.
     *
     * <p>The tree and the flat index agree on the same wrong value, so the tree does not introduce this — but it
     * blocks the motivating consumer. {@code micronaut-validation} cascades today: its
     * {@code DefaultConstraintDescriptor} builds each composing descriptor from the already-overridden
     * {@code AnnotationValue} of its parent, which re-applies {@code @OverridesAttribute} one level down, and the
     * constraint-composition tests in the Jakarta Validation TCK cover that. Reading composing values off the
     * tree instead of reflecting over the annotation types would silently regress it.</p>
     *
     * <p>Both views are asserted so that a fix moves them together.</p>
     */
    @PendingFeature(reason = "applyIntroducedAliases does not re-run down the subtree whose members it rewrote")
    void "a transitive override cascades to what the intermediate annotation composes"() {
        given:
        def annotationMetadata = writeAndLoadMetadata('nestedspec.Test', buildTypeAnnotationMetadata(NESTED))

        when:
        def outer = annotationMetadata.getAnnotation("nestedspec.Outer")
        def inner = outer.getStereotypes().find { it.getAnnotationName() == "nestedspec.Inner" }

        then: "the override reaches the intermediate annotation"
        inner.getValues() == [min: 3]

        and: "and cascades to what that annotation composes"
        inner.getStereotypes()
                .findAll { it.getAnnotationName() == "jakarta.validation.constraints.Size" }
                .collect { it.getValues() } == [[min: 3]]

        and: "the flat index agrees"
        annotationMetadata.getAnnotationValuesByName("jakarta.validation.constraints.Size")
                .collect { it.getValues() } == [[min: 3]]
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

    @Unroll
    void "written retained trees exclude #retention-retention annotations"() {
        given:
        def packageName = "retentionspec${retention.toLowerCase()}"
        def annotationMetadata = writeAndLoadMetadata("${packageName}.Test", buildTypeAnnotationMetadata("""
package ${packageName};

import io.micronaut.core.annotation.RetainStereotypes;
import java.lang.annotation.*;

@Retaining
class Test {
}

@RetainStereotypes
@RuntimeStereotype
@NotRuntime
@Retention(RetentionPolicy.RUNTIME)
@interface Retaining {
}

@Retention(RetentionPolicy.RUNTIME)
@interface RuntimeStereotype {
}

@Retention(RetentionPolicy.${retention})
@interface NotRuntime {
}
"""))

        expect:
        annotationMetadata.getAnnotation("${packageName}.Retaining").getStereotypes()*.annotationName as Set ==
                ["${packageName}.RuntimeStereotype"] as Set

        where:
        retention << ["SOURCE", "CLASS"]
    }

    @Unroll
    void "retained stereotypes survive #operation"() {
        when:
        def result = transform(metadata(true))

        then:
        sizeOf(result.getAnnotation("originspec.ComposedA")) == [[min: 3]]
        sizeOf(result.getAnnotation("originspec.ComposedB")) == [[max: 9]]

        where:
        operation                       | transform
        "conversion to mutable metadata" | { MutableAnnotationMetadata.of(it) }
        "mutable metadata cloning"       | { MutableAnnotationMetadata.of(it).clone() }
        "default metadata cloning"       | { it.clone() }
    }

    @Unroll
    void "merging a #kind hierarchy keeps the retained tree from the overriding metadata"() {
        given:
        def parent = buildTypeAnnotationMetadata("""
package hierarchyspec;
${IMPORTS}
@ComposedA(min = 4)
class Parent {
}
${COMPOSED}
""")
        def child = buildTypeAnnotationMetadata("""
package hierarchyspec;
${IMPORTS}
@ComposedA(min = 3)
class Child {
}
${COMPOSED}
""")
        if (kind == "written") {
            parent = writeAndLoadMetadata("hierarchyspec.Parent", parent)
            child = writeAndLoadMetadata("hierarchyspec.Child", child)
        }

        when:
        def merged = new AnnotationMetadataHierarchy(parent, child).merge()

        then:
        merged.getAnnotation("hierarchyspec.ComposedA").getValues() == [min: 3]
        sizeOf(merged.getAnnotation("hierarchyspec.ComposedA")) == [[min: 3]]

        where:
        kind << ["compiled", "written"]
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
