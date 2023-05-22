package io.micronaut.inject.validation;

import jakarta.annotation.Nullable;
import javax.persistence.Entity;
import jakarta.validation.constraints.NotBlank;

@Entity
public class Account1 {

    private Long id;

    @Nullable
    @NotBlank
    private String username;

    @Nullable
    @NotBlank
    private String password;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
