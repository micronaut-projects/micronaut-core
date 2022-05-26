package io.micronaut.inject.visitor.beans;

import io.micronaut.core.annotation.AccessorsStyle;

@AccessorsStyle(readPrefixes = "read", writePrefixes = "with")
public abstract class TestCustomGetterClass {

    private String name;
    private String author;

    public String readName() {
        return name;
    }

    public void withName(String name) {
        this.name = name;
    }

    public String readAuthor() {
        return author;
    }

    public void withAuthor(String author) {
        this.author = author;
    }
}
