package io.micronaut.http.client.docs.httpclientexceptionbody

import groovy.transform.CompileStatic

@CompileStatic
class Book {
    String isbn
    String title

    Book(String isbn, String title) {
        this.isbn = isbn
        this.title = title
    }
}
