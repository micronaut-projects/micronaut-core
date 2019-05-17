package io.micronaut.docs.replaces

import io.micronaut.context.annotation.Replaces
import io.micronaut.docs.requires.Book

import javax.inject.Singleton

// tag::class[]
@Replaces(JdbcBookService.class) // <1>
@Singleton
class MockBookService implements BookService {

    Map<String, Book> bookMap = [:]

    @Override
    Book findBook(String title) {
        bookMap.get(title)
    }
}
// tag::class[]
