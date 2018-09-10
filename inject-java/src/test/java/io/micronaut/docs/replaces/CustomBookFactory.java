package io.micronaut.docs.replaces;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.docs.requires.Book;

import javax.inject.Singleton;


// tag::class[]
@Factory
@Replaces(factory = BookFactory.class)
public class CustomBookFactory {

    @Singleton
    Book otherNovel() {
        return new Book("An OK Novel");
    }
}
// end::class[]
