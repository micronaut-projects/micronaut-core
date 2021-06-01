package io.micronaut.validation.validator.reactive

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.reactivex.Single
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.validation.ConstraintViolationException
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException

class ReactiveMethodValidationSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void "test reactive return type validation"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        bookService.rxReturnInvalid(Single.just(new Book(title: "It"))).blockingGet()

        then:
        def e = thrown(ConstraintViolationException)
        e.message == 'title: must not be blank'
        e.getConstraintViolations().first().propertyPath.toString() == 'title'
    }

    void "test reactive validation with invalid argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        bookService.rxValid(Single.just(new Book(title: ""))).blockingGet()

        then:
        def e = thrown(ConstraintViolationException)
        e.message == 'rxValid.title: must not be blank'
        e.getConstraintViolations().first().propertyPath.toString() == 'rxValid.title'
    }

    void "test reactive validation with invalid simple argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        bookService.rxSimple(Single.just("")).blockingGet()

        then:
        def e = thrown(ConstraintViolationException)
        e.message == 'rxSimple.title: must not be blank'
        e.getConstraintViolations().first().propertyPath.toString() == 'rxSimple.title'
    }

    void "test future validation with invalid simple argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        bookService.futureSimple(CompletableFuture.completedFuture("")).get()

        then:
        def e = thrown(ExecutionException)

        e.cause.message == 'futureSimple.title: must not be blank'
        e.cause.getConstraintViolations().first().propertyPath.toString() == 'futureSimple.title'
    }

    void "test future validation with invalid argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        bookService.futureValid(CompletableFuture.completedFuture(new Book(title: ""))).get()

        then:
        def e = thrown(ExecutionException)

        e.cause.message == 'futureValid.title: must not be blank'
        e.cause.getConstraintViolations().first().propertyPath.toString() == 'futureValid.title'
    }

    void "test reactive validation with valid argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        def book = bookService.rxValid(Single.just(new Book(title: "It"))).blockingGet()

        then:
        book.title == 'It'

    }
}

@Singleton
class BookService {
    @Executable
    @Valid
    CompletionStage<Book> futureSimple(@NotBlank CompletionStage<String> title) {
        return title.thenApply({ String t -> new Book(title: t)})
    }

    @Executable
    @Valid
    CompletableFuture<Book> futureValid(@Valid CompletableFuture<Book> book) {
        return book
    }

    @Executable
    @Valid
    Single<Book> rxSimple(@NotBlank Single<String> title) {
        return title.map({ String t -> new Book(title: t)})
    }

    @Executable
    @Valid
    Single<Book> rxValid(@Valid Single<Book> book) {
        return book
    }

    @Executable
    @Valid
    Single<Book> rxReturnInvalid(@Valid Single<Book> book) {
        return book.map({ b -> b.title =''; return b})
    }


}

@Introspected
class Book {
    @NotBlank
    String title
}
