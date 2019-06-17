package io.micronaut.validation.validator

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.Introspected
import io.micronaut.validation.validator.resolver.CompositeTraversableResolver
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.inject.Singleton
import javax.validation.ConstraintViolation
import javax.validation.ElementKind
import javax.validation.Valid
import javax.validation.ValidatorFactory
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size
import javax.validation.metadata.BeanDescriptor
import javax.validation.metadata.ConstraintDescriptor

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
        violations[1].invalidValue == 50
        violations[1].propertyPath.iterator().next().name == 'pages'
        violations[1].rootBean == b
        violations[1].rootBeanClass == Book
        violations[1].messageTemplate == '{javax.validation.constraints.Min.message}'
        violations[2].invalidValue == null
        violations[2].propertyPath.iterator().next().name == 'primaryAuthor'
        violations[3].invalidValue == ''
        violations[3].propertyPath.iterator().next().name == 'title'

    }


    void "test validate bean property"() {
        given:
        Book b = new Book(title: "", pages: 50)
        def violations = validator.validateProperty(b, "title").sort { it.propertyPath.iterator().next().name }

        expect:
        violations.size() == 1
        violations[0].invalidValue == ''
        violations[0].propertyPath.iterator().next().name == 'title'

    }

    void "test validate value"() {
        given:
        def violations = validator.validateValue(Book, "title", "").sort { it.propertyPath.iterator().next().name }

        expect:
        violations.size() == 1
        violations[0].invalidValue == ''
        violations[0].propertyPath.iterator().next().name == 'title'
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
        v2.messageTemplate == '{javax.validation.constraints.NotBlank.message}'
        v2.propertyPath.toString() == 'primaryAuthor.name'
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
        v2.messageTemplate == '{javax.validation.constraints.NotBlank.message}'
        v2.propertyPath.toString() == 'primaryAuthor.name'
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
        v2.messageTemplate == '{javax.validation.constraints.NotBlank.message}'
    }


    void "test cascade to array elements"() {
        given:
        ArrayTest arrayTest = new ArrayTest(integers: [30, 10, 60] as int[])
        def violations = validator.validate(arrayTest)

        expect:
        violations.size() == 2
        violations.find {
            it.invalidValue == 30 &&
                    it.propertyPath.toList()[0].index == 0 &&
            it.propertyPath.toString() =='integers[0]'}
        violations.find { it.invalidValue == 60 && it.propertyPath.toList()[0].index == 2 }
    }

    void "test cascade to array elements - nested"() {
        when:
        ArrayTest arrayTest = new ArrayTest(integers: [10, 15] as int[], child: new ArrayTest(integers: [10, 60] as int[]))
        def violations = validator.validate(arrayTest).toList()

        then:
        violations.size() == 1
        violations[0].propertyPath.toString() == 'child.integers[1]'


        when:
        arrayTest = new ArrayTest(
                integers: [10, 15] as int[],
                child: new ArrayTest(integers: [10, 15] as int[], child: new ArrayTest(integers: [10, 60] as int[])))
        violations = validator.validate(arrayTest).toList()

        then:
        violations.size() == 1
        violations[0].propertyPath.toString() == 'child.child.integers[1]'
    }


    void "test cascade to array elements null"() {
        given:
        ArrayTest arrayTest = new ArrayTest(integers: null)
        def violations = validator.validate(arrayTest)

        expect:
        violations.size() == 1
        violations.first().messageTemplate == '{javax.validation.constraints.NotNull.message}'
        violations.first().propertyPath.toString() == 'integers'
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

    }

    void "test executable validator - cascade to array"() {
        when:
        BookService bookService = applicationContext.getBean(BookService)
        def constraintViolations = validator.forExecutables().validateParameters(
                bookService,
                BookService.getDeclaredMethod("saveIntArray", int[].class),
                [[30, 10, 60] as int[]] as Object[]
        ).toList().sort({ it.propertyPath.toString() })


        then:
        constraintViolations.size() == 2
        constraintViolations[0].propertyPath.toString() == 'saveIntArray.integers[0]'

        when:
        ArrayTest arrayTest = applicationContext.createBean(ArrayTest)
        arrayTest.integers = [30,10,60] as int[]
        def violations = validator.forExecutables().validateParameters(
                new ArrayTest(),
                ArrayTest.getDeclaredMethod("saveChild", ArrayTest.class),
                [arrayTest] as Object[]
        ).toList().sort({ it -> it.propertyPath.toString() })

        then:
        violations.size() == 2
        violations[0].propertyPath.toString() == 'saveChild.arrayTest.integers[0]'

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
}

@Singleton
class BookService {
    @Executable
    Book saveBook(@NotBlank String title, @Min(100l) int pages) {
        new Book(title: title, pages: pages)
    }

    @Executable
    ArrayTest saveIntArray(@Valid
                   @Max(20l)
                   @NotNull int[] integers) {
        new ArrayTest(integers: integers)
    }
}