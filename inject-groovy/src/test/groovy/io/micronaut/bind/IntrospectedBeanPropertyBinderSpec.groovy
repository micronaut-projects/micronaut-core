package io.micronaut.bind

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.bind.IntrospectedBeanPropertyBinder
import io.micronaut.core.type.Argument
import spock.lang.Specification
import spock.lang.Unroll

class IntrospectedBeanPropertyBinderSpec extends Specification {

    @Unroll
    void "test bind map properties to object"() {
        given:
        IntrospectedBeanPropertyBinder binder = new IntrospectedBeanPropertyBinder(null)
        def result = binder.bind(type, map)

        expect:
        result == expected

        where:
        type   | map                                                                                            | expected
        Author | ['name': 'Stephen King', 'publisher.name': 'Blah']                                             | new Author(name: "Stephen King", publisher: new Publisher(name: "Blah"))
        Book   | ['authors[0].name': 'Stephen King', 'authors[0].publisher.name': 'Blah']                       | new Book(authors: [new Author(name: "Stephen King", publisher: new Publisher(name: 'Blah'))])
        Book   | ['authorsByInitials[SK].name': 'Stephen King', 'authorsByInitials[SK].publisher.name': 'Blah'] | new Book(authorsByInitials: [SK: new Author(name: "Stephen King", publisher: new Publisher(name: 'Blah'))])
        Book   | ['title': 'The Stand', url: 'http://foo.com']                                                  | new Book(title: "The Stand", url: new URL("http://foo.com"))
        Book   | ['authors[0].name': 'Stephen King']                                                            | new Book(authors: [new Author(name: "Stephen King")])
        Book   | ['authors[0].name': 'Stephen King', 'authors[0].age': 60]                                      | new Book(authors: [new Author(name: "Stephen King", age: 60)])
        Book   | ['authors[0].name': 'Stephen King', 'authors[0].age': 60,
                  'authors[1].name': 'JRR Tolkien', 'authors[1].age': 110]                                      | new Book(authors: [new Author(name: "Stephen King", age: 60), new Author(name: "JRR Tolkien", age: 110)])
        Book   | ['authorsByInitials[SK].name' : 'Stephen King', 'authorsByInitials[SK].age': 60,
                  'authorsByInitials[JRR].name': 'JRR Tolkien', 'authorsByInitials[JRR].age': 110]              | new Book(authorsByInitials: [SK: new Author(name: "Stephen King", age: 60), JRR: new Author(name: "JRR Tolkien", age: 110)])

    }

    void "test constructor arguments"() {
        given:
        IntrospectedBeanPropertyBinder binder = new IntrospectedBeanPropertyBinder(null)
        Map<String, Object> map = [
                'publishers[1].name': 'Pub 1',
                'publishers[0].name': 'Pub 0',
                'strings[2]': 'String 2', 
                'strings[1]': 'String 1', 
                'integers[0]': '0',
                'publisherMap[pub1].name': 'Pub Map 1',
                'publisherMap[pub2].name': 'Pub Map 2',
                'integer': '2',
                'author.name': 'John',
                'author.age': '53',
                'author.publisher.name': 'Author Publisher',
                'books[0].authors[0].name': 'John',
                'books[0].authors[0].age': '53',
                'books[0].authors[0].publisher.name': 'John Publisher',
                'books[0].authors[1].name': 'Sally',
                'books[0].authors[1].age': '45',
                'books[0].authors[1].publisher.name': 'Sally Publisher',
                'books[0].title': 'Book 0 Title',
                'books[0].url': 'https://micronaut.io',
                'books[1].authors[0].name': 'Susan',
                'books[1].authors[0].age': '19',
                'books[1].authors[0].publisher.name': 'Susan Publisher',
                'books[1].authorsByInitials[sk].name': 'Stephen King'
        ]
        
        when:
        ConstructorArgs args = binder.bind(ConstructorArgs, map)
        
        then:
        args.publishers[1].name == 'Pub 1'
        args.publishers[0].name == 'Pub 0'
        args.strings[2] == 'String 2'
        args.strings[1] == 'String 1'
        args.strings[0] == null
        args.publisherMap.pub1.name == 'Pub Map 1'
        args.publisherMap.pub2.name == 'Pub Map 2'
        args.integer == 2
        args.author.name == 'John'
        args.author.age == 53
        args.author.publisher.name == 'Author Publisher'
        args.books[0].authors[0].name == 'John'
        args.books[0].authors[0].age == 53
        args.books[0].authors[0].publisher.name == 'John Publisher'
        args.books[0].authors[1].name == 'Sally'
        args.books[0].authors[1].age == 45
        args.books[0].authors[1].publisher.name == 'Sally Publisher'
        args.books[0].title == 'Book 0 Title'
        args.books[0].url == new URL('https://micronaut.io')
        args.books[1].authors[0].name == 'Susan'
        args.books[1].authors[0].age == 19
        args.books[1].authors[0].publisher.name == 'Susan Publisher'
        args.books[1].authorsByInitials.sk.name == 'Stephen King'
    }

    void "test constructor arguments with nested map"() {
        given:
        IntrospectedBeanPropertyBinder binder = new IntrospectedBeanPropertyBinder(null)
        Map<String, Object> map = [
                publishers: [
                        [name: 'Pub 0'],
                        [name: 'Pub 1'],
                ],
                strings: ['String 0', 'String 1'],
                integers: [0],
                publisherMap: [pub1: [name: 'Pub Map 1'], pub2: [name: 'Pub Map 2']],
                integer: '2',
                author: [name: 'John', age: '53', publisher: [name: 'Author Publisher']],
                books: [
                        [
                                title: 'Book 0 Title',
                                url: 'https://micronaut.io',
                                authors: [
                                        [name: 'John', age: '53', publisher: [name: 'John Publisher']],
                                        [name: 'Sally', age: '45', publisher: [name: 'Sally Publisher']]
                                ]
                        ],
                        [
                                authors: [
                                        [name: 'Susan', age: '19', publisher: [name: 'Susan Publisher']]
                                ],
                                authorsByInitials: [sk: [name: 'Stephen King']]
                        ]
                ]
        ]

        when:
        ConstructorArgs args = binder.bind(ConstructorArgs, map)

        then:
        args.publishers[1].name == 'Pub 1'
        args.publishers[0].name == 'Pub 0'
        args.strings[1] == 'String 1'
        args.strings[0] == 'String 0'
        args.publisherMap.pub1.name == 'Pub Map 1'
        args.publisherMap.pub2.name == 'Pub Map 2'
        args.integer == 2
        args.author.name == 'John'
        args.author.age == 53
        args.author.publisher.name == 'Author Publisher'
        args.books[0].authors[0].name == 'John'
        args.books[0].authors[0].age == 53
        args.books[0].authors[0].publisher.name == 'John Publisher'
        args.books[0].authors[1].name == 'Sally'
        args.books[0].authors[1].age == 45
        args.books[0].authors[1].publisher.name == 'Sally Publisher'
        args.books[0].title == 'Book 0 Title'
        args.books[0].url == new URL('https://micronaut.io')
        args.books[1].authors[0].name == 'Susan'
        args.books[1].authors[0].age == 19
        args.books[1].authors[0].publisher.name == 'Susan Publisher'
        args.books[1].authorsByInitials.sk.name == 'Stephen King'
    }

    @EqualsAndHashCode
    @ToString
    @Introspected
    static class Book {
        String title
        URL url

        List<Author> authors = []
        Map<String, Author> authorsByInitials = [:]
    }

    @EqualsAndHashCode
    @ToString
    @Introspected
    static class Author {
        String name
        Integer age

        Publisher publisher
    }

    @EqualsAndHashCode
    @ToString
    @Introspected
    static class Publisher {
        String name
    }

    @Introspected
    static class ConstructorArgs {

        final List<Publisher> publishers
        final Set<String> strings
        final Map<String, Publisher> publisherMap
        final int integer
        final Author author
        final Book[] books

        ConstructorArgs(List<Publisher> publishers,
                        Set<String> strings,
                        Author author,
                        Map<String, Publisher> publisherMap,
                        Integer integer,
                        Book[] books) {
            this.books = books
            this.author = author
            this.integer = integer
            this.publisherMap = publisherMap
            this.strings = strings
            this.publishers = publishers
        }
    }
}
