package io.micronaut.validation.validator

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.Introspected
import io.micronaut.validation.validator.resolver.CompositeTraversableResolver
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Singleton
import javax.validation.ElementKind
import javax.validation.Valid
import javax.validation.ValidatorFactory
import javax.validation.constraints.*
import javax.validation.metadata.BeanDescriptor

class ValidatorSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()
    @Shared
    Validator validator = applicationContext.getBean(Validator)

    void "test validator config"() {
        given:
        ValidatorConfiguration config = applicationContext.getBean(ValidatorConfiguration)
        ValidatorFactory factory = applicationContext.getBean(ValidatorFactory)

        expect:
        config.traversableResolver instanceof CompositeTraversableResolver
        factory instanceof DefaultValidatorFactory
    }

    void "test simple bean validation"() {
        given:
        Book b = new Book(title: "", pages: 50)
        def violations = validator.validate(b).sort { it.propertyPath.iterator().next().name }

        expect:
        violations.size() == 4
        violations[0].invalidValue == []
        violations[0].messageTemplate == '{javax.validation.constraints.Size.message}'
        violations[0].constraintDescriptor != null
        violations[0].constraintDescriptor.annotation instanceof Size
        violations[0].constraintDescriptor.annotation.min() == 1
        violations[0].constraintDescriptor.annotation.max() == 10
        violations[1].invalidValue == 50
        violations[1].propertyPath.iterator().next().name == 'pages'
        violations[1].rootBean == b
        violations[1].rootBeanClass == Book
        violations[1].messageTemplate == '{javax.validation.constraints.Min.message}'
        violations[1].constraintDescriptor != null
        violations[1].constraintDescriptor.annotation instanceof Min
        violations[1].constraintDescriptor.annotation.value() == 100l
        violations[2].invalidValue == null
        violations[2].propertyPath.iterator().next().name == 'primaryAuthor'
        violations[2].constraintDescriptor != null
        violations[2].constraintDescriptor.annotation instanceof NotNull
        violations[3].invalidValue == ''
        violations[3].propertyPath.iterator().next().name == 'title'
        violations[3].constraintDescriptor != null
        violations[3].constraintDescriptor.annotation instanceof NotBlank

    }


    void "test validate bean property"() {
        given:
        Book b = new Book(title: "", pages: 50)
        def violations = validator.validateProperty(b, "title").sort { it.propertyPath.iterator().next().name }

        expect:
        violations.size() == 1
        violations[0].invalidValue == ''
        violations[0].propertyPath.iterator().next().name == 'title'
        violations[0].constraintDescriptor != null
        violations[0].constraintDescriptor.annotation instanceof NotBlank

    }

    void "test validate value"() {
        given:
        def violations = validator.validateValue(Book, "title", "").sort { it.propertyPath.iterator().next().name }

        expect:
        violations.size() == 1
        violations[0].invalidValue == ''
        violations[0].propertyPath.iterator().next().name == 'title'
        violations[0].constraintDescriptor != null
        violations[0].constraintDescriptor.annotation instanceof NotBlank
    }

    void "test cascade to bean"() {
        given:
        Book b = new Book(title: "The Stand", pages: 1000, primaryAuthor: new Author(age: 150), authors: [new Author(name: "Stephen King", age: 50)])
        def violations = validator.validate(b).sort { it.propertyPath.iterator().next().name }

        def v1 = violations.find { it.propertyPath.toString() == 'primaryAuthor.age'}
        def v2 = violations.find { it.propertyPath.toString() == 'primaryAuthor.name'}
        expect:
        violations.size() == 2
        v1.messageTemplate == '{javax.validation.constraints.Max.message}'
        v1.propertyPath.toString() == 'primaryAuthor.age'
        v1.invalidValue == 150
        v1.rootBean.is(b)
        v1.leafBean instanceof Author
        v1.constraintDescriptor != null
        v1.constraintDescriptor.annotation instanceof Max
        v1.constraintDescriptor.annotation.value() == 100l
        v2.messageTemplate == '{javax.validation.constraints.NotBlank.message}'
        v2.propertyPath.toString() == 'primaryAuthor.name'
        v2.constraintDescriptor != null
        v2.constraintDescriptor.annotation instanceof NotBlank
    }

    void "test cascade to bean - no cycle"() {
        given:
        Book b = new Book(
                title: "The Stand",
                pages: 1000,
                primaryAuthor: new Author(age: 150),
                authors: [new Author(name: "Stephen King", age: 50)]
        )
        b.primaryAuthor.favouriteBook = b // create cycle
        def violations = validator.validate(b).sort { it.propertyPath.iterator().next().name }

        def v1 = violations.find { it.propertyPath.toString() == 'primaryAuthor.age'}
        def v2 = violations.find { it.propertyPath.toString() == 'primaryAuthor.name'}

        expect:
        violations.size() == 2
        v1.messageTemplate == '{javax.validation.constraints.Max.message}'
        v1.propertyPath.toString() == 'primaryAuthor.age'
        v1.invalidValue == 150
        v1.rootBean.is(b)
        v1.leafBean instanceof Author
        v1.constraintDescriptor != null
        v1.constraintDescriptor.annotation instanceof Max
        v1.constraintDescriptor.annotation.value() == 100l
        v2.messageTemplate == '{javax.validation.constraints.NotBlank.message}'
        v2.propertyPath.toString() == 'primaryAuthor.name'
        v2.constraintDescriptor != null
        v2.constraintDescriptor.annotation instanceof NotBlank
    }

    void "test cascade to list - no cycle"() {
        given:
        Book b = new Book(
                title: "The Stand",
                pages: 1000,
                primaryAuthor: new Author(age: 50, name: "Stephen King"),
                authors: [new Author(age: 150)]
        )
        b.authors[0].favouriteBook = b // create cycle
        def violations = validator.validate(b).sort { it.propertyPath.iterator().next().name }

        def v1 = violations.find { it.propertyPath.toString() == 'authors[0].age'}
        def v2 = violations.find { it.propertyPath.toString() == 'authors[0].name'}

        expect:
        violations.size() == 2
        v1.messageTemplate == '{javax.validation.constraints.Max.message}'
        v1.invalidValue == 150
        v1.propertyPath[0].kind == ElementKind.CONTAINER_ELEMENT
        v1.propertyPath[0].isInIterable()
        v1.propertyPath[0].index == 0
        !v1.propertyPath[1].isInIterable()
        v1.propertyPath[1].index == null
        v1.propertyPath[1].kind == ElementKind.PROPERTY
        v1.rootBean.is(b)
        v1.leafBean instanceof Author
        v1.constraintDescriptor != null
        v1.constraintDescriptor.annotation instanceof Max
        v1.constraintDescriptor.annotation.value() == 100l
        v2.messageTemplate == '{javax.validation.constraints.NotBlank.message}'
        v2.constraintDescriptor != null
        v2.constraintDescriptor.annotation instanceof NotBlank
    }

    void "test array elements"() {
        given:
        ObjectArray arrayTest = new ObjectArray(strings: [] as String[])
        def violations = validator.validate(arrayTest)

        expect:
        violations.size() == 1
        violations[0].invalidValue == []
        violations[0].messageTemplate == '{javax.validation.constraints.Size.message}'
        violations[0].constraintDescriptor != null
        violations[0].constraintDescriptor.annotation instanceof Size
        violations[0].constraintDescriptor.annotation.min() == 1
        violations[0].constraintDescriptor.annotation.max() == 2

        when:
        arrayTest = new ObjectArray(strings: ["a", "b", "c"] as String[])
        violations = validator.validate(arrayTest)

        then:
        violations.size() == 1
        violations[0].invalidValue == ["a", "b", "c"]
        violations[0].messageTemplate == '{javax.validation.constraints.Size.message}'
        violations[0].constraintDescriptor != null
        violations[0].constraintDescriptor.annotation instanceof Size
        violations[0].constraintDescriptor.annotation.min() == 1
        violations[0].constraintDescriptor.annotation.max() == 2

        when:
        arrayTest = new ObjectArray(numbers: [] as Long[])
        violations = validator.validate(arrayTest)

        then:
        violations.size() == 1
        violations[0].invalidValue == []
        violations[0].messageTemplate == '{javax.validation.constraints.Size.message}'
        violations[0].constraintDescriptor != null
        violations[0].constraintDescriptor.annotation instanceof Size
        violations[0].constraintDescriptor.annotation.min() == 1
        violations[0].constraintDescriptor.annotation.max() == 2

        when:
        arrayTest = new ObjectArray(numbers: [1L, 2L, 3L] as long[])
        violations = validator.validate(arrayTest)

        then:
        violations.size() == 1
        violations[0].invalidValue == [1L, 2L, 3L]
        violations[0].messageTemplate == '{javax.validation.constraints.Size.message}'
        violations[0].constraintDescriptor != null
        violations[0].constraintDescriptor.annotation instanceof Size
        violations[0].constraintDescriptor.annotation.min() == 1
        violations[0].constraintDescriptor.annotation.max() == 2
    }

    void "test cascade to array elements"() {
        given:
        ArrayTest arrayTest = new ArrayTest(integers: [30, 10, 60] as int[])
        def violations = validator.validate(arrayTest)

        expect:
        violations.size() == 2
        def v1 = violations.find {
            it.invalidValue == 30 &&
                    it.propertyPath.toList()[0].index == 0 &&
            it.propertyPath.toString() =='integers[0]'}
        v1.constraintDescriptor != null
        v1.constraintDescriptor.annotation instanceof  Max

        def v2 = violations.find { it.invalidValue == 60 && it.propertyPath.toList()[0].index == 2 }
        v2.constraintDescriptor != null
        v2.constraintDescriptor.annotation instanceof  Max
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
        given:
        ArrayTest arrayTest = new ArrayTest(integers: null)
        def violations = validator.validate(arrayTest)

        expect:
        violations.size() == 1
        violations.first().messageTemplate == '{javax.validation.constraints.NotNull.message}'
        violations.first().propertyPath.toString() == 'integers'
        violations.first().constraintDescriptor != null
        violations.first().constraintDescriptor.annotation instanceof  NotNull
    }

    void "test executable validator"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)
        def constraintViolations = validator.forExecutables().validateParameters(
                bookService,
                BookService.getDeclaredMethod("saveBook", String, int.class),
                ["", 50] as Object[]
        ).toList().sort({ it.propertyPath.toString() })

        expect:
        constraintViolations.size() == 2
        constraintViolations[0].invalidValue == 50
        constraintViolations[0].propertyPath.toString() == 'saveBook.pages'
        constraintViolations[0].constraintDescriptor != null
        constraintViolations[0].constraintDescriptor.annotation instanceof  Min
        constraintViolations[0].constraintDescriptor.annotation.value() == 100l
        constraintViolations[1].constraintDescriptor != null
        constraintViolations[1].constraintDescriptor.annotation instanceof  NotBlank

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

    void "test bean descriptor"() {
        given:
        BeanDescriptor beanDescriptor = validator.getConstraintsForClass(Book)

        def descriptors = beanDescriptor.getConstraintsForProperty("authors")
                .getConstraintDescriptors()

        expect:
        beanDescriptor.isBeanConstrained()
        beanDescriptor.getConstrainedProperties().size() == 4
        descriptors.size() == 1
        descriptors.first().annotation instanceof Size
        descriptors.first().annotation.min() == 1
        descriptors.first().annotation.max() == 10
    }

    void "test empty bean descriptor"() {
        given:
        BeanDescriptor beanDescriptor = validator.getConstraintsForClass(String)


        expect:
        !beanDescriptor.isBeanConstrained()
        beanDescriptor.getConstrainedProperties().size() == 0
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

    @Ignore("FIXME: https://github.com/micronaut-projects/micronaut-core/issues/4410")
    void "test element validation in String collection" () {
        when:
        ListOfStrings strings = new ListOfStrings(strings: ["", null, "a string that's too long"])
        def violations = validator.validate(strings)

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
        EnumList b = new EnumList(
                enums: [null]
        )

        def violations = validator.validate(b)

        expect:
        violations.size() == 1
        violations.first().message == "must not be null"
    }
}

@Introspected
class HiveOfBeeMap {
    @Valid
    Map<String, Bee> bees
}

@Introspected
class HiveOfBeeList {
    @Valid
    List<Bee> bees
}

// not introspected, expect validation failure
class Bee {
    @NotBlank
    String name
}

// FIXME see https://github.com/micronaut-projects/micronaut-core/issues/4410
// demonstrated by "test fail elements in String list"
// List, Set and array all work same
// 1) With Valid and constraints, validation is broken because it also applies @Size constraint to the container itself
// 2) Without @Valid only the container itself is validated for given constraints (makes sense)
@Introspected
class ListOfStrings {
    @Valid
    @Size(min=1, max=2)
    @NotNull
    List<String> strings
}

@Introspected
class ObjectArray {
    @Size(min = 1, max = 2)
    String[] strings

    @Size(min = 1, max = 2)
    Long[] numbers
}

@Introspected
class Book {
    @NotBlank
    String title

    @Min(100l)
    int pages

    @Valid
    @NotNull
    Author primaryAuthor

    @Size(min = 1, max = 10)
    @Valid
    List<Author> authors = []
}

@Introspected
class Author {
    @NotBlank
    String name
    @Max(100l)
    Integer age

    @Valid
    Book favouriteBook
}

@Introspected
@Prototype
class ArrayTest {
    @Valid
    @Max(20l)
    @NotNull
    int[] integers

    @Valid
    ArrayTest child

    @Executable
    void saveChild(@Valid ArrayTest arrayTest) {

    }

    @Executable
    ArrayTest saveIntArray(@Valid
                           @Max(20l)
                           @NotNull int[] integers) {
        new ArrayTest(integers: integers)
    }
}

@Singleton
class BookService {
    @Executable
    Book saveBook(@NotBlank String title, @Min(100l) int pages) {
        new Book(title: title, pages: pages)
    }
}

@Introspected
class EnumList {

    @Valid
    @NotNull
    List<AuthorState> enums
}

enum AuthorState {
    PUBLISHED,
    DRAFT
}
