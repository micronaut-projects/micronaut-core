package io.micronaut.security.handlers

import io.micronaut.context.annotation.Requires

import javax.inject.Singleton

@Requires(property = "spec.name", value = "RedirectRejectionHandlerSpec")
@Singleton
class CustomForbiddenRejectionUriProvider implements ForbiddenRejectionUriProvider {

    @Override
    Optional<String> getForbiddenRedirectUri() {
        Optional.of("/forbidden")
    }
}
