package io.micronaut.ast.groovy.visitor

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

class SuperClass {

    @NotBlank
    @NotNull
    private String username

    String getUsername() {
        return username
    }

    void setUsername(String username) {
        this.username = username
    }
}
