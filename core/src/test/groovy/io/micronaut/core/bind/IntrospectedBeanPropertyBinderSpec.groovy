package io.micronaut.core.bind

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.core.annotation.Introspected
import spock.lang.Specification
import spock.lang.Unroll

class IntrospectedBeanPropertyBinderSpec extends Specification {

    @Unroll
    void "test bind map properties to object"() {
        given:
        IntrospectedBeanPropertyBinder binder = new IntrospectedBeanPropertyBinder()
        def result = binder.bind(type.newInstance(), map)

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

    @EqualsAndHashCode
    @ToString
    static class Book {
        String title
        URL url

        List<Author> authors = []
        Map<String, Author> authorsByInitials = [:]
    }

    @EqualsAndHashCode
    @ToString
    static class Author {
        String name
        Integer age

        Publisher publisher
    }

    @EqualsAndHashCode
    @ToString
    static class Publisher {
        String name
    }
}
