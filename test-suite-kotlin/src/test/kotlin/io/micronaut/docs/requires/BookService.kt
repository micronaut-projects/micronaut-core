package io.micronaut.docs.requires

interface BookService {
    fun findBook(title: String): Book?
}
