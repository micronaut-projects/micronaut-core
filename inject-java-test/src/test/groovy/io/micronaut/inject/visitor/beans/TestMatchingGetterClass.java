package io.micronaut.inject.visitor.beans;

public abstract class TestMatchingGetterClass {

    private Boolean isDeleted;
    private String getName;
    private String author;

    public Boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public String getName() {
        return getName;
    }

    public void setGetName(String getName) {
        this.getName = getName;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
}
