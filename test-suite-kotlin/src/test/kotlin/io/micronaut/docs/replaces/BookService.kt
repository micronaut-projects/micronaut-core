package io.micronaut.docs.replaces

import io.micronaut.docs.requires.Book

interface BookService {
    fun findBook(title: String): Book?
}
