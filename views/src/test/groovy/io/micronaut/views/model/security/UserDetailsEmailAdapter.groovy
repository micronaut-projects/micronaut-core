package io.micronaut.views.model.security

import io.micronaut.security.authentication.Authentication

class UserDetailsEmailAdapter implements Authentication {

    UserDetailsEmail userDetailsEmail

    UserDetailsEmailAdapter(UserDetailsEmail userDetailsEmail) {
        this.userDetailsEmail = userDetailsEmail
    }

    @Override
    Map<String, Object> getAttributes() {
        return [email: userDetailsEmail.email, roles: userDetailsEmail.roles]
    }

    @Override
    String getName() {
        return userDetailsEmail.username
    }
}
