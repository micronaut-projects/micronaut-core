package io.micronaut.core.annotation

import spock.lang.Specification

class AnnotationValueSpec extends Specification {

    void "test toString()"() {
        given:
        def av = AnnotationValue.builder("test.Foo")
            .value(10).build()

        expect:
        av.toString() == "@test.Foo(value=10)"
    }

    void "toString() renders the members in name order, whichever order they are held in"() {
        given: "the same annotation with its members held in either order"
        def declared = new AnnotationValue("test.Sized", [min: 2, max: 4] as Map<CharSequence, Object>)
        def reversed = new AnnotationValue("test.Sized", [max: 4, min: 2] as Map<CharSequence, Object>)

        expect: "a builder fills the map in whichever order it reads the members, which the rendering does not follow"
        declared.toString() == "@test.Sized(max=4, min=2)"
        reversed.toString() == declared.toString()
    }

    /**
     * A metadata built at runtime accumulates the occurrences of a repeatable annotation in a collection, where a
     * generated one holds them in an array. The accessors that map over a member value by value are to answer the
     * same over either shape, rather than stringifying the whole collection as one value.
     */
    void "the accessors read a member held in a collection value by value, as they read an array"() {
        given:
        def one = AnnotationValue.builder("test.Tag").value("one").build()
        def two = AnnotationValue.builder("test.Tag").value("two").build()
        def collected = new AnnotationValue("test.Tags", [value: [one, two] as LinkedHashSet] as Map<CharSequence, Object>)
        def arrayed = new AnnotationValue("test.Tags", [value: [one, two] as AnnotationValue[]] as Map<CharSequence, Object>)

        expect: "one string per occurrence, rather than one string holding the whole collection"
        collected.stringValues("value") == arrayed.stringValues("value")
        collected.stringValues("value").length == 2

        and: "the same for the accessors that parse each string"
        def numbers = new AnnotationValue("test.Nums", [value: ["1", "2"] as LinkedHashSet] as Map<CharSequence, Object>)
        numbers.intValues("value") == [1, 2] as int[]
        numbers.longValues("value") == [1L, 2L] as long[]
        numbers.doubleValues("value") == [1d, 2d] as double[]

        and: "and for the ones that read a member holding classes"
        def classes = new AnnotationValue("test.Types", [value: [new AnnotationClassValue<Object>(AnnotationValueSpec),
                                                                new AnnotationClassValue<Object>(Specification)] as LinkedHashSet] as Map<CharSequence, Object>)
        classes.classValues("value") == [AnnotationValueSpec, Specification] as Class[]
        classes.annotationClassValues("value").length == 2

        and: "the singular accessors answer from the first occurrence, as they do over an array"
        collected.stringValue("value") == arrayed.stringValue("value")
        collected.getAnnotation("value") == arrayed.getAnnotation("value")
        collected.getAnnotations("value") == arrayed.getAnnotations("value")
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
     * Seeding a builder from a retaining value and adding a stereotype is the shape an integration takes to opt
     * an annotation in: {@code micronaut-validation}'s remapper does exactly
     * {@code annotation.mutate().stereotype(...).build()}. The added stereotype has to reach the member too,
     * otherwise the rebuilt value writes the tree it was seeded with and silently drops the addition.
     */
    void "a stereotype added to a seeded builder reaches the member as well as the field"() {
        given:
        def size = AnnotationValue.builder("jakarta.validation.constraints.Size").member("min", 3).build()
        def notNull = AnnotationValue.builder("jakarta.validation.constraints.NotNull").build()
        def retaining = new AnnotationValue("test.Composed", [min: 3, (AnnotationUtil.STEREOTYPES_MEMBER): [size] as AnnotationValue[]] as Map<CharSequence, Object>)

        when:
        def rebuilt = retaining.mutate().stereotype(notNull).build()

        then: "the addition is readable"
        rebuilt.getStereotypes() == [size, notNull]

        and: "and is in the member the writer emits, not only in the transient field"
        rebuilt.getAnnotations(AnnotationUtil.STEREOTYPES_MEMBER) == [size, notNull]
    }

    /**
     * A builder that was never seeded from a retaining value must stay untouched, because the transient field
     * carries the mapping protocol there: null means "fill the stereotypes from the annotation definition" and
     * empty means "skip". Writing the member for those would turn a mapping instruction into retained state.
     */
    void "a builder not seeded from a retaining value gains no reserved member"() {
        given:
        def size = AnnotationValue.builder("jakarta.validation.constraints.Size").member("min", 3).build()

        expect: "a stereotype on a fresh builder stays in the field only"
        def built = AnnotationValue.builder("test.Composed").stereotype(size).build()
        built.getStereotypes() == [size]
        !built.contains(AnnotationUtil.STEREOTYPES_MEMBER)

        and: "and a plain value seeded from a non-retaining one gains nothing"
        !AnnotationValue.builder(new AnnotationValue("test.Plain", [min: 3] as Map<CharSequence, Object>))
                .build()
                .contains(AnnotationUtil.STEREOTYPES_MEMBER)
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

    void "a member held in a collection is read through every branch an array is read through"() {
        given:
        def bar = AnnotationValue.builder(Bar).build()

        expect: "a collection of class names reads as class values, as an array of them does"
        def names = new AnnotationValue("test.Types", [value: ["java.lang.String", "java.util.Random"] as LinkedHashSet] as Map<CharSequence, Object>)
        names.annotationClassValues("value")*.name == ["java.lang.String", "java.util.Random"]
        names.annotationClassValues("absent").length == 0

        and: "a collection holding only annotations is read as the array of them a generated metadata would hold"
        def annotations = new AnnotationValue("test.Types", [value: [bar] as LinkedHashSet] as Map<CharSequence, Object>)
        annotations.classValues("value") == [] as Class[]
        annotations.annotationClassValues("value").length == 0

        and: "an empty collection holds nothing to read"
        def empty = new AnnotationValue("test.Types", [value: [] as LinkedHashSet] as Map<CharSequence, Object>)
        empty.classValues("value") == [] as Class[]
        !empty.getAnnotation("value").isPresent()

        and: "an entry bound to null is rendered as one, rather than failing"
        def nulls = new AnnotationValue("test.Strings", [value: ["a", null]] as Map<CharSequence, Object>)
        nulls.stringValues("value") == ["a", null] as String[]
    }

    void "getAnnotation() reads the first occurrence out of a collection, as it does out of an array"() {
        given:
        def bar = AnnotationValue.builder(Bar).build()
        def held = new AnnotationValue("test.Foo",
            [bars: [bar] as LinkedHashSet, plain: ["one"] as LinkedHashSet] as Map<CharSequence, Object>)

        expect: "the occurrence is read when its type matches, and not when it does not"
        held.getAnnotation("bars", Bar).get() == bar
        !held.getAnnotation("bars", Baz).isPresent()
        held.getAnnotation("bars").get() == bar

        and: "a collection holding something other than an annotation holds no occurrence"
        !held.getAnnotation("plain", Bar).isPresent()
        !held.getAnnotation("plain").isPresent()

        and: "nor does an empty collection, or a member the annotation does not bind"
        def empty = new AnnotationValue("test.Foo", [bars: [] as LinkedHashSet] as Map<CharSequence, Object>)
        !empty.getAnnotation("bars", Bar).isPresent()
        !held.getAnnotation("missing", Bar).isPresent()
        !held.getAnnotation("missing").isPresent()
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

    void "test matches() leaves out the non-binding members either side lists"() {
        given: "a compiled occurrence listing its non-binding member, and one built at runtime without the list"
        def defaults = [note: ""] as Map<CharSequence, Object>
        def compiled = new AnnotationValue("test.Noted", [
                value                                 : "one",
                note                                  : "first",
                (AnnotationUtil.NON_BINDING_ATTRIBUTE): ["note", AnnotationUtil.NON_BINDING_ATTRIBUTE] as String[]
        ] as Map<CharSequence, Object>, defaults)
        def built = new AnnotationValue("test.Noted", [value: "one", note: "other"] as Map<CharSequence, Object>, defaults)
        def omitted = new AnnotationValue("test.Noted", [value: "one"] as Map<CharSequence, Object>, defaults)

        expect: "the side without the list lacks the information, so the list of the other side applies, both ways"
        compiled.matches(built)
        built.matches(compiled)
        compiled.matches(omitted)
        omitted.matches(compiled)

        and: "a binding member still decides"
        !compiled.matches(new AnnotationValue("test.Noted", [value: "two", note: "first"] as Map<CharSequence, Object>, defaults))
        !new AnnotationValue("test.Noted", [value: "two", note: "first"] as Map<CharSequence, Object>, defaults).matches(compiled)

        and: "with neither side listing the member it is compared"
        !built.matches(new AnnotationValue("test.Noted", [value: "one", note: "first"] as Map<CharSequence, Object>, defaults))
    }

    void "test matches() leaves out the union of the non-binding members both sides list"() {
        given: "two occurrences each listing a different non-binding member"
        def left = new AnnotationValue("test.Noted", [
                value                                 : "one",
                note                                  : "left",
                debug                                 : true,
                (AnnotationUtil.NON_BINDING_ATTRIBUTE): ["note"] as String[]
        ] as Map<CharSequence, Object>)
        def right = new AnnotationValue("test.Noted", [
                value                                 : "one",
                note                                  : "right",
                debug                                 : false,
                (AnnotationUtil.NON_BINDING_ATTRIBUTE): ["debug", AnnotationUtil.NON_BINDING_ATTRIBUTE] as String[]
        ] as Map<CharSequence, Object>)

        expect: "the members either lists take no part, and the list itself is never a member"
        left.matches(right)
        right.matches(left)
    }

    void "test matches() fills the defaults of a value carrying none from the other side"() {
        given: "a compiled value carrying the defaults, and values built at runtime without them"
        def compiled = new AnnotationValue("test.Zone", [:] as Map<CharSequence, Object>, [value: "north"] as Map<CharSequence, Object>)
        def built = new AnnotationValue("test.Zone", [:] as Map<CharSequence, Object>, (Map<CharSequence, Object>) null)
        def south = new AnnotationValue("test.Zone", [value: "south"] as Map<CharSequence, Object>, (Map<CharSequence, Object>) null)
        def north = new AnnotationValue("test.Zone", [value: "north"] as Map<CharSequence, Object>, (Map<CharSequence, Object>) null)

        expect: "the side without defaults lacks the information, so the defaults of the other side apply, both ways"
        compiled.matches(built)
        built.matches(compiled)
        compiled.matches(north)
        north.matches(compiled)

        and: "a member declared as something else still differs"
        !compiled.matches(south)
        !south.matches(compiled)

        and: "an empty map of defaults is filled in as well"
        new AnnotationValue("test.Zone", [:] as Map<CharSequence, Object>, [:] as Map<CharSequence, Object>).matches(compiled)
        compiled.matches(new AnnotationValue("test.Zone", [:] as Map<CharSequence, Object>, [:] as Map<CharSequence, Object>))
    }

    void "test matches() compares a nested annotation through matches()"() {
        given: "a compiled nested occurrence listing its non-binding member and carrying its defaults"
        def compiledScope = new AnnotationValue("test.Scope", [
                value                                 : "x",
                note                                  : "compiled",
                (AnnotationUtil.NON_BINDING_ATTRIBUTE): ["note", AnnotationUtil.NON_BINDING_ATTRIBUTE] as String[]
        ] as Map<CharSequence, Object>, [note: "", level: "INFO"] as Map<CharSequence, Object>)
        def compiled = new AnnotationValue("test.Scoped", [scope: compiledScope] as Map<CharSequence, Object>)

        and: "nested occurrences built at runtime, without the list or the defaults"
        def built = scoped([value: "x", note: "built"])
        def explicit = scoped([value: "x", level: "INFO"])

        expect: "the nested non-binding member takes no part, and the nested defaults apply, both ways"
        compiled.matches(built)
        built.matches(compiled)
        compiled.matches(explicit)
        explicit.matches(compiled)

        and: "a nested binding member still decides"
        !compiled.matches(scoped([value: "y"]))
        !scoped([value: "y"]).matches(compiled)
        !compiled.matches(scoped([value: "x", level: "DEBUG"]))
    }

    void "test matches() compares the nested annotations of an array in order, whichever shape holds them"() {
        given:
        def compiledX = new AnnotationValue("test.Scope", [
                value                                 : "x",
                note                                  : "compiled",
                (AnnotationUtil.NON_BINDING_ATTRIBUTE): ["note"] as String[]
        ] as Map<CharSequence, Object>)
        def compiledY = new AnnotationValue("test.Scope", [value: "y"] as Map<CharSequence, Object>)
        def compiled = new AnnotationValue("test.Scoped", [value: [compiledX, compiledY] as AnnotationValue[]] as Map<CharSequence, Object>)
        def x = new AnnotationValue("test.Scope", [value: "x", note: "built"] as Map<CharSequence, Object>)
        def y = new AnnotationValue("test.Scope", [value: "y"] as Map<CharSequence, Object>)

        expect: "an array, an Object[] and a collection of the same occurrences match"
        compiled.matches(new AnnotationValue("test.Scoped", [value: [x, y] as AnnotationValue[]] as Map<CharSequence, Object>))
        compiled.matches(new AnnotationValue("test.Scoped", [value: [x, y] as Object[]] as Map<CharSequence, Object>))
        compiled.matches(new AnnotationValue("test.Scoped", [value: [x, y]] as Map<CharSequence, Object>))
        new AnnotationValue("test.Scoped", [value: [x, y]] as Map<CharSequence, Object>).matches(compiled)

        and: "the order and the number of the occurrences decide, as they do for an annotation instance"
        !compiled.matches(new AnnotationValue("test.Scoped", [value: [y, x] as AnnotationValue[]] as Map<CharSequence, Object>))
        !compiled.matches(new AnnotationValue("test.Scoped", [value: [x] as AnnotationValue[]] as Map<CharSequence, Object>))
        !compiled.matches(new AnnotationValue("test.Scoped", [value: [x, y, y] as AnnotationValue[]] as Map<CharSequence, Object>))
    }

    void "test matches() compares containers of nested annotations whichever shape holds them, empty or not"() {
        given:
        def x = new AnnotationValue("test.Scope", [value: "x"] as Map<CharSequence, Object>)

        expect: "an empty array and an empty collection hold the same nothing"
        new AnnotationValue("test.Scoped", [value: [] as AnnotationValue[]] as Map<CharSequence, Object>)
                .matches(new AnnotationValue("test.Scoped", [value: []] as Map<CharSequence, Object>))
        new AnnotationValue("test.Scoped", [value: []] as Map<CharSequence, Object>)
                .matches(new AnnotationValue("test.Scoped", [value: [] as AnnotationValue[]] as Map<CharSequence, Object>))

        and: "a nested annotation is not a string, whichever side it is on"
        !new AnnotationValue("test.Scoped", [value: x] as Map<CharSequence, Object>)
                .matches(new AnnotationValue("test.Scoped", [value: "x"] as Map<CharSequence, Object>))
        !new AnnotationValue("test.Scoped", [value: "x"] as Map<CharSequence, Object>)
                .matches(new AnnotationValue("test.Scoped", [value: x] as Map<CharSequence, Object>))

        and: "a single string still differs from an array holding it, as before"
        !new AnnotationValue("test.Named", [value: "x"] as Map<CharSequence, Object>)
                .matches(new AnnotationValue("test.Named", [value: ["x"] as String[]] as Map<CharSequence, Object>))
    }

    private static AnnotationValue<?> scoped(Map<String, Object> scope) {
        return new AnnotationValue("test.Scoped", [
                scope: new AnnotationValue("test.Scope", scope as Map<CharSequence, Object>, (Map<CharSequence, Object>) null)
        ] as Map<CharSequence, Object>)
    }

    void "test matches() compares an empty string with the default it replaces"() {
        given: "an annotation whose member defaults to something other than the empty string"
        def defaults = [value: "default"] as Map<CharSequence, Object>
        def empty = new AnnotationValue("test.Named", [value: ""] as Map<CharSequence, Object>, defaults)
        def omitted = new AnnotationValue("test.Named", [:] as Map<CharSequence, Object>, defaults)

        expect: "an empty string is a value of its own, the way an annotation instance compares it"
        !empty.matches(omitted)
        !omitted.matches(empty)
        empty.matches(new AnnotationValue("test.Named", [value: ""] as Map<CharSequence, Object>, defaults))
    }

    void "test matches() takes a member bound to the empty string for one left out with no default recorded"() {
        given: "compiled metadata records no default of the empty string, so a member defaulting to it is left out"
        def compiled = new AnnotationValue("test.Zone", [:] as Map<CharSequence, Object>, [region: "eu"] as Map<CharSequence, Object>)
        def empty = new AnnotationValue("test.Zone", [value: ""] as Map<CharSequence, Object>, (Map<CharSequence, Object>) null)
        def leftOut = new AnnotationValue("test.Zone", [:] as Map<CharSequence, Object>, (Map<CharSequence, Object>) null)

        expect:
        empty.matches(leftOut)
        leftOut.matches(empty)
        empty.matches(compiled)
        compiled.matches(empty)
        new AnnotationValue("test.Zone", [value: "", region: "eu"] as Map<CharSequence, Object>, [region: "eu"] as Map<CharSequence, Object>).matches(compiled)
        compiled.matches(new AnnotationValue("test.Zone", [value: "", region: "eu"] as Map<CharSequence, Object>, [region: "eu"] as Map<CharSequence, Object>))

        and: "any other value of a member the other side leaves out with no default still differs"
        !new AnnotationValue("test.Zone", [value: "x"] as Map<CharSequence, Object>).matches(compiled)
        !compiled.matches(new AnnotationValue("test.Zone", [value: "x"] as Map<CharSequence, Object>))
        !new AnnotationValue("test.Zone", [value: [] as String[]] as Map<CharSequence, Object>).matches(leftOut)
        !leftOut.matches(new AnnotationValue("test.Zone", [value: [] as String[]] as Map<CharSequence, Object>))
    }

    void "test matches() compares an enum constant with the name compiled metadata holds"() {
        given: "a value built at runtime keeping the constant, and the compiled value holding its name"
        def built = AnnotationValue.builder("test.Tier").member("level", MatchedLevel.GOLD).build()
        def compiled = new AnnotationValue("test.Tier", [level: "GOLD"] as Map<CharSequence, Object>)

        expect:
        built.matches(compiled)
        compiled.matches(built)
        !built.matches(new AnnotationValue("test.Tier", [level: "SILVER"] as Map<CharSequence, Object>))

        and: "an array of constants matches the array of their names"
        AnnotationValue.builder("test.Tier").member("levels", MatchedLevel.GOLD, MatchedLevel.SILVER).build()
                .matches(new AnnotationValue("test.Tier", [levels: ["GOLD", "SILVER"] as String[]] as Map<CharSequence, Object>))
        new AnnotationValue("test.Tier", [levels: ["GOLD", "SILVER"] as String[]] as Map<CharSequence, Object>)
                .matches(AnnotationValue.builder("test.Tier").member("levels", MatchedLevel.GOLD, MatchedLevel.SILVER).build())
        !AnnotationValue.builder("test.Tier").member("levels", MatchedLevel.SILVER, MatchedLevel.GOLD).build()
                .matches(new AnnotationValue("test.Tier", [levels: ["GOLD", "SILVER"] as String[]] as Map<CharSequence, Object>))
    }

    void "test matches() compares a class with the class value or name compiled metadata holds"() {
        given:
        def compiled = new AnnotationValue("test.Typed", [value: new AnnotationClassValue<>(String.name)] as Map<CharSequence, Object>)

        expect: "a class put in the members as it is, the class value and the class name are the same member"
        new AnnotationValue("test.Typed", [value: String] as Map<CharSequence, Object>).matches(compiled)
        compiled.matches(new AnnotationValue("test.Typed", [value: String] as Map<CharSequence, Object>))
        new AnnotationValue("test.Typed", [value: String.name] as Map<CharSequence, Object>).matches(compiled)
        AnnotationValue.builder("test.Typed").member("value", String).build().matches(compiled)
        !new AnnotationValue("test.Typed", [value: Integer] as Map<CharSequence, Object>).matches(compiled)

        and: "an array of classes matches the array of their class values"
        new AnnotationValue("test.Typed", [value: [String, Integer] as Class[]] as Map<CharSequence, Object>)
                .matches(new AnnotationValue("test.Typed", [value: [new AnnotationClassValue<>(String.name), new AnnotationClassValue<>(Integer.name)] as AnnotationClassValue[]] as Map<CharSequence, Object>))
    }

    void "test matches() compares a primitive array with the boxed values"() {
        given:
        def compiled = new AnnotationValue("test.Ports", [value: [1, 2] as int[]] as Map<CharSequence, Object>)

        expect:
        compiled.matches(new AnnotationValue("test.Ports", [value: [1, 2] as Integer[]] as Map<CharSequence, Object>))
        new AnnotationValue("test.Ports", [value: [1, 2] as Integer[]] as Map<CharSequence, Object>).matches(compiled)
        compiled.matches(new AnnotationValue("test.Ports", [value: [1, 2]] as Map<CharSequence, Object>))
        compiled.matches(new AnnotationValue("test.Ports", [value: [1, 2] as int[]] as Map<CharSequence, Object>))
        !compiled.matches(new AnnotationValue("test.Ports", [value: [2, 1] as Integer[]] as Map<CharSequence, Object>))
        !compiled.matches(new AnnotationValue("test.Ports", [value: [1, 2, 3] as int[]] as Map<CharSequence, Object>))
        !compiled.matches(new AnnotationValue("test.Ports", [value: 1] as Map<CharSequence, Object>))
    }

    void "mutate().defaultValues() works on a value built without defaults"() {
        when:
        AnnotationValue<?> av = AnnotationValue.builder("x.Y").member("a", 1).build()
                .mutate().defaultValues(Map.of("a", 0, "b", "z")).build()

        then:
        av.intValue("a").getAsInt() == 1
        av.getDefaultValues() == [a: 0, b: "z"]
        av.get("b", String).get() == "z"

        when: "the source was created with the two-argument constructor"
        av = new AnnotationValue<>("x.Y", [a: 1]).mutate().defaultValues([a: 0]).build()

        then:
        av.getDefaultValues() == [a: 0]
    }

    void "mutate().defaultValues() does not write into the source value's defaults"() {
        given:
        Map<CharSequence, Object> defaults = Collections.unmodifiableMap([a: 0] as Map<CharSequence, Object>)
        AnnotationValue<?> source = new AnnotationValue<>("x.Y", [a: 1] as Map<CharSequence, Object>, defaults)

        when:
        AnnotationValue<?> mutated = source.mutate().defaultValues([b: 2]).build()

        then:
        mutated.getDefaultValues() == [a: 0, b: 2]
        source.getDefaultValues() == [a: 0]
    }
}

@interface Bar {}

@interface Baz {}

enum MatchedLevel { GOLD, SILVER }
