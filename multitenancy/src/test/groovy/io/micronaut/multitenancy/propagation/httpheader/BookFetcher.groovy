package io.micronaut.multitenancy.propagation.httpheader

import io.reactivex.Flowable

interface BookFetcher {
    Flowable<Book> findAll()
}