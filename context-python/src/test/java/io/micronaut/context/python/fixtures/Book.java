package io.micronaut.context.python.fixtures;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

@Introspected
public class Book {
    private String title;
    private int pages;
    private List<String> authors;

    public Book() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    public List<String> getAuthors() {
        return authors;
    }

    public void setAuthors(List<String> authors) {
        this.authors = authors;
    }
}
