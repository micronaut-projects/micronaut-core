package io.micronaut.json

import groovy.transform.EqualsAndHashCode
import io.micronaut.core.type.Argument;
import spock.lang.Specification

import java.nio.charset.StandardCharsets;

class JsonMapperSpec extends Specification {

    void "test JsonMapper default methods"() {
        given:
        JsonMapper jsonMapper = JsonMapper.createDefault()
        Book b = new Book(title: "Getting Things done")
        String expected = '{"title":"Getting Things done"}'

        when:
        String result = jsonMapper.writeValueAsString(b)

        then:
        expected == result

        when:
        result = jsonMapper.writeValueAsString(Argument.of(Book), b)

        then:
        expected == result

        when:
        result = jsonMapper.writeValueAsString(Argument.of(Book), b, StandardCharsets.UTF_8)

        then:
        expected == result

        when:
        Book bookRead = jsonMapper.readValue(expected, Argument.of(Book))

        then:
        bookRead == b

        when:
        bookRead = jsonMapper.readValue(expected, Book)

        then:
        bookRead == b

        when:
        bookRead = jsonMapper.readValue(expected.bytes, Book)

        then:
        bookRead == b
    }

    @EqualsAndHashCode
    static class Book {
        String title
    }
}
