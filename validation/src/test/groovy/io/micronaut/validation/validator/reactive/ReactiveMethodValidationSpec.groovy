package io.micronaut.validation.validator.reactive

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import io.micronaut.core.async.annotation.SingleResult
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
        Mono.from(bookService.rxReturnInvalid(Mono.just(new Book(title: "It")))).block()

        then:
        ConstraintViolationException e = thrown()
        e.message == 'title: must not be blank'
        e.getConstraintViolations().first().propertyPath.toString() == 'title'
    }

    void "test reactive validation with invalid argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        Mono.from(bookService.rxValid(Mono.just(new Book(title: "")))).block()

        then:
        ConstraintViolationException e = thrown()
        e.message == 'rxValid.title: must not be blank'
        e.getConstraintViolations().first().propertyPath.toString() == 'rxValid.title'
    }

    void "test reactive validation with invalid simple argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        Mono.from(bookService.rxSimple(Mono.just(""))).block()

        then:
        ConstraintViolationException e = thrown()
        e.message == 'rxSimple.title: must not be blank'
        e.getConstraintViolations().first().propertyPath.toString() == 'rxSimple.title'
    }

    void "test future validation with invalid simple argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        bookService.futureSimple(CompletableFuture.completedFuture("")).get()

        then:
        ExecutionException e = thrown()

        e.cause.message == 'futureSimple.title: must not be blank'
        e.cause.getConstraintViolations().first().propertyPath.toString() == 'futureSimple.title'
    }

    void "test future validation with invalid argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        bookService.futureValid(CompletableFuture.completedFuture(new Book(title: ""))).get()

        then:
        ExecutionException e = thrown()

        e.cause.message == 'futureValid.title: must not be blank'
        e.cause.getConstraintViolations().first().propertyPath.toString() == 'futureValid.title'
    }

    void "test reactive validation with valid argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        Book book = Mono.from(bookService.rxValid(Mono.just(new Book(title: "It")))).block()

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
    @SingleResult
    Publisher<Book> rxSimple(@NotBlank Publisher<String> title) {
        return Flux.from(title).map({ String t -> new Book(title: t)})
    }

    @Executable
    @Valid
    @SingleResult
    Publisher<Book> rxValid(@Valid Publisher<Book> book) {
        return book
    }

    @Executable
    @Valid
    @SingleResult
    Publisher<Book> rxReturnInvalid(@Valid Publisher<Book> book) {
        return Flux.from(book).map({ b -> b.title =''; return b})
    }


}

@Introspected
class Book {
    @NotBlank
    String title
}
