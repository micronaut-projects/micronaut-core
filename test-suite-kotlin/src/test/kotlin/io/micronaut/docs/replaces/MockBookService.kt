package io.micronaut.docs.replaces

import io.micronaut.context.annotation.Replaces
import io.micronaut.docs.requires.Book

import javax.inject.Singleton
import java.util.LinkedHashMap

// tag::class[]
@Replaces(JdbcBookService::class) // <1>
@Singleton
class MockBookService : BookService {

    var bookMap: Map<String, Book> = LinkedHashMap()

    override fun findBook(title: String): Book? {
        return bookMap[title]
    }
}
// tag::class[]
