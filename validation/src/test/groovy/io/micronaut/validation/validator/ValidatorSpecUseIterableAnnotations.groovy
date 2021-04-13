package io.micronaut.validation.validator

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.validation.ElementKind
import javax.validation.constraints.Max
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

class ValidatorSpecUseIterableAnnotations extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run(
            'micronaut.validator.useIterableAnnotationsForIterableValues': true
    )

    @Shared
    Validator validator = applicationContext.getBean(Validator)

    void "test element validation in String collection" () {
        when:
        ListOfStrings strings = new ListOfStrings(strings: ["", null, "a string that's too long"])
        def violations = validator.validate(strings)
        violations = violations.sort{it -> it.getPropertyPath().toString() }

        then:
        // should be: two for violating element size, and one null string violation
        violations.size() == 3
        violations[0].constraintDescriptor
        violations[0].constraintDescriptor.annotation instanceof Size

        violations[1].constraintDescriptor
        violations[1].constraintDescriptor.annotation instanceof NotNull

        violations[2].constraintDescriptor
        violations[2].constraintDescriptor.annotation instanceof Size
    }

    void "test cascade to bean - enum"() {
        given:
        EnumList b = new EnumList(enums: [null])

        def violations = validator.validate(b)

        expect:
        violations.size() == 1
        violations.first().message == "must not be null"
    }

    void "test cascade to list - handle cycle"() {
        given:
        Book b = new Book(
                title: "The Stand",
                pages: 1000,
                primaryAuthor: new Author(age: 50, name: "Stephen King"),
                authors: [new Author(age: 150)]
        )
        b.authors[0].favouriteBook = b // create cycle
        def violations = validator.validate(b).sort { it.propertyPath.iterator().next().name }

        def v1 = violations.find { it.propertyPath.toString().contains('age')}
        def v2 = violations.find { it.propertyPath.toString().contains('name')}

        expect:
        violations.size() == 2
        v1.messageTemplate == '{javax.validation.constraints.Max.message}'
        v1.invalidValue == 150
        v1.propertyPath.size() == 3
        v1.propertyPath[0].kind == ElementKind.CONTAINER_ELEMENT
        v1.propertyPath[0].isInIterable()
        v1.propertyPath[0].index == 0
        !v1.propertyPath[1].isInIterable()
        v1.propertyPath[1].index == null
        v1.propertyPath[1].kind == ElementKind.CONTAINER_ELEMENT
        !v1.propertyPath[2].isInIterable()
        v1.propertyPath[2].index == null
        v1.propertyPath[2].kind == ElementKind.PROPERTY
        v1.rootBean.is(b)
        v1.leafBean instanceof Author
        v1.constraintDescriptor != null
        v1.constraintDescriptor.annotation instanceof Max
        v1.constraintDescriptor.annotation.value() == 100l

        v2.messageTemplate == '{javax.validation.constraints.NotBlank.message}'
        v2.constraintDescriptor != null
        v2.constraintDescriptor.annotation instanceof NotBlank
    }

    void "test cascade to container of non-introspected class" () {
        when:
        Bee notIntrospected = new Bee(name: "")
        HiveOfBeeList beeHive = new HiveOfBeeList(bees: [notIntrospected])
        def violations = validator.validate(beeHive)

        then:
        violations.size() == 1
        !violations[0].constraintDescriptor
        violations[0].message == "Cannot validate io.micronaut.validation.validator.Bee. No bean introspection present. " +
                "Please add @Introspected to the class and ensure Micronaut annotation processing is enabled"

        when:
        beeHive = new HiveOfBeeList(bees: [null])
        violations = validator.validate(beeHive)

        then:
        violations.size() == 1
        !violations[0].constraintDescriptor
        violations[0].message == "Cannot validate io.micronaut.validation.validator.Bee. No bean introspection present. " +
                "Please add @Introspected to the class and ensure Micronaut annotation processing is enabled"
    }

    void "test cascade to map of non-introspected value class" () {
        when:
        Bee notIntrospected = new Bee(name: "")
        HiveOfBeeMap beeHive = new HiveOfBeeMap(bees: ["blank" : notIntrospected])
        def violations = validator.validate(beeHive)

        then:
        violations.size() == 1
        !violations[0].constraintDescriptor
        violations[0].message == "Cannot validate io.micronaut.validation.validator.Bee. No bean introspection present. " +
                "Please add @Introspected to the class and ensure Micronaut annotation processing is enabled"

        when:
        Map<String, Bee> map = [:]
        map.put("blank", null)
        beeHive = new HiveOfBeeMap(bees: map)
        violations = validator.validate(beeHive)

        then:
        violations.size() == 1
        !violations[0].constraintDescriptor
        violations[0].message == "Cannot validate io.micronaut.validation.validator.Bee. No bean introspection present. " +
                "Please add @Introspected to the class and ensure Micronaut annotation processing is enabled"
    }

    // ARRAY CASCADES

    void "test cascade to array elements"() {
        given:
        ArrayTest arrayTest = new ArrayTest(integers: [30, 10, 60] as int[])
        def violations = validator.validate(arrayTest)
        violations = violations.sort{it -> it.getPropertyPath().toString() }

        expect:
        violations.size() == 2
        violations[0].invalidValue == 30
        violations[0].propertyPath.toList()[0].index == 0
        violations[0].propertyPath.toString() == "integers[0]"
        violations[0].constraintDescriptor != null
        violations[0].constraintDescriptor.annotation instanceof Max

        violations[1].invalidValue == 60
        violations[1].propertyPath.toList()[0].index == 2
        violations[1].propertyPath.toString() == "integers[2]"
        violations[1].constraintDescriptor != null
        violations[1].constraintDescriptor.annotation instanceof Max
    }

    void "test cascade to array elements - nested"() {
        when:
        ArrayTest arrayTest = new ArrayTest(integers: [10, 15] as int[], child: new ArrayTest(integers: [10, 60] as int[]))
        def violations = validator.validate(arrayTest).toList()

        then:
        violations.size() == 1
        violations[0].propertyPath.toString() == 'child.integers[1]'
        violations[0].constraintDescriptor != null
        violations[0].constraintDescriptor.annotation instanceof  Max


        when:
        arrayTest = new ArrayTest(
                integers: [10, 15] as int[],
                child: new ArrayTest(integers: [10, 15] as int[], child: new ArrayTest(integers: [10, 60] as int[])))
        violations = validator.validate(arrayTest).toList()

        then:
        violations.size() == 1
        violations[0].propertyPath.toString() == 'child.child.integers[1]'
        violations[0].constraintDescriptor != null
        violations[0].constraintDescriptor.annotation instanceof  Max
    }

    void "test cascade to array elements null"() {
        when:
        ArrayTest arrayTest = new ArrayTest(integers: [null])
        def violations = validator.validate(arrayTest)

        then:
        violations.size() == 1
        violations.first().messageTemplate == '{javax.validation.constraints.NotNull.message}'
        violations.first().propertyPath.toString() == 'integers[0]'
        violations.first().constraintDescriptor != null
        violations.first().constraintDescriptor.annotation instanceof  NotNull

        when:
        arrayTest = new ArrayTest(integers: null)
        violations = validator.validate(arrayTest)

        then:
        violations.size() == 0
    }

    void "test executable validator - cascade to array"() {
        when:
        ArrayTest arrayTest = applicationContext.getBean(ArrayTest)
        def constraintViolations = validator.forExecutables().validateParameters(
                arrayTest,
                ArrayTest.getDeclaredMethod("saveIntArray", int[].class),
                [[30, 10, 60] as int[]] as Object[]
        ).toList().sort({ it.propertyPath.toString() })


        then:
        constraintViolations.size() == 2
        constraintViolations[0].propertyPath.toString() == 'saveIntArray.integers[0]'
        constraintViolations[0].constraintDescriptor != null
        constraintViolations[0].constraintDescriptor.annotation instanceof  Max
        constraintViolations[1].propertyPath.toString() == 'saveIntArray.integers[2]'
        constraintViolations[1].constraintDescriptor != null
        constraintViolations[1].constraintDescriptor.annotation instanceof  Max

        when:
        arrayTest = applicationContext.createBean(ArrayTest)
        arrayTest.integers = [30,10,60] as int[]
        def violations = validator.forExecutables().validateParameters(
                new ArrayTest(),
                ArrayTest.getDeclaredMethod("saveChild", ArrayTest.class),
                [arrayTest] as Object[]
        ).toList().sort({ it -> it.propertyPath.toString() })

        then:
        violations.size() == 2
        violations[0].propertyPath.toString() == 'saveChild.arrayTest.integers[0]'
        violations[0].constraintDescriptor != null
        violations[0].constraintDescriptor.annotation instanceof  Max
        violations[1].propertyPath.toString() == 'saveChild.arrayTest.integers[2]'
        violations[1].constraintDescriptor != null
        violations[1].constraintDescriptor.annotation instanceof  Max

    }
}
