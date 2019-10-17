package io.micronaut.docs.httpclientexceptionbody;

import groovy.transform.CompileStatic;

@CompileStatic
public class Book {
    private String isbn;
    private String title;

    Book(String isbn, String title) {
        this.isbn = isbn;
        this.title = title;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
