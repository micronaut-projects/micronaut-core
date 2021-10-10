package io.micronaut.docs.basics;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Book {
    private String title;

    @JsonCreator
    public Book(@JsonProperty("title") String title) {
        this.title = title;
    }

    Book() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
