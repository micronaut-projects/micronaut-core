package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.Retainable
import io.micronaut.inject.annotation.AnnotationTransformer
import io.micronaut.inject.annotation.NamedAnnotationTransformer
import io.micronaut.inject.visitor.VisitorContext
import jakarta.validation.Constraint
import org.jspecify.annotations.NonNull
import spock.lang.Unroll

import java.lang.annotation.Annotation

/**
 * The association between a composing annotation occurrence and the annotation that introduced it is lost when
 * the annotation tree is flattened into the name-keyed stereotype indexes. A {@code @Retainable} annotation keeps
 * it: every annotation composing it retains the occurrence.
 *
 * <p>The constraint annotations composed throughout are {@code jakarta.validation}'s, which cannot carry the
 * marker themselves, so {@code @Constraint} is marked through a transformer, the way micronaut-validation marks
 * it.</p>
 */
class StereotypeOriginSpec extends AbstractTypeElementSpec {

    @Override
    protected List<AnnotationTransformer<? extends Annotation>> getLocalAnnotationTransformers(@NonNull String annotationName) {
        if (annotationName == Constraint.name) {
            return [new RetainableConstraintTransformer()]
        }
        return super.getLocalAnnotationTransformers(annotationName)
    }

    private static class RetainableConstraintTransformer implements NamedAnnotationTransformer {

        @Override
        String getName() {
            return Constraint.name
        }

        @Override
        List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
            return [annotation.mutate().stereotype(AnnotationValue.builder(Retainable).build()).build()]
        }
    }

    private static final String IMPORTS = '''
import io.micronaut.context.annotation.AliasFor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;
'''

    private static final String COMPOSED = '''
@Size(min = 5)
@NotNull
@Retention(RetentionPolicy.RUNTIME)
@interface ComposedA {
    @AliasFor(annotation = Size.class, member = "min", applyDefault = true)
    int min() default 5;
}

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
    void "a retainable composed annotation is attributed to the annotation that introduced it (#kind)"() {
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

        and: "a retained occurrence keeps its own retainable stereotypes and nothing else"
        composedA.getStereotypes()
                .find { it.getAnnotationName() == "jakarta.validation.constraints.Size" }
                .getStereotypes()*.getAnnotationName() == ["jakarta.validation.Constraint"]

        where:
        kind << ["compiled", "written"]
    }

    private static final String NESTED = '''
package nestedspec;

import io.micronaut.context.annotation.AliasFor;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;

@Outer(shortest = 3)
class Test {
}

@Inner(min = 1)
@Retention(RetentionPolicy.RUNTIME)
@interface Outer {
    @AliasFor(annotationName = "nestedspec.Inner", member = "min", applyDefault = true)
    int shortest() default 1;
}

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
     * An overridden member may itself alias a member of an annotation the occurrence composes, and the
     * occurrence's subtree was computed from the values it had before the override, so the override has to
     * cascade: {@code @Outer(shortest = 3)} overriding {@code Inner.min}, which itself overrides
     * {@code Size.min}, must reach {@code @Size(min = 3)} and not stop at {@code @Inner(min = 3)}.
     *
     * <p>This is what {@code micronaut-validation} does today, by building each composing descriptor from the
     * already-overridden {@code AnnotationValue} of its parent and re-applying {@code @OverridesAttribute} one
     * level down; the constraint-composition tests in the Jakarta Validation TCK cover it. Reading composing
     * values off the tree instead of reflecting over the annotation types must not regress it.</p>
     *
     * <p>Both views are asserted, because the flat index took the same uncascaded value.</p>
     */
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

    /**
     * The cascade recurses, so it has to reach the leaves and not just the first intermediate level: for
     * {@code @A(shortest = 7)} over {@code @B} over {@code @C} over {@code @Size}, every level takes the
     * override, {@code @Size} included.
     *
     * <p>Re-deriving the aliases of a node further down the subtree needs that node's annotation type, and the
     * tree holds only annotation values, so the type is looked up by name. Where an implementation cannot
     * resolve an annotation type from a name alone — the builder notes this for KSP and Groovy, and this spec
     * reproduces it because {@code writeAndLoadMetadata} builds with a parser that never entered the source —
     * that lookup returns nothing and the cascade used to stop at the first intermediate annotation, leaving
     * {@code @Size(min = 1)}. The builder now falls back to the annotation types it saw during processing.</p>
     *
     * <p>Two levels is not exotic for the motivating consumer: a composed constraint composing another composed
     * constraint is ordinary, and {@code micronaut-validation} cascades to any depth today because each
     * composing descriptor re-applies {@code @OverridesAttribute} from its own already-overridden value.</p>
     */
    void "a transitive override cascades through more than one intermediate annotation"() {
        given:
        def annotationMetadata = writeAndLoadMetadata('deepspec.Test', buildTypeAnnotationMetadata('''
package deepspec;

import io.micronaut.context.annotation.AliasFor;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;

@A(shortest = 7)
class Test {
}

@B(min = 1)
@Retention(RetentionPolicy.RUNTIME)
@interface A {
    @AliasFor(annotationName = "deepspec.B", member = "min", applyDefault = true)
    int shortest() default 1;
}

@C(min = 2)
@Retention(RetentionPolicy.RUNTIME)
@interface B {
    @AliasFor(annotationName = "deepspec.C", member = "min", applyDefault = true)
    int min() default 2;
}

@Size(min = 3)
@Retention(RetentionPolicy.RUNTIME)
@interface C {
    @AliasFor(annotation = Size.class, member = "min", applyDefault = true)
    int min() default 3;
}
'''))

        when:
        def b = annotationMetadata.getAnnotation("deepspec.A").getStereotypes()
                .find { it.getAnnotationName() == "deepspec.B" }
        def c = b.getStereotypes().find { it.getAnnotationName() == "deepspec.C" }

        then: "the override reaches every level, not just the first"
        b.getValues() == [min: 7]
        c.getValues() == [min: 7]

        and: "including the leaf three levels down"
        c.getStereotypes()
                .findAll { it.getAnnotationName() == "jakarta.validation.constraints.Size" }
                .collect { it.getValues() } == [[min: 7]]

        and: "the flat index agrees"
        annotationMetadata.getAnnotationValuesByName("jakarta.validation.constraints.Size")
                .collect { it.getValues() } == [[min: 7]]
    }

    /**
     * The cascade lives in {@code applyIntroducedAliases}, which runs for every annotation the builder
     * processes, so it is not a retention feature: an uncascaded override was reported by the flat index of
     * annotations that retain nothing, and every existing {@code @AliasFor} user sees this change. Pinned
     * separately so a regression here is not read as a retention regression.
     */
    void "the cascade is not tied to retention"() {
        given: "nothing composed is retainable, so nothing is retained and only the flat index exists"
        def annotationMetadata = writeAndLoadMetadata('plaincascadespec.Test', buildTypeAnnotationMetadata('''
package plaincascadespec;

import io.micronaut.context.annotation.AliasFor;
import java.lang.annotation.*;

@Outer(shortest = 4)
class Test {
}

@Inner(min = 1)
@Retention(RetentionPolicy.RUNTIME)
@interface Outer {
    @AliasFor(annotationName = "plaincascadespec.Inner", member = "min", applyDefault = true)
    int shortest() default 1;
}

@Limit(min = 2)
@Retention(RetentionPolicy.RUNTIME)
@interface Inner {
    @AliasFor(annotation = Limit.class, member = "min", applyDefault = true)
    int min() default 2;
}

@Retention(RetentionPolicy.RUNTIME)
@interface Limit {
    int min() default 0;
}
'''))

        expect: "nothing is retained"
        annotationMetadata.getAnnotation("plaincascadespec.Outer").getStereotypes() == null

        and: "and the flat index still takes the cascaded value"
        annotationMetadata.getAnnotation("plaincascadespec.Inner").getValues() == [min: 4]
        annotationMetadata.getAnnotation("plaincascadespec.Limit").getValues() == [min: 4]
    }

    void "the marker on a framework annotation makes every annotation meta-annotated with it retainable"() {
        given: "Family stands in for a framework annotation such as jakarta.validation.Constraint"
        def annotationMetadata = writeAndLoadMetadata('familyspec.Test', buildTypeAnnotationMetadata('''
package familyspec;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.Retainable;
import java.lang.annotation.*;

@Composed(min = 3)
class Test {
}

@Retainable
@Retention(RetentionPolicy.RUNTIME)
@interface Family {
}

@Family
@Retention(RetentionPolicy.RUNTIME)
@interface Member {
    int min() default 0;
}

@Member(min = 5)
@Retention(RetentionPolicy.RUNTIME)
@interface Composed {
    @AliasFor(annotation = Member.class, member = "min", applyDefault = true)
    int min() default 5;
}
'''))

        when:
        def member = annotationMetadata.getAnnotation("familyspec.Composed").getStereotypes()
                .find { it.getAnnotationName() == "familyspec.Member" }

        then: "Member is retained without declaring the marker itself, with the override applied"
        member.getValues() == [min: 3]

        and: "and keeps the framework annotation that made it retainable, but not the marker"
        member.getStereotypes()*.getAnnotationName() == ["familyspec.Family"]
    }

    void "annotations composing nothing retainable retain nothing"() {
        given:
        def annotationMetadata = writeAndLoadMetadata('plainspec.Test', buildTypeAnnotationMetadata('''
package plainspec;

import io.micronaut.context.annotation.AliasFor;
import java.lang.annotation.*;

@Plain(min = 3)
class Test {
}

@Limit(min = 5)
@Retention(RetentionPolicy.RUNTIME)
@interface Plain {
    @AliasFor(annotation = Limit.class, member = "min", applyDefault = true)
    int min() default 5;
}

@Retention(RetentionPolicy.RUNTIME)
@interface Limit {
    int min() default 0;
}
'''))

        expect:
        annotationMetadata.getAnnotation("plainspec.Plain").getStereotypes() == null
        annotationMetadata.getAnnotation("plainspec.Limit").getValues() == [min: 3]
    }

    @Unroll
    void "written retained trees exclude #retention-retention annotations"() {
        given:
        def packageName = "retentionspec${retention.toLowerCase()}"
        def annotationMetadata = writeAndLoadMetadata("${packageName}.Test", buildTypeAnnotationMetadata("""
package ${packageName};

import io.micronaut.core.annotation.Retainable;
import java.lang.annotation.*;

@Retaining
class Test {
}

@RuntimeStereotype
@NotRuntime
@Retention(RetentionPolicy.RUNTIME)
@interface Retaining {
}

@Retainable
@Retention(RetentionPolicy.RUNTIME)
@interface RuntimeStereotype {
}

@Retainable
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
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;

@Bounded(min = 3)
@Bounded(min = 7)
class Test {
}

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

    /**
     * A constraint composing the same constraint twice is the shape {@code AliasFor.index} exists for: the
     * occurrences are what {@code jakarta.validation.OverridesAttribute.constraintIndex} selects between, and a
     * flat name-keyed index cannot address them at all. The two occurrences are folded into the repeatable
     * container by javac, so this also exercises the container flattening.
     */
    void "a constraint composing the same annotation twice attributes an index-selective override to each occurrence"() {
        given:
        def annotationMetadata = writeAndLoadMetadata('twicespec.Test', buildTypeAnnotationMetadata('''
package twicespec;

import io.micronaut.context.annotation.AliasFor;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;

@Password(shortest = 8, longest = 64)
class Test {
}

@Size(min = 1)
@Size(max = 5)
@Retention(RetentionPolicy.RUNTIME)
@interface Password {
    @AliasFor(annotation = Size.class, member = "min", index = 0, applyDefault = true)
    int shortest() default 1;

    @AliasFor(annotation = Size.class, member = "max", index = 1, applyDefault = true)
    int longest() default 5;
}
'''))

        expect: "each occurrence keeps the override addressed to it, and neither leaks into the other"
        sizeOf(annotationMetadata.getAnnotation("twicespec.Password")) == [[min: 8], [max: 64]]
    }

    /**
     * The default {@code AliasFor.index} of {@code -1} applies the override to every occurrence, which is what
     * {@code @OverridesAttribute} without a {@code constraintIndex} means.
     */
    void "an override with no index reaches every occurrence of the composed annotation"() {
        given:
        def annotationMetadata = writeAndLoadMetadata('allspec.Test', buildTypeAnnotationMetadata('''
package allspec;

import io.micronaut.context.annotation.AliasFor;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;

@Bounded(min = 5)
class Test {
}

@Size(min = 1, max = 10)
@Size(min = 2, max = 20)
@Retention(RetentionPolicy.RUNTIME)
@interface Bounded {
    @AliasFor(annotation = Size.class, member = "min", applyDefault = true)
    int min() default 1;
}
'''))

        expect: "both occurrences take the override, and each keeps the member it was not overridden on"
        sizeOf(annotationMetadata.getAnnotation("allspec.Bounded")) == [[min: 5, max: 10], [min: 5, max: 20]]
    }

    /**
     * Writing the composing constraints inside the repeatable container by hand is the same declaration as
     * repeating them, so it must produce the same tree — javac folds the repeated form into exactly this.
     */
    void "composing annotations written inside their container flatten like repeated ones"() {
        given:
        def annotationMetadata = writeAndLoadMetadata('containedspec.Test', buildTypeAnnotationMetadata('''
package containedspec;

import io.micronaut.context.annotation.AliasFor;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;

@Contained(shortest = 8, longest = 64)
class Test {
}

@Size.List({@Size(min = 1), @Size(max = 5)})
@Retention(RetentionPolicy.RUNTIME)
@interface Contained {
    @AliasFor(annotation = Size.class, member = "min", index = 0, applyDefault = true)
    int shortest() default 1;

    @AliasFor(annotation = Size.class, member = "max", index = 1, applyDefault = true)
    int longest() default 5;
}
'''))

        expect: "the container is flattened into its occurrences, addressable by index"
        sizeOf(annotationMetadata.getAnnotation("containedspec.Contained")) == [[min: 8], [max: 64]]
    }

    /**
     * A composing container need not be the one declared through {@code @Repeatable}: an annotation holding an
     * array of annotations in its {@code value} is unwrapped too, which is what {@code micronaut-validation}
     * does at runtime — {@code ConstraintContainers} resolves the constraint a container holds "whatever its
     * name", and {@code DefaultConstraintDescriptor} unwraps any member returning an array of annotations.
     *
     * <p>So an occurrence contributed by a hand-rolled container is an occurrence like any other, and is
     * addressable by index alongside a directly declared one.</p>
     */
    void "a container that is not the declared repeatable container is flattened too"() {
        given:
        def annotationMetadata = writeAndLoadMetadata('opaquespec.Test', buildTypeAnnotationMetadata('''
package opaquespec;

import io.micronaut.context.annotation.AliasFor;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;

@Custom(longest = 64)
class Test {
}

@Retention(RetentionPolicy.RUNTIME)
@interface Sizes {
    Size[] value();
}

@Size(min = 1)
@Sizes({@Size(max = 5)})
@Retention(RetentionPolicy.RUNTIME)
@interface Custom {
    @AliasFor(annotation = Size.class, member = "max", index = 1, applyDefault = true)
    int longest() default 5;
}
'''))

        expect: "the occurrence the hand-rolled container contributed is an occurrence, and takes an override by index"
        sizeOf(annotationMetadata.getAnnotation("opaquespec.Custom")) == [[min: 1], [max: 64]]
    }

    /**
     * A value rebuilt with {@code mutate()} carries the tree only in the transient {@code stereotypes} field,
     * which the writer does not emit — see {@code AnnotationValueSpec}. Merging that value back into the
     * metadata is nonetheless safe, because {@code addDeclaredAnnotation} merges into the entry already there
     * rather than replacing it, so the member the rebuilt value lost is still on the original.
     *
     * <p>That is the path a {@code TypeElementVisitor} adjusting an annotation takes, so it is worth pinning:
     * were the merge to become a replace, every visitor that touches an annotation would silently drop its
     * tree, and nothing would fail until the metadata was read back after writing.</p>
     */
    void "an annotation rebuilt and merged back keeps its retained tree through the writer"() {
        given: "metadata whose composed annotation is rebuilt the way a visitor would rebuild it"
        def compiled = buildTypeAnnotationMetadata("""
package rebuiltspec;
${IMPORTS}
@ComposedA(min = 3)
class Test {
}
${COMPOSED}
""")
        def mutable = MutableAnnotationMetadata.of(compiled)
        def original = mutable.getAnnotation("rebuiltspec.ComposedA")
        def rebuilt = original.mutate().member("min", 9).build()
        mutable.addDeclaredAnnotation(rebuilt.getAnnotationName(), rebuilt.getValues(), rebuilt.getRetentionPolicy())

        expect: "the rebuilt value still reads its tree before being written"
        rebuilt.getStereotypes() != null

        and: "and the tree is still there once written"
        writeAndLoadMetadata('rebuiltspec.Test', mutable)
                .getAnnotation("rebuiltspec.ComposedA")
                .getStereotypes() != null
    }

    /**
     * A composed constraint in the shape a validation integration sees it. {@code @Inherited} is what
     * {@code micronaut-validation}'s remapper adds to every constraint, and without it the composed annotation
     * does not reach an implementing class at all — only its flattened stereotypes do. The composed annotation is
     * a retainable one declared alongside, because these shapes are built by the annotation processor itself,
     * which the transformer this spec registers does not reach.
     */
    private static final String SHAPES = '''
@Inherited
@Limit(min = 5)
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE,
          ElementType.ANNOTATION_TYPE, ElementType.RECORD_COMPONENT })
@interface MinimumLength {
    @AliasFor(annotation = Limit.class, member = "min", applyDefault = true)
    int min() default 5;
}

@Retainable
@Retention(RetentionPolicy.RUNTIME)
@interface Limit {
    int min() default 0;
}
'''

    private static final String SHAPE_IMPORTS = '''
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Retainable;
import java.lang.annotation.*;
import java.util.List;
'''

    /**
     * The element shapes a validation integration actually validates. Each reaches the metadata by its own
     * route — a property merges the field and the accessors, a container element lands on a type argument, an
     * implementation inherits from its interface, a record component feeds both the property and the
     * constructor argument — and the reserved member travels in a values map through all of them.
     */
    void "the retained tree survives the merge into property metadata"() {
        given:
        def introspection = buildBeanIntrospection('propertyshape.Person', """
package propertyshape;
${SHAPE_IMPORTS}
@Introspected
class Person {
    @MinimumLength(min = 8)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
${SHAPES}
""")

        expect: "the merge neither drops the tree nor duplicates the composing occurrence"
        limitOf(introspection.getRequiredProperty("name", String)
                .getAnnotationMetadata()
                .getAnnotation('propertyshape.MinimumLength'), 'propertyshape') == [[min: 8]]
    }

    void "the retained tree survives into container element metadata"() {
        given:
        def introspection = buildBeanIntrospection('elementshape.Team', """
package elementshape;
${SHAPE_IMPORTS}
@Introspected
class Team {
    private List<@MinimumLength(min = 8) String> members;

    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members;
    }
}
${SHAPES}
""")

        expect: "the type argument carries the constraint with its composing occurrence attributed"
        limitOf(introspection.getRequiredProperty("members", List)
                .asArgument()
                .getTypeParameters()[0]
                .getAnnotationMetadata()
                .getAnnotation('elementshape.MinimumLength'), 'elementshape') == [[min: 8]]
    }

    void "the retained tree survives inheritance from an interface"() {
        given: "the constraint is declared on the interface and validated on the implementation"
        def introspection = buildBeanIntrospection('inheritedshape.Person', """
package inheritedshape;
${SHAPE_IMPORTS}
interface Named {
    @MinimumLength(min = 8)
    String getName();
}

@Introspected
class Person implements Named {

    private String name;

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
${SHAPES}
""")

        expect: "the inherited constraint keeps the occurrence it introduced"
        limitOf(introspection.getRequiredProperty("name", String)
                .getAnnotationMetadata()
                .getAnnotation('inheritedshape.MinimumLength'), 'inheritedshape') == [[min: 8]]
    }

    void "the retained tree survives onto a record component"() {
        given:
        def introspection = buildBeanIntrospection('recordshape.Person', """
package recordshape;
${SHAPE_IMPORTS}
@Introspected
record Person(@MinimumLength(min = 8) String name) {
}
${SHAPES}
""")

        expect: "both routes to the component carry the tree"
        limitOf(introspection.getRequiredProperty("name", String)
                .getAnnotationMetadata()
                .getAnnotation('recordshape.MinimumLength'), 'recordshape') == [[min: 8]]
        limitOf(introspection.getConstructorArguments()[0]
                .getAnnotationMetadata()
                .getAnnotation('recordshape.MinimumLength'), 'recordshape') == [[min: 8]]
    }

    private static List<Map> limitOf(AnnotationValue<?> annotationValue, String packageName) {
        annotationValue.getStereotypes()
                .findAll { it.getAnnotationName() == "${packageName}.Limit".toString() }
                .collect { it.getValues() }
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
