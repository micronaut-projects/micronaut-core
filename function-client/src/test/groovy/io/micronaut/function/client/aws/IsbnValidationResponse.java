package io.micronaut.function.client.aws;

public class IsbnValidationResponse {
    private String isbn;
    private boolean valid;

    public IsbnValidationResponse() {

    }
    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }
}
