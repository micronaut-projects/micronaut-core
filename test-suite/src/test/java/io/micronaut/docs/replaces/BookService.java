package io.micronaut.docs.replaces;

import io.micronaut.docs.requires.Book;

public interface BookService {
    Book findBook(String title);
}
