package io.micronaut.docs.replaces

import io.micronaut.docs.requires.Book

interface BookService {
    Book findBook(String title)
}