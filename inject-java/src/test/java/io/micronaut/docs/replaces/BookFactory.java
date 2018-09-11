package io.micronaut.docs.replaces;

import io.micronaut.context.annotation.Factory;
import io.micronaut.docs.requires.Book;

import javax.inject.Singleton;

// tag::class[]
@Factory
public class BookFactory {

    @Singleton
    Book novel() {
        return new Book("A Great Novel");
    }

    @Singleton
    TextBook textBook() {
        return new TextBook("Learning 101");
    }
}
// end::class[]