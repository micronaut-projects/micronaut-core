package io.micronaut.docs.aop.retry;

import io.micronaut.retry.annotation.CircuitBreaker;
import io.micronaut.retry.annotation.Retryable;
import io.reactivex.Flowable;

import java.util.Collections;
import java.util.List;

public class BookService {

    // tag::simple[]
    @Retryable
    public List<Book> listBooks() {
        // ...
    // end::simple[]
        return Collections.singletonList(
                new Book("The Stand")
        );
    }

    // tag::circuit[]
    @CircuitBreaker(reset = "30s")
    public List<Book> findBooks() {
        // ...
    // end::circuit[]
        return Collections.singletonList(
                new Book("The Stand")
        );
    }

    // tag::attempts[]
    @Retryable( attempts = "5",
                delay = "2s" )
    public Book findBook(String title) {
        // ...
    // end::attempts[]
        return new Book(title);
    }


    // tag::config[]
    @Retryable( attempts = "${book.retry.attempts:3}",
                delay = "${book.retry.delay:1s}" )
    public Book getBook(String title) {
        // ...
    // end::config[]
        return new Book(title);
    }

    // tag::reactive[]
    @Retryable
    public Flowable<Book> streamBooks() {
        // ...
    // end::reactive[]
        return Flowable.just(
                new Book("The Stand")
        );
    }
}
