package io.micronaut.validation.validator

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.validation.validator.resolver.CompositeTraversableResolver
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Singleton
import javax.validation.ElementKind
import javax.validation.Path
import javax.validation.Valid
import javax.validation.ValidatorFactory
import javax.validation.constraints.*
import javax.validation.metadata.BeanDescriptor

import java.util.regex.Pattern

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

    void "test annotate property with argument constraints"() {
        when:
        def b = new ValidatorSpecClasses.Book("Mary", [])
        def introspection = BeanIntrospector.SHARED.findIntrospection((Class<Object>) b.getClass()).orElse(null);
        def authorsProperty = introspection.beanProperties
                .stream().filter(p -> p.name == "authors").findFirst().get()
        def nameProperty = introspection.beanProperties
                .stream().filter(p -> p.name == "name").findFirst().get()

        then:
        authorsProperty.name == "authors"
        // is auto generated for List with generic parameter annotations
        authorsProperty.hasStereotype(Valid.class)

        nameProperty.name == "name"
        !nameProperty.hasStereotype(Valid.class)
    }

    void "test validate property argument"() {
        given:
        def listOfNames = new ValidatorSpecClasses.ListOfNames(['X', 'Ann', 'TooLongName'])
        def violations = validator.validate(listOfNames)
                .sort { it.propertyPath.toString() }

        expect:
        violations.size() == 2
        violations[0].invalidValue == 'X'
        violations[0].messageTemplate == '{javax.validation.constraints.Size.message}'
        violations[0].propertyPath.toString() == 'names[0]<E class java.lang.String>'

        violations[1].invalidValue == 'TooLongName'
        violations[1].messageTemplate == '{javax.validation.constraints.Size.message}'
        violations[1].propertyPath.toString() == 'names[2]<E class java.lang.String>'
    }

    void "test validate property argument of map"() {
        given:
        def phoneBook = new ValidatorSpecClasses.PhoneBook([
                "Andriy": 2000,
                "Bob": -10,
                "": 911
        ])
        def violations = validator.validate(phoneBook)
            .sort{it.propertyPath.toString() }

        expect:
        violations.size() == 2
        violations[0].invalidValue == -10
        violations[0].propertyPath.toString() == 'numbers[Bob]<V class java.lang.Integer>'

        violations[1].invalidValue == ""
        violations[1].propertyPath.toString() == 'numbers[]<K class java.lang.String>'
    }

    void "test validate property argument cascade"() {
        given:
        def book = new ValidatorSpecClasses.Book("LOTR", [new ValidatorSpecClasses.Author("")])
        def violations = validator.validate(book);

        expect:
        violations.size() == 1
        violations[0].invalidValue == ""
        Pattern.matches("authors\\[0]<E class .*Author>\\.name", violations[0].propertyPath.toString())
    }

    void "test validate property argument cascade with cycle"() {
        given:
        def book = new ValidatorSpecClasses.Book("LOTR", [new ValidatorSpecClasses.Author("")])
        book.authors[0].books.add(book)
        def violations = validator.validate(book)

        expect:
        violations.size() == 1
        violations[0].invalidValue == ""
        Pattern.matches("authors\\[0]<E class .*Author>\\.name", violations[0].propertyPath.toString())
    }

    void "test validate property argument cascade of null container"() {
        given:
        def book = new ValidatorSpecClasses.Book("LOTR")
        def violations = validator.validate(book)

        expect:
        violations.size() == 0
    }

    void "test validate property argument cascade - nested"() {
        given:
        Set books = [
                new ValidatorSpecClasses.Book("Alice in wonderland", []),
                new ValidatorSpecClasses.Book("LOTR", [
                        new ValidatorSpecClasses.Author("Bob"),
                        new ValidatorSpecClasses.Author("")
                ]),
                new ValidatorSpecClasses.Book("?")
        ]
        def library = new ValidatorSpecClasses.Library(books)
        def violations = validator.validate(library)
                .sort{it.propertyPath.toString()}

        expect:
        violations.size() == 2
        violations[0].invalidValue == ""
        Pattern.matches("books\\[]<E .*Book>.authors\\[1]<E .*Author>.name",
                violations[0].propertyPath.toString())

        violations[1].invalidValue == "?"
        Pattern.matches("books\\[]<E .*Book>.name", violations[1].propertyPath.toString())
    }

    @Ignore("The validateProperty method does not support cascading")
    void "test validate bean property cascade"() {
        given:
        Book b = new Book(primaryAuthor: new Author(name: "", age: 200));
        def violations = validator.validateProperty(b, "primaryAuthor").sort{it.propertyPath.toString();}

        expect:
        violations.size() == 2
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

    @Ignore("List cascade not supported on groovy")
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

    void "test array size"() {
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

    @Ignore("Array cascade not supported")
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

    @Ignore("Array cascade not supported")
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

    @Ignore("Array cascade not supported")
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

    void "test executable validator - cascade"() {
        when:
        def bookService = applicationContext.getBean(ValidatorSpecClasses.BookService)
        def book = new ValidatorSpecClasses.Book("X", [
                new ValidatorSpecClasses.Author("")
        ])
        def violations = validator.forExecutables().validateParameters(
                bookService,
                ValidatorSpecClasses.BookService.getDeclaredMethod("saveBook", ValidatorSpecClasses.Book),
                [book] as Object[]
        ).sort{it.propertyPath.toString()}

        then:
        violations.size() == 2
        violations[0].invalidValue == ""
        violations[0].propertyPath.toString() ==
                'saveBook.book.authors[0]<E class io.micronaut.validation.validator.ValidatorSpecClasses$Author>.name'
        violations[0].constraintDescriptor != null
        violations[0].constraintDescriptor.annotation instanceof NotBlank

        violations[1].invalidValue == "X"
        violations[1].propertyPath.toString() == 'saveBook.book.name'
        violations[1].constraintDescriptor != null
        violations[1].constraintDescriptor.annotation instanceof Size
    }

    @Ignore("Array cascade not supported")
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

    void "test validate property argument cascade - to non-introspected - inside map"(){
        when:
        def notIntrospected = new ValidatorSpecClasses.Person("")
        def apartmentBuilding = new ValidatorSpecClasses.ApartmentBuilding([1: notIntrospected])
        def violations = validator.validate(apartmentBuilding)

        then:
        violations.size() == 1
        !violations[0].constraintDescriptor
        violations[0].message == "Cannot validate io.micronaut.validation.validator.ValidatorSpecClasses\$Person. No bean introspection present. " +
                "Please add @Introspected to the class and ensure Micronaut annotation processing is enabled"
        // violations[0].propertyPath.toString() ==
        //        'apartmentLivers[1]<V class io.micronaut.validation.validator.ValidatorSpecClasses.Person>'

        when:
        apartmentBuilding = new ValidatorSpecClasses.ApartmentBuilding([2: null])
        violations = validator.validate(apartmentBuilding)

        then:
        violations.size() == 1
        !violations[0].constraintDescriptor
        violations[0].message == "Cannot validate io.micronaut.validation.validator.ValidatorSpecClasses\$Person. No bean introspection present. " +
                "Please add @Introspected to the class and ensure Micronaut annotation processing is enabled"
    }

    @Ignore("List cascade not supported in Groovy")
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

    @Ignore("Map cascade not supported in Groovy")
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

    @Ignore("List cascade not supported in Groovy https://github.com/micronaut-projects/micronaut-core/issues/4410")
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

    void "test validate property argument cascade - enum"() {
        given:
        def inventory = new ValidatorSpecClasses.BooksInventory([
                ValidatorSpecClasses.BookCondition.USED,
                null,
                ValidatorSpecClasses.BookCondition.NEW
        ])
        def violations = validator.validate(inventory)

        expect:
        violations.size() == 1
        violations[0].message == "must not be null"
    }

    @Ignore("List cascade not supported in Groovy")
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

    void "test kotlin validation"() {
//        when:
//        var b = new BookKotlin(["X", "Me", "TooLongName"])
//        var violations = validator.validate(b)
//
//        then:
//        violations.size() == 2
    }

    void "test validate method argument generic annotations"() {
        when:
        var b = applicationContext.getBean(ValidatorSpecClasses.Bank)
        var violations = validator.forExecutables().validateParameters(
                b,
                ValidatorSpecClasses.Bank.getDeclaredMethod("deposit", List<Integer>),
                [[0, 100, 500, -1]] as Object[]
        )
        violations = violations.sort({it -> it.getPropertyPath().toString() })

        then:
        violations.size() == 2
        Pattern.matches("deposit.banknotes\\[0]<E [^>]*Integer>", violations[0].getPropertyPath().toString())
        Pattern.matches("deposit.banknotes\\[3]<E [^>]*Integer>", violations[1].getPropertyPath().toString())
        violations[0].getPropertyPath().size() == 3

        when:
        def path0 = violations[0].getPropertyPath().iterator()
        def node0 = path0.next()
        def node1 = path0.next()
        def node2 = path0.next()

        then:
        node0 instanceof Path.MethodNode
        node0.getName() == "deposit"

        node1 instanceof Path.ParameterNode
        node1.getName() == "banknotes"

        node2 instanceof Path.ContainerElementNode
        node2.getName() == "E class java.lang.Integer"
    }

    void "test validate method argument generic annotations cascade"() {
        when:
        var b = applicationContext.getBean(ValidatorSpecClasses.Bank)
        var methDecl = ValidatorSpecClasses.Bank.getDeclaredMethod(
                "createAccount",
                ValidatorSpecClasses.Client,
                Map<Integer, ValidatorSpecClasses.Client>
        )
        // client, clientsWithAccess
        var params = [
                new ValidatorSpecClasses.Client(""),
                [
                        "child": new ValidatorSpecClasses.Client("Ann"),
                        "spouse": new ValidatorSpecClasses.Client("X"),
                        "": new ValidatorSpecClasses.Client("Jack")
                ]
        ]
        var violations = validator.forExecutables().validateParameters(b, methDecl, params as Object[])
        violations = violations.sort({it -> it.getPropertyPath().toString() })

        then:
        violations.size() == 3
        violations[0].getPropertyPath().toString() == "createAccount.client.name"
        Pattern.matches("createAccount.clientsWithAccess\\[]<K .*String>",
                violations[1].getPropertyPath().toString())
        Pattern.matches("createAccount.clientsWithAccess\\[spouse]<V [^>]*Client>.name",
                violations[2].getPropertyPath().toString())

        when:
        var path0 = violations[0].getPropertyPath().iterator()
        var path1 = violations[1].getPropertyPath().iterator()
        var path2 = violations[2].getPropertyPath().iterator()

        then:
        violations[0].getPropertyPath().size() == 3
        violations[1].getPropertyPath().size() == 3
        violations[2].getPropertyPath().size() == 4

        path0.next() instanceof Path.MethodNode
        path0.next() instanceof Path.ParameterNode
        path0.next() instanceof Path.PropertyNode

        path1.next() instanceof Path.MethodNode
        path1.next() instanceof Path.ParameterNode
        path1.next() instanceof Path.ContainerElementNode

        path2.next() instanceof Path.MethodNode
        path2.next() instanceof Path.ParameterNode
        path2.next() instanceof Path.ContainerElementNode
        path2.next() instanceof Path.PropertyNode

    }

    void "test validate annotations null"() {
        when:
        var book = new ValidatorSpecClasses.Book("Alice In Wonderland", [null])
        var violations = validator.validate(book)

        then:
        violations.size() == 0

        when:
        var b = applicationContext.getBean(ValidatorSpecClasses.Bank)
        var methDecl = ValidatorSpecClasses.Bank.getDeclaredMethod(
                "createAccount",
                ValidatorSpecClasses.Client,
                Map<Integer, ValidatorSpecClasses.Client>
        )
        // client, clientsWithAccess
        var params = [null, ["child": null]]
        var methodViolations = validator.forExecutables().validateParameters(b, methDecl, params as Object[])

        then:
        methodViolations.size() == 0
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
class ListOfNames {
    @NotNull
    List<@Size(min=3, max=8) String> names;
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
    List<@Valid Author> authors = []
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
