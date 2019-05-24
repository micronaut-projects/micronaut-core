package io.micronaut.docs.replaces;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.docs.requires.Book;

import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;

// tag::class[]
@Replaces(JdbcBookService.class) // <1>
@Singleton
public class MockBookService implements BookService {

    Map<String, Book> bookMap = new LinkedHashMap<>();

    @Override
    public Book findBook(String title) {
        return bookMap.get(title);
    }
}
// tag::class[]
