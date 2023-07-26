package io.micronaut.inject.visitor.beans.builder;

import java.util.Objects;

public class Book {

    private final String title;
    private final String author;

    private Book(String title, String author) {
        this.title = title;
        this.author = author;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Book book = (Book) o;

        if (!Objects.equals(title, book.title)) return false;
        return Objects.equals(author, book.author);
    }

    @Override
    public int hashCode() {
        int result = title != null ? title.hashCode() : 0;
        result = 31 * result + (author != null ? author.hashCode() : 0);
        return result;
    }

    public static Builder builder() {
        return new Builder();
    }

    static class Builder {
        private String title;
        private String author;

        Builder title(String title) {
            this.title = title;
            return this;
        }

        Builder author(String author) {
            this.author = author;
            return this;
        }

        Book build() {
            return new Book(title, author);
        }

    }
}
