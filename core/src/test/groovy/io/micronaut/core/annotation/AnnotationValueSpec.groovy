package io.micronaut.core.annotation

import spock.lang.PendingFeature
import spock.lang.Specification

class AnnotationValueSpec extends Specification {

    void "test toString()"() {
        given:
        def av = AnnotationValue.builder("test.Foo")
            .value(10).build()

        expect:
        av.toString() == "@test.Foo(value=10)"
    }

    void "the reserved stereotypes member is read through getStereotypes() and hidden from the attributes"() {
        given:
        def size = AnnotationValue.builder("jakarta.validation.constraints.Size").member("min", 3).build()
        def retaining = new AnnotationValue("test.Composed", [min: 3, (AnnotationUtil.STEREOTYPES_MEMBER): [size] as AnnotationValue[]] as Map<CharSequence, Object>)
        def plain = new AnnotationValue("test.Composed", [min: 3] as Map<CharSequence, Object>)

        expect: "the member is the stereotypes"
        retaining.getStereotypes() == [size]
        plain.getStereotypes() == null

        and: "it is not an attribute"
        retaining.getValues() == [min: 3]
        retaining.getMemberNames() == ["min"] as Set
        retaining.toString() == "@test.Composed(min=3)"
        retaining.contains(AnnotationUtil.STEREOTYPES_MEMBER)

        and: "it takes no part in equality"
        retaining == plain
        retaining.hashCode() == plain.hashCode()

        and: "it survives mutation"
        retaining.mutate().member("min", 4).build().getStereotypes() == [size]
    }

    /**
     * {@code AnnotationValueBuilder} copies the values it is seeded with from {@link AnnotationValue#getValues()},
     * which hides the reserved member, and separately copies {@link AnnotationValue#getStereotypes()} into the
     * transient {@code stereotypes} field. So {@code mutate()} and {@code AnnotationValue.builder(value)} move
     * the tree out of the member and into the field.
     *
     * <p>The field is the representation the writer does not emit — moving the tree there is what this PR set
     * out to avoid. Reads are unaffected, because {@code getStereotypes()} answers from the field, so a mutated
     * value looks correct in memory and silently writes no tree. The assertion above covers exactly that reading
     * path, which is what masks it.</p>
     *
     * <p>It also leaves the two accessors disagreeing: {@code getStereotypes()} answers while
     * {@code contains($stereotypes)} does not.</p>
     */
    @PendingFeature(reason = "AnnotationValueBuilder seeds itself from getValues(), which hides the reserved member")
    void "mutating an annotation keeps the stereotypes in the member the writer emits"() {
        given:
        def size = AnnotationValue.builder("jakarta.validation.constraints.Size").member("min", 3).build()
        def retaining = new AnnotationValue("test.Composed", [min: 3, (AnnotationUtil.STEREOTYPES_MEMBER): [size] as AnnotationValue[]] as Map<CharSequence, Object>)

        expect: "the value it is seeded from carries the member"
        retaining.contains(AnnotationUtil.STEREOTYPES_MEMBER)

        and: "and so does the mutated value, not only its transient field"
        retaining.mutate().member("min", 4).build().contains(AnnotationUtil.STEREOTYPES_MEMBER)

        and: "the same for a builder seeded from it"
        AnnotationValue.builder(retaining).build().contains(AnnotationUtil.STEREOTYPES_MEMBER)
    }

    /**
     * {@code convertibleValues} is built once in the constructor from the raw values map, before
     * {@link AnnotationValue#getValues()} gets the chance to hide the reserved member, so every bulk view
     * reached through {@link AnnotationValue#getConvertibleValues()} — {@code names()}, {@code values()},
     * {@code asMap()}, iteration — reports {@code $stereotypes} as if it were an attribute of the annotation.
     *
     * <p>Anything walking the members of an annotation generically goes through that view: it is what
     * {@code AnnotationValueResolver} is backed by. The member is hidden from {@code getValues()},
     * {@code getMemberNames()} and {@code toString()}, so this is the one hole left in "it is not an
     * attribute", and it widens the blast radius of the reserved member beyond the opted-in consumers.</p>
     */
    void "the reserved stereotypes member is hidden from the convertible values"() {
        given:
        def size = AnnotationValue.builder("jakarta.validation.constraints.Size").member("min", 3).build()
        def retaining = new AnnotationValue("test.Composed", [min: 3, (AnnotationUtil.STEREOTYPES_MEMBER): [size] as AnnotationValue[]] as Map<CharSequence, Object>)

        expect:
        retaining.getConvertibleValues().names() == ["min"] as Set
        retaining.getConvertibleValues().asMap().keySet() == ["min"] as Set
    }

    void "test get properties"() {
        given:
        def av = AnnotationValue.builder("test.Foo")
                        .member("props",
                                AnnotationValue.builder("test.Prop").member("name", "foo.bar1").value("one").build(),
                                AnnotationValue.builder("test.Prop").member("name", "foo.bar2").value("two").build()
                        )

                        .build()

        expect:
        av.getProperties("props") == ['foo.bar1':'one', 'foo.bar2':'two']
    }

    void "test class value"() {
        given:
        def av = AnnotationValue.builder("test.Foo")
                       .value(AnnotationValueSpec)
                       .build()

        expect:
        av.classValues() == [AnnotationValueSpec] as Class[]
        av.classValue().get() == AnnotationValueSpec
        av.classValue("value").get() == AnnotationValueSpec
        av.classValue("value", Specification).get() == AnnotationValueSpec
        !av.classValue("value", URL).isPresent()
    }

    void "test class values"() {
        given:
        def av = AnnotationValue.builder("test.Foo")
                .values(AnnotationValueSpec, Specification)
                .build()

        expect:
        av.classValues().contains(AnnotationValueSpec)
        av.classValues().contains(Specification)
        def annotationClassValues = av.annotationClassValues("value")
        annotationClassValues.length == 2
        annotationClassValues[0].type.present
        annotationClassValues[0].type.get() == AnnotationValueSpec
        annotationClassValues[1].type.present
        annotationClassValues[1].type.get() == Specification

        def optAnnotationClassValue = av.annotationClassValue("value")
        optAnnotationClassValue.present
        def annotationClassValue = optAnnotationClassValue.get()
        annotationClassValue.type.present
        annotationClassValue.type.get() == AnnotationValueSpec
    }

    void "test class value 2"() {
        given:
        def av = AnnotationValue.builder("test.Foo")
                .values(new AnnotationClassValue<Object>(AnnotationValueSpec), new AnnotationClassValue<Object>(Specification))
                .build()

        expect:
        av.classValues().length == 2
        av.classValues()[0] == AnnotationValueSpec
        av.classValues()[1] == Specification
        av.classValue().get() == AnnotationValueSpec
        av.classValue("value").get() == AnnotationValueSpec
        av.classValue("value", Specification).get() == AnnotationValueSpec
        !av.classValue("value", URL).isPresent()
    }

    void "test annotationClassValue"() {
        when:
        def av = AnnotationValue.builder("test.Foo")
                .member("missing", new String[] { "java.lang.String", "java.lang.Integer" })
                .build()
        then:
        def optValue = av.annotationClassValue("missing")
        optValue.present
        def value = optValue.get()
        value.name == 'java.lang.String'
        value.type.present
        value.type.get() == String

        when:
        av = AnnotationValue.builder("test.Foo")
                .member("required", "java.util.Random")
                .build()
        then:
        def optSecondValue = av.annotationClassValue("required")
        optSecondValue.present
        def secondValue = optSecondValue.get()
        secondValue.name == 'java.util.Random'
        secondValue.type.present
        secondValue.type.get() == Random

        when:
        av = AnnotationValue.builder("test.Foo")
                .member("absent", "org.something.NonExisting")
                .build()
        then:
        def optThirdValue = av.annotationClassValue("absent")
        optThirdValue.present
        def thirdValue = optThirdValue.get()
        thirdValue.name == 'org.something.NonExisting'
        !thirdValue.type.present
    }

    void "test INT value"() {
        given:
        def av = AnnotationValue.builder("test.Foo")
                .value(10)
                .build()

        expect:
        av.intValue().asInt == 10
    }

    void "test LONG value"() {
        given:
        def av = AnnotationValue.builder("test.Foo")
                .value(10)
                .member("str", "10")
                .build()

        expect:
        av.longValue().asLong == 10
        av.longValue("str").asLong == 10
    }

    void "test string value"() {
        given:
        def av = AnnotationValue.builder("test.Foo")
                .member("number", 10)
                .member("bool", true)
                .member("type", new AnnotationClassValue(Specification))
                .member("types", new AnnotationClassValue(Specification), new AnnotationClassValue(AnnotationValueSpec))
                .build()

        expect:
        av.stringValue("number").get() == "10"
        av.stringValue("bool").get() == "true"
        av.stringValue("type").get() == Specification.name
        av.stringValue("types").get() == Specification.name
        av.stringValues("number") == ["10"] as String[]
    }


    void "test INT value array"() {
        given:
        int[] ints = [10, 20]
        def av = AnnotationValue.builder("test.Foo")
                .values(ints)
                .build()

        expect:
        av.intValue().asInt == 10
    }

    void "test INT value strings"() {
        given:
        def av = AnnotationValue.builder("test.Foo")
                .values("10", "two")
                .build()

        expect:
        av.intValue().asInt == 10
    }

    void "test is true"() {
        given:
        def av = AnnotationValue.builder("test.Foo")
                .member("one", "y")
                .member("two", "true")
                .member("three", true)
                .member("four", false)
                .member("five", "false")
                .build()

        expect:
        av.isPresent("one")
        av.isTrue("one")
        av.isTrue("two")
        av.isTrue("three")
        !av.isFalse("one")
        !av.isFalse("two")
        !av.isFalse("three")
        !av.isTrue("four")
        !av.isTrue("five")
        !av.isTrue("six")
        av.isFalse("four")
        av.isFalse("five")
        av.isFalse("six")
    }

    void "test getAnnotation()"() {
        given:
        def innerAv = AnnotationValue.builder(Bar.class).build()
        def av = AnnotationValue.builder("test.Foo")
                .member("bar", innerAv)
                .member("bars", innerAv, innerAv)
                .build()

        expect:
        av.getAnnotation("bar", Bar.class).get() == innerAv
        av.getAnnotation("bars", Bar.class).get() == innerAv
    }
    void "test matches() fills the members neither value declared in from the defaults"() {
        given:
        def defaults = [level: "INFO"] as Map<CharSequence, Object>
        def declared = new AnnotationValue("test.Audited", [level: "INFO"] as Map<CharSequence, Object>, defaults)
        def omitted = new AnnotationValue("test.Audited", [:] as Map<CharSequence, Object>, defaults)
        def other = new AnnotationValue("test.Audited", [level: "DEBUG"] as Map<CharSequence, Object>, defaults)

        expect: "the declared default and the omitted one are the same annotation"
        declared.matches(omitted)
        omitted.matches(declared)

        and: "a member declared as something else is not"
        !declared.matches(other)
        !omitted.matches(other)

        and: "equals() still compares the members that were declared"
        declared != omitted
    }

    void "test matches() compares the annotation name"() {
        given:
        def av = new AnnotationValue("test.Foo", [value: 1] as Map<CharSequence, Object>)

        expect: "a value matches itself"
        av.matches(av)

        and:
        !av.matches(null)
        !av.matches(new AnnotationValue("test.Bar", [value: 1] as Map<CharSequence, Object>))
    }

    void "test matches() compares every member"() {
        given:
        def av = new AnnotationValue("test.Foo", [one: 1] as Map<CharSequence, Object>)

        expect: "a value with a member the other does not have is not the same annotation"
        !av.matches(new AnnotationValue("test.Foo", [one: 1, two: 2] as Map<CharSequence, Object>))
        !new AnnotationValue("test.Foo", [one: 1, two: 2] as Map<CharSequence, Object>).matches(av)

        and: "nor is one binding the same number of members under other names"
        !av.matches(new AnnotationValue("test.Foo", [two: 1] as Map<CharSequence, Object>))

        and: "nor one binding the same member to something else"
        !av.matches(new AnnotationValue("test.Foo", [one: 2] as Map<CharSequence, Object>))

        and:
        av.matches(new AnnotationValue("test.Foo", [one: 1] as Map<CharSequence, Object>))
    }

    void "test matches() compares a member bound to null"() {
        given:
        def nullValue = new AnnotationValue("test.Foo", [one: null] as Map<CharSequence, Object>)
        def value = new AnnotationValue("test.Foo", [one: 1] as Map<CharSequence, Object>)

        expect:
        nullValue.matches(new AnnotationValue("test.Foo", [one: null] as Map<CharSequence, Object>))
        !nullValue.matches(value)
        !value.matches(nullValue)
    }

    void "test matches() with no defaults to fill in"() {
        given: "one value carrying no defaults and one carrying an empty map of them"
        def noDefaults = new AnnotationValue("test.Foo", [one: 1] as Map<CharSequence, Object>, (Map<CharSequence, Object>) null)
        def emptyDefaults = new AnnotationValue("test.Foo", [one: 1] as Map<CharSequence, Object>, [:] as Map<CharSequence, Object>)

        expect: "the declared members are compared as they are"
        noDefaults.matches(emptyDefaults)
        emptyDefaults.matches(noDefaults)
        !noDefaults.matches(new AnnotationValue("test.Foo", [:] as Map<CharSequence, Object>, (Map<CharSequence, Object>) null))
    }
}

@interface Bar {}
