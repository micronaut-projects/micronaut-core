package io.micronaut.inject.blockingutils

import io.micronaut.context.annotation.Executable

interface AuthenticationProvider {
    @Executable
    boolean authenticate(String username, String password)
}
