package io.micronaut.docs.ioc.validation.iterableGenericParameters;

import java.util.*;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.validation.ConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
public class BookInfoSpec {

    @Inject BookInfoService bookInfoService;

    // tag::validate-iterables[]

    @Test
    void testAuthorNamesAreValidated() {
        final List<String> authors = Arrays.asList("Me", "");

        final ConstraintViolationException exception =
                assertThrows(ConstraintViolationException.class, () ->
                        bookInfoService.setBookAuthors("My Book", authors)
                );

        assertEquals("setBookAuthors.authors[1]<E String>: must not be blank",
                exception.getMessage()); // <1>
    }

    @Test
    void testSectionsAreValidated() {
        final Map<String, Integer> sectionStartPages = new HashMap<>();
        sectionStartPages.put("", 1);

        final ConstraintViolationException exception =
                assertThrows(ConstraintViolationException.class, () ->
                        bookInfoService.setBookSectionPages("My Book", sectionStartPages)
                );

        assertEquals("setBookSectionPages.sectionStartPages[]<K String>: must not be blank",
                exception.getMessage()); // <2>
    }

    // end::validate-iterables[]
}
