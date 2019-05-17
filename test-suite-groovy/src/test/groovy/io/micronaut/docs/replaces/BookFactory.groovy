package io.micronaut.docs.replaces

import io.micronaut.context.annotation.Factory
import io.micronaut.docs.requires.Book

import javax.inject.Singleton

// tag::class[]
@Factory
class BookFactory {

    @Singleton
    Book novel() {
        new Book('A Great Novel')
    }

    @Singleton
    TextBook textBook() {
        new TextBook('Learning 101')
    }
}
// end::class[]