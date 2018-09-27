package io.micronaut.function.client.aws;

public class IsbnValidationRequest {
    private String isbn;

    public IsbnValidationRequest() {

    }
    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }
}
