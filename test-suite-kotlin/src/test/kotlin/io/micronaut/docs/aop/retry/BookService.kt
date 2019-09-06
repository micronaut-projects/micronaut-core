package io.micronaut.docs.aop.retry

import io.micronaut.retry.annotation.CircuitBreaker
import io.micronaut.retry.annotation.Retryable
import io.reactivex.Flowable

import java.util.Collections

open class BookService {

    // tag::simple[]
    @Retryable
    open fun listBooks(): List<Book> {
        // ...
        // end::simple[]
        return listOf(Book("The Stand"))
    }

    // tag::circuit[]
    @CircuitBreaker(reset = "30s")
    open fun findBooks(): List<Book> {
        // ...
        // end::circuit[]
        return listOf(Book("The Stand"))
    }

    // tag::attempts[]
    @Retryable(attempts = "5", delay = "2s")
    open fun findBook(title: String): Book {
        // ...
        // end::attempts[]
        return Book(title)
    }

    // tag::config[]
    @Retryable(attempts = "\${book.retry.attempts:3}", delay = "\${book.retry.delay:1s}")
    open fun getBook(title: String): Book {
        // ...
        // end::config[]
        return Book(title)
    }

    // tag::reactive[]
    @Retryable
    open fun streamBooks(): Flowable<Book> {
        // ...
        // end::reactive[]
        return Flowable.just(
                Book("The Stand")
        )
    }
}
