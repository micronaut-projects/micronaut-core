package io.micronaut.docs.basics

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

class Book {
    private String title

    @JsonCreator
    Book(@JsonProperty("title") String title) {
        this.title = title
    }

    Book() {
    }

    String getTitle() {
        return title
    }

    void setTitle(String title) {
        this.title = title
    }
}
