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
}

@interface Bar {}
