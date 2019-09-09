package io.micronaut.docs.aop.retry

class Book {
    private String title

    Book(String title) {
        this.title = title
    }

    String getTitle() {
        return title
    }
}
