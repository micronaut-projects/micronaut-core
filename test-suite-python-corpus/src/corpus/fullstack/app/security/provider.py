"""Authentication provider.

A Python class implementing a Java interface: ``HttpRequestAuthenticationProvider``
is a Micronaut Security interface, subclassed here directly. This is the
pattern used by Micronaut Security's own Python test suite
(``micronaut-security/test-suite-python``), so it is a supported shape rather
than an experiment.

It replaces ``crud.authenticate`` plus ``deps.get_current_user`` in the
upstream template: Micronaut runs the provider, issues the JWT and validates it
on subsequent requests, and ``@Secured`` decides access per route.
"""

from jakarta.inject import Singleton
from micronaut.http import HttpRequest
from micronaut.security.authentication import (
    AuthenticationFailureReason,
    AuthenticationRequest,
    AuthenticationResponse,
)
from micronaut.security.authentication.provider import HttpRequestAuthenticationProvider

from ..services.users import UserService

ROLE_USER = "ROLE_USER"
ROLE_SUPERUSER = "ROLE_SUPERUSER"


@Singleton
class EmailPasswordAuthenticationProvider(HttpRequestAuthenticationProvider):
    """Authenticates an email address and password against the users table."""

    def __init__(self, users: UserService):
        self.users = users

    def authenticate(
        self, requestContext: HttpRequest, authRequest: AuthenticationRequest
    ) -> AuthenticationResponse:
        email = str(authRequest.getIdentity())
        password = str(authRequest.getSecret())

        user = self.users.authenticate(email, password)
        if user is None:
            # Same failure for an unknown email and a wrong password, so the
            # endpoint cannot be used to enumerate accounts.
            return AuthenticationResponse.failure(
                AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH
            )
        if not user.isActive:
            return AuthenticationResponse.failure(
                AuthenticationFailureReason.USER_DISABLED
            )

        roles = [ROLE_USER]
        if user.isSuperuser:
            roles.append(ROLE_SUPERUSER)

        return AuthenticationResponse.success(
            str(user.id),
            roles,
            {"email": user.email, "fullName": user.fullName},
        )
