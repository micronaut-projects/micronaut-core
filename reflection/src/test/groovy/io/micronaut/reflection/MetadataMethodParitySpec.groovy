package io.micronaut.reflection

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.Specification

import static io.micronaut.reflection.DeepAliasAnnotations.DeepA
import static io.micronaut.reflection.DeepAliasAnnotations.DeepB
import static io.micronaut.reflection.DeepAliasAnnotations.DeepC
import static io.micronaut.reflection.DeepAliasAnnotations.DeepLimit

/**
 * Every read method of {@link AnnotationMetadata}, answered by the metadata the processors generate for an
 * element and by the metadata {@link ReflectionAnnotations} builds for the same element, compared answer by
 * answer.
 *
 * <p>The shapes covered are the ones the two builders can disagree over: an annotation composing another with
 * {@code @AliasFor} overriding its members, an annotation composed more than once with an alias per occurrence,
 * a member aliasing another member of the same annotation, a repeatable annotation written once and twice, an
 * annotation left at its defaults, and the stereotypes and the retained tree of all of them.</p>
 */
class MetadataMethodParitySpec extends Specification {

    private static final List<String> PROBES = [
        Username.name, Sized.name, Sizes.name, Contract.name, Spread.name, Labelled.name,
        Tag.name, Tags.name, Stereo.name, Level.name, "io.micronaut.core.annotation.Retainable", "does.not.Exist",
        Every.name, InheritedMark.name
    ]

    private static Object canonical(Object v) {
        if (v instanceof java.lang.annotation.Annotation) {
            return canonical(AnnotationValue.of(v))
        }
        if (v instanceof AnnotationValue) {
            return v.getAnnotationName() + new TreeMap<>(v.getValues().collectEntries { k, x -> [(k.toString()): canonical(x)] })
        }
        if (v?.getClass()?.isArray()) {
            return (v as Object[]).toList().collect { canonical(it) }
        }
        if (v instanceof Collection) {
            return v.collect { canonical(it) }
        }
        if (v instanceof Optional) {
            return v.map { canonical(it) }.orElse("<empty>")
        }
        if (v instanceof OptionalInt) {
            return v.isPresent() ? canonical(v.getAsInt()) : "<empty>"
        }
        if (v instanceof OptionalLong) {
            return v.isPresent() ? canonical(v.getAsLong()) : "<empty>"
        }
        if (v instanceof OptionalDouble) {
            return v.isPresent() ? canonical(v.getAsDouble()) : "<empty>"
        }
        if (v instanceof Map) {
            return new TreeMap<>(v.collectEntries { k, x -> [(String.valueOf(k)): canonical(x)] }).toString()
        }
        if (v instanceof Class) {
            return v.name
        }
        return String.valueOf(v)
    }

    /** Whether a member holds annotation values rather than plain ones. */
    private static boolean holdsAnnotations(Object value) {
        if (value instanceof AnnotationValue) {
            return true
        }
        Collection<?> held = value?.getClass()?.isArray() ? (value as Object[]).toList()
            : (value instanceof Collection ? value : null)
        return held != null && held.any { it instanceof AnnotationValue }
    }

    /** Calls a method, so that a metadata answering by throwing is compared with one answering the same way. */
    private static Object answer(Closure<?> call) {
        try {
            return canonical(call.call())
        } catch (Throwable e) {
            return e.getClass().simpleName
        }
    }

    private static String describe(AnnotationMetadata m) {
        StringBuilder out = new StringBuilder()
        out << "isEmpty=" << answer { m.isEmpty() } << "\n"
        out << "annotationNames=" << answer { m.getAnnotationNames().toSorted() } << "\n"
        out << "declaredAnnotationNames=" << answer { m.getDeclaredAnnotationNames().toSorted() } << "\n"
        out << "stereotypeAnnotationNames=" << answer { m.getStereotypeAnnotationNames().toSorted() } << "\n"
        out << "declaredStereotypeAnnotationNames=" << answer { m.getDeclaredStereotypeAnnotationNames().toSorted() } << "\n"
        out << "hasPropertyExpressions=" << answer { m.hasPropertyExpressions() } << "\n"
        out << "hasEvaluatedExpressions=" << answer { m.hasEvaluatedExpressions() } << "\n"

        for (name in PROBES.toSorted()) {
            out << "-- " << name << "\n"
            out << "  hasAnnotation=" << answer { m.hasAnnotation(name) } << "\n"
            out << "  hasDeclaredAnnotation=" << answer { m.hasDeclaredAnnotation(name) } << "\n"
            out << "  hasStereotype=" << answer { m.hasStereotype(name) } << "\n"
            out << "  hasDeclaredStereotype=" << answer { m.hasDeclaredStereotype(name) } << "\n"
            out << "  hasSimpleAnnotation=" << answer { m.hasSimpleAnnotation(name.substring(name.lastIndexOf('.') + 1)) } << "\n"
            out << "  hasSimpleDeclaredAnnotation=" << answer { m.hasSimpleDeclaredAnnotation(name.substring(name.lastIndexOf('.') + 1)) } << "\n"
            out << "  hasStereotypeNonRepeating=" << answer { m.hasStereotypeNonRepeating(name) } << "\n"
            out << "  isRepeatableAnnotation=" << answer { m.isRepeatableAnnotation(name) } << "\n"
            out << "  getAnnotation=" << answer { m.getAnnotation(name) } << "\n"
            out << "  findAnnotation=" << answer { m.findAnnotation(name) } << "\n"
            out << "  findDeclaredAnnotation=" << answer { m.findDeclaredAnnotation(name) } << "\n"
            out << "  findRepeatableAnnotation=" << answer { m.findRepeatableAnnotation(name) } << "\n"
            out << "  getAnnotationValuesByName=" << answer { m.getAnnotationValuesByName(name) } << "\n"
            out << "  getDeclaredAnnotationValuesByName=" << answer { m.getDeclaredAnnotationValuesByName(name) } << "\n"
            out << "  getAnnotationType=" << answer { m.getAnnotationType(name) } << "\n"
            out << "  getAnnotationNamesByStereotype=" << answer { m.getAnnotationNamesByStereotype(name).toSorted() } << "\n"
            out << "  getAnnotationNameByStereotype=" << answer { m.getAnnotationNameByStereotype(name) } << "\n"
            out << "  getAnnotationTypeByStereotype=" << answer { m.getAnnotationTypeByStereotype(name) } << "\n"
            out << "  getAnnotationTypesByStereotype=" << answer { m.getAnnotationTypesByStereotype(name).collect { it.name }.toSorted() } << "\n"
            out << "  getDeclaredAnnotationNamesByStereotype=" << answer { m.getDeclaredAnnotationNamesByStereotype(name).toSorted() } << "\n"
            out << "  getDeclaredAnnotationNameByStereotype=" << answer { m.getDeclaredAnnotationNameByStereotype(name) } << "\n"
            out << "  getDeclaredAnnotationTypeByStereotype=" << answer { m.getDeclaredAnnotationTypeByStereotype(name) } << "\n"
            out << "  getAnnotationValuesByStereotype=" << answer { m.getAnnotationValuesByStereotype(name) } << "\n"
            out << "  getDefaultValues=" << answer { m.getDefaultValues(name) } << "\n"
            out << "  getValues=" << answer { m.getValues(name) } << "\n"
            for (member in ["value", "min", "max", "least", "first", "second", "name", "priority", "type", "level",
                            "nested", "message", "handledBy",
                            "aByte", "aShort", "anInt", "aLong", "aFloat", "aDouble", "aChar", "aBoolean", "aString",
                            "aClass", "anEnum", "anAnnotation", "bytes", "shorts", "ints", "longs", "floats",
                            "doubles", "chars", "booleans", "strings", "classes", "enums", "annotations",
                            "absentMember"]) {
                out << "    " << member << ": "
                out << "getValue=" << answer { m.getValue(name, member, Object) } << " "
                out << "getDefaultValue=" << answer { m.getDefaultValue(name, member, Object) } << " "
                // the two implementations hold the occurrences of a repeatable annotation differently, which
                // the string accessors render differently; the divergence is pinned by a test of its own below
                boolean nested = holdsAnnotations(m.getValues(name).get(member))
                out << "stringValue=" << (nested ? "<nested>" : answer { m.stringValue(name, member) }) << " "
                out << "stringValues=" << (nested ? "<nested>" : answer { m.stringValues(name, member) }) << " "
                out << "intValue=" << answer { m.intValue(name, member) } << " "
                out << "longValue=" << answer { m.longValue(name, member) } << " "
                out << "doubleValue=" << answer { m.doubleValue(name, member) } << " "
                out << "booleanValue=" << answer { m.booleanValue(name, member) } << " "
                out << "classValue=" << answer { m.classValue(name, member) } << " "
                out << "classValues=" << answer { m.classValues(name, member).collect { it.name } } << " "
                out << "enumValue=" << answer { m.enumValue(name, member, Level) } << " "
                out << "enumValues=" << answer { m.enumValues(name, member, Level).toList() } << " "
                out << "enumValuesSet=" << answer { m.enumValuesSet(name, member, Level).collect { it.name() }.toSorted() } << " "
                out << "isTrue=" << answer { m.isTrue(name, member) } << " "
                out << "isFalse=" << answer { m.isFalse(name, member) } << " "
                out << "isPresent=" << answer { m.isPresent(name, member) } << "\n"
            }
        }
        return out.toString()
    }

    void "every read method answers the same for the property #property, built either way"() {
        given:
        def compileTime = BeanIntrospector.SHARED.getIntrospection(RetainedBean)
            .getRequiredProperty(property, String).getAnnotationMetadata()
        def reflective = ReflectionAnnotations.metadataOf(RetainedBean.getDeclaredField(property))

        expect:
        dump(property, "all", reflective, compileTime)

        where:
        property << ["name", "code", "labelled", "spread", "tagged", "bare", "plain", "duplicated"]
    }

    private static boolean dump(String property, String view, AnnotationMetadata reflective, AnnotationMetadata compileTime) {
        String r = describe(reflective)
        String c = describe(compileTime)
        if (r != c) {
            new File("/tmp/parity-${property}-${view}-reflective.txt").text = r
            new File("/tmp/parity-${property}-${view}-compiletime.txt").text = c
        }
        return r == c
    }

    void "every read method answers the same for the #kind of a type described either way"() {
        expect:
        dump(kind, "element", ReflectionAnnotations.metadataOf(element), generated)

        where:
        kind          | element                                                             || generated
        "type"        | EveryKindBean                                                       || BeanIntrospector.SHARED.getIntrospection(EveryKindBean).getAnnotationMetadata()
        "field"       | EveryKindBean.getDeclaredField("annotated")                         || BeanIntrospector.SHARED.getIntrospection(EveryKindBean).getRequiredProperty("annotated", String).getAnnotationMetadata()
        "defaulted"   | EveryKindBean.getDeclaredField("defaulted")                         || BeanIntrospector.SHARED.getIntrospection(EveryKindBean).getRequiredProperty("defaulted", String).getAnnotationMetadata()
    }

    void "the annotations a type keeps from its super class and its interfaces are the same, described either way"() {
        given:
        def compileTime = BeanIntrospector.SHARED.getIntrospection(EveryKindBean).getAnnotationMetadata()
        def reflective = ReflectionAnnotations.metadataOf(EveryKindBean)

        expect: "an @Inherited annotation of the super class is on the subtype, and is not one it declares"
        reflective.hasAnnotation(InheritedMark) == compileTime.hasAnnotation(InheritedMark)
        reflective.hasDeclaredAnnotation(InheritedMark) == compileTime.hasDeclaredAnnotation(InheritedMark)
        reflective.stringValue(InheritedMark).orElse(null) == compileTime.stringValue(InheritedMark).orElse(null)

        and: "an annotation that is not @Inherited is on neither"
        reflective.getAnnotationValuesByType(Tag)*.stringValue()*.orElse(null) ==
            compileTime.getAnnotationValuesByType(Tag)*.stringValue()*.orElse(null)
    }

    void "the members of every kind convert the same, and so do their defaults"() {
        given:
        def compileTime = BeanIntrospector.SHARED.getIntrospection(EveryKindBean)
            .getRequiredProperty("defaulted", String).getAnnotationMetadata()
        def reflective = ReflectionAnnotations.metadataOf(EveryKindBean.getDeclaredField("defaulted"))

        expect: "a member left at its default is not a value, on either side"
        canonical(reflective.getValues(Every.name)) == canonical(compileTime.getValues(Every.name))

        and: "and the defaults registered for the type answer for it"
        canonical(reflective.getDefaultValues(Every.name)) == canonical(compileTime.getDefaultValues(Every.name))

        and: "a member left at its default is not answered as a value by either, and the default is registered"
        !reflective.intValue(Every, "anInt").isPresent()
        !compileTime.intValue(Every, "anInt").isPresent()
        reflective.getDefaultValue(Every.name, "anInt", Integer).get() == 3
        reflective.getDefaultValue(Every.name, "anInt", Integer) == compileTime.getDefaultValue(Every.name, "anInt", Integer)
    }

    void "a member of every kind that is written is converted the same, built either way"() {
        given:
        def compileTime = BeanIntrospector.SHARED.getIntrospection(EveryKindBean)
            .getRequiredProperty("annotated", String).getAnnotationMetadata()
        def reflective = ReflectionAnnotations.metadataOf(EveryKindBean.getDeclaredField("annotated"))

        expect:
        reflective.intValue(Every, "anInt") == compileTime.intValue(Every, "anInt")
        reflective.stringValues(Every, "strings").toList() == compileTime.stringValues(Every, "strings").toList()
        reflective.classValues(Every, "classes").toList() == compileTime.classValues(Every, "classes").toList()

        and: "and every member of the annotation on the type, which sets an enum and a class"
        def onType = ReflectionAnnotations.metadataOf(EveryKindBean)
        def onTypeGenerated = BeanIntrospector.SHARED.getIntrospection(EveryKindBean).getAnnotationMetadata()
        onType.enumValue(Every, "anEnum", Level) == onTypeGenerated.enumValue(Every, "anEnum", Level)
        onType.stringValue(Every, "aString") == onTypeGenerated.stringValue(Every, "aString")
    }

    void "the occurrences of a repeatable annotation are held in a set rather than an array"() {
        given: "a property carrying a repeatable annotation twice"
        def compileTime = BeanIntrospector.SHARED.getIntrospection(RetainedBean)
            .getRequiredProperty("tagged", String).getAnnotationMetadata()
        def reflective = ReflectionAnnotations.metadataOf(RetainedBean.getDeclaredField("tagged"))

        expect: "the occurrences and their values are the same"
        canonical(reflective.getValues(Tags.name).get("value")) == canonical(compileTime.getValues(Tags.name).get("value"))

        and: "but a generated metadata holds them in an array, where one built at runtime holds them in a set"
        compileTime.getValues(Tags.name).get("value").getClass().isArray()
        reflective.getValues(Tags.name).get("value") instanceof Set

        and: "which is MutableAnnotationMetadata rather than anything this module does, and is why the string"
        and: "accessors of such a member are left out of the sweep above"
        reflective instanceof io.micronaut.inject.annotation.MutableAnnotationMetadata
        compileTime instanceof io.micronaut.inject.annotation.DefaultAnnotationMetadata
    }

    void "the declared view of the property #property answers the same, built either way"() {
        given:
        def compileTime = BeanIntrospector.SHARED.getIntrospection(RetainedBean)
            .getRequiredProperty(property, String).getAnnotationMetadata().getDeclaredMetadata()
        def reflective = ReflectionAnnotations.metadataOf(RetainedBean.getDeclaredField(property)).getDeclaredMetadata()

        expect:
        dump(property, "declared", reflective, compileTime)

        where:
        property << ["name", "code", "labelled", "spread", "tagged", "bare", "plain", "duplicated"]
    }

    void "an explicitly written repeatable container keeps all of its members for #property"() {
        given:
        def compileTime = BeanIntrospector.SHARED.getIntrospection(RetainedBean)
            .getRequiredProperty(property, String).getAnnotationMetadata()
        def reflective = ReflectionAnnotations.metadataOf(RetainedBean.getDeclaredField(property))

        expect:
        canonical(reflective.getAnnotation(Tags)) == canonical(compileTime.getAnnotation(Tags))

        where:
        property << ["explicitTags", "emptyTags"]
    }

    void "a member explicitly written as its default has the same presence, built either way"() {
        given:
        def compileTime = BeanIntrospector.SHARED.getIntrospection(EveryKindBean)
            .getRequiredProperty("explicitlyDefaulted", String).getAnnotationMetadata()
        def reflective = ReflectionAnnotations.metadataOf(EveryKindBean.getDeclaredField("explicitlyDefaulted"))

        expect:
        verifyAll {
            canonical(reflective.getValues(Every.name)) == canonical(compileTime.getValues(Every.name))
            reflective.getValue(Every.name, "anInt", Integer) == compileTime.getValue(Every.name, "anInt", Integer)
            reflective.intValue(Every, "anInt") == compileTime.intValue(Every, "anInt")
            reflective.isPresent(Every.name, "anInt") == compileTime.isPresent(Every.name, "anInt")
        }
    }

    void "an alias override cascades through every composed stereotype, built either way"() {
        given:
        def compileTime = BeanIntrospector.SHARED.getIntrospection(RetainedBean)
            .getRequiredProperty("deepAlias", String).getAnnotationMetadata()
        def reflective = ReflectionAnnotations.metadataOf(RetainedBean.getDeclaredField("deepAlias"))

        expect:
        verifyAll {
            reflective.intValue(DeepB, "min") == compileTime.intValue(DeepB, "min")
            reflective.intValue(DeepC, "min") == compileTime.intValue(DeepC, "min")
            reflective.intValue(DeepLimit, "min") == compileTime.intValue(DeepLimit, "min")
        }
    }

    void "an alias override cascades through the retained stereotype tree, built either way"() {
        given:
        def compileTime = BeanIntrospector.SHARED.getIntrospection(RetainedBean)
            .getRequiredProperty("deepAlias", String).getAnnotationMetadata()
        def reflective = ReflectionAnnotations.metadataOf(RetainedBean.getDeclaredField("deepAlias"))

        expect:
        retainedTree(reflective.getAnnotation(DeepA)) == retainedTree(compileTime.getAnnotation(DeepA))
    }

    private static Object retainedTree(AnnotationValue<?> value) {
        if (value == null) {
            return null
        }
        return [name: value.annotationName,
                values: canonical(value.values.findAll { it.key.toString() != '$stereotypes' }),
                stereotypes: value.stereotypes?.collect { retainedTree(it) }]
    }
}
