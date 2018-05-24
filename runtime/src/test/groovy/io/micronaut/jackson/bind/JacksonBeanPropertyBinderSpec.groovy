/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jackson.bind

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class JacksonBeanPropertyBinderSpec extends Specification {

    @Unroll
    void "test bind map properties to object"() {
        given:
        JacksonBeanPropertyBinder binder = ApplicationContext.run().getBean(JacksonBeanPropertyBinder)
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
