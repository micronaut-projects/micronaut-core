package io.micronaut.validation;

import javax.validation.constraints.Email;

public class Pojo {

    @Email(message = "Email should be valid")
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
