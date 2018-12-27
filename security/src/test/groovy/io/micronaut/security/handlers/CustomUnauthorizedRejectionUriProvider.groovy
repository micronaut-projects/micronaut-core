package io.micronaut.security.handlers

import io.micronaut.context.annotation.Requires

import javax.inject.Singleton

@Requires(property = "spec.name", value = "RedirectRejectionHandlerSpec")
@Singleton
class CustomUnauthorizedRejectionUriProvider implements UnauthorizedRejectionUriProvider {

    @Override
    Optional<String> getUnauthorizedRedirectUri() {
        Optional.of("/login")
    }
}
