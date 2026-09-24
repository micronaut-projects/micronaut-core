"""Test-support endpoints, available only in the `dev` and `test` environments.

`@Requires(env=...)` is evaluated when the bean is loaded, so in production this
controller does not exist at all — it is not a route that checks a flag, it is a
route that was never registered. The upstream template achieves the same thing
by conditionally including a router.

It exists so that end-to-end tests can create a user directly instead of going
through signup and email verification.
"""

from typing import Annotated

from jakarta.validation import Valid
from micronaut.context.annotation import Requires
from micronaut.http import HttpResponse, HttpStatus
from micronaut.http.annotation import Body, Controller, Post
from micronaut.security.annotation import Secured
from micronaut.security.rules import SecurityRule

from ..dto import UserCreate
from ..mappers import user_public
from ..services.users import EmailAlreadyUsed, UserService


@Controller("/api/v1/private")
@Requires(env=["dev", "test"])
@Secured(SecurityRule.IS_ANONYMOUS)
class PrivateController:
    def __init__(self, users: UserService):
        self.users = users

    @Post("/users")
    def create_user_directly(self, body: Annotated[UserCreate, Body, Valid]) -> HttpResponse:
        """Create a user without authentication. Never available in production.

        Named distinctly from `create_user` on the users controller: operation
        ids must be unique across the whole API or the generated client ends up
        with a `createUser1`, whose number depends on declaration order.
        """
        try:
            user = self.users.create(body)
        except EmailAlreadyUsed:
            return HttpResponse.status(HttpStatus.CONFLICT).body(
                user_public(self.users.by_email(body.email))
            )
        return HttpResponse.status(HttpStatus.CREATED).body(user_public(user))
