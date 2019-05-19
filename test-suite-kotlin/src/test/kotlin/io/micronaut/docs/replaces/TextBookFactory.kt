package io.micronaut.docs.replaces

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces

import javax.inject.Singleton

// tag::class[]
@Factory
class TextBookFactory {

    @Singleton
    @Replaces(value = TextBook::class, factory = BookFactory::class)
    internal fun textBook(): TextBook {
        return TextBook("Learning 305")
    }
}
// end::class[]
