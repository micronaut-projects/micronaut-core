package io.micronaut.validation.validator.reactive

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.validation.validator.Validator
import io.reactivex.Single
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Singleton
import javax.validation.ConstraintViolationException
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.regex.Pattern

class ReactiveMethodValidationSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    void "test reactive return type validation"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        bookService.rxReturnInvalid(Single.just(new Book("It"))).blockingGet()

        then:
        def e = thrown(ConstraintViolationException)
        e.message == 'title: must not be blank'
        e.getConstraintViolations().first().propertyPath.toString() == 'title'
    }

    void "test reactive validation with invalid simple argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        var validator = applicationContext.getBean(Validator)
        var violations = validator.forExecutables().validateParameters(
                bookService,
                BookService.class.getDeclaredMethod("rxSimple", Single<String>),
                [Single.just("")] as Object[]
        )

        bookService.rxSimple(Single.just("")).blockingGet()

        then:
        def e = thrown(ConstraintViolationException)
        Pattern.matches('rxSimple.title\\[]<T [^>]*String>: must not be blank', e.message)
        def path = e.getConstraintViolations().first().propertyPath.iterator()
        path.next().getName() == 'rxSimple'
        path.next().getName() == 'title'
        path.next().isInIterable()
    }

    void "test reactive validation with valid argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        def book = bookService.rxValid(Single.just(new Book("It"))).blockingGet()

        then:
        book.title == 'It'

    }

    void "test reactive validation with invalid argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        bookService.rxValid(Single.just(new Book(""))).blockingGet()

        then:
        def e = thrown(ConstraintViolationException)
        Pattern.matches('rxValid.book\\[]<T .*Book>.title: must not be blank', e.message)
        e.getConstraintViolations().first().propertyPath.toString().startsWith('rxValid.book')
    }

    void "test future validation with invalid simple argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        bookService.futureSimple(CompletableFuture.completedFuture("")).get()

        then:
        def e = thrown(ExecutionException)

        Pattern.matches('futureSimple.title\\[]<T .*String>: must not be blank', e.cause.message)
        e.cause.getConstraintViolations().first().propertyPath.toString().startsWith('futureSimple.title')
    }

    void "test future validation with invalid argument"() {
        given:
        BookService bookService = applicationContext.getBean(BookService)

        when:
        bookService.futureValid(CompletableFuture.completedFuture(new Book(""))).get()

        then:
        def e = thrown(ExecutionException)

        Pattern.matches('futureValid.book\\[]<T .*Book>.title: must not be blank', e.cause.message);
        e.cause.getConstraintViolations().first().propertyPath.toString().startsWith('futureValid.book')
    }
}
