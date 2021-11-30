package io.micronaut.ast.groovy.visitor

import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

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
