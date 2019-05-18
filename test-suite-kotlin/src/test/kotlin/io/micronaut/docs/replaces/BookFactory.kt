package io.micronaut.docs.replaces

import io.micronaut.context.annotation.Factory
import io.micronaut.docs.requires.Book

import javax.inject.Singleton

// tag::class[]
@Factory
class BookFactory {

    @Singleton
    internal fun novel(): Book {
        return Book("A Great Novel")
    }

    @Singleton
    internal fun textBook(): TextBook {
        return TextBook("Learning 101")
    }
}
// end::class[]