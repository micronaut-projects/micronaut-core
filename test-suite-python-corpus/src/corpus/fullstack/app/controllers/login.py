"""Login-adjacent endpoints: token check and password recovery.

Login and logout themselves are not here. Micronaut Security provides them at
`/api/v1/login` and `/api/v1/logout`, configured in `config/application.toml`;
the authentication itself lives in `app/security/provider.py`.

That is the one deliberate departure from the upstream template, which exposes
`POST /login/access-token` taking an OAuth2 `x-www-form-urlencoded` form. That
form encoding is a FastAPI-ism inherited from its Swagger UI integration, and
the TypeScript client is generated from OpenAPI either way, so nothing needs it.
"""

from typing import Annotated

from jakarta.validation import Valid
from micronaut.http import HttpResponse
from micronaut.http.annotation import Body, Controller, Get, PathVariable, Post
from micronaut.security.annotation import Secured
from micronaut.security.authentication import Authentication
from micronaut.security.rules import SecurityRule

from ..dto import Message, NewPassword, UserPublic
from ..mappers import user_public
from ..security.tokens import PasswordResetTokens
from ..services.mail import MailService
from ..services.users import UserService


@Controller("/api/v1")
class LoginController:
    def __init__(
        self,
        users: UserService,
        mail: MailService,
        tokens: PasswordResetTokens,
    ):
        self.users = users
        self.mail = mail
        self.tokens = tokens

    @Get("/login/test-token")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    def test_token(self, authentication: Authentication) -> UserPublic:
        """Verify an access token and return the user it belongs to."""
        from java.util import UUID

        return user_public(self.users.by_id(UUID.fromString(str(authentication.getName()))))

    @Post("/password-recovery/{email}")
    @Secured(SecurityRule.IS_ANONYMOUS)
    def recover_password(self, email: str) -> Message:
        """Send a password recovery link.

        The response is identical whether or not the address is registered, so
        this endpoint cannot be used to discover which emails have accounts.
        """
        user = self.users.by_email(email)
        if user is not None:
            token = self.tokens.issue(user.email)
            self.mail.send_reset_password_email(user.email, user.email, token)
        return Message(message="If that email is registered, we sent a password recovery link")

    @Post("/reset-password")
    @Secured(SecurityRule.IS_ANONYMOUS)
    def reset_password(self, body: Annotated[NewPassword, Body, Valid]) -> HttpResponse:
        """Set a new password using a recovery token.

        An expired token, a token issued for another purpose, and a token for a
        user that no longer exists all return the same error, so the response
        reveals nothing about which case applied.
        """
        email = self.tokens.verify(body.token)
        if email is None:
            return HttpResponse.badRequest(Message(message="Invalid token"))
        user = self.users.by_email(email)
        if user is None or not user.isActive:
            return HttpResponse.badRequest(Message(message="Invalid token"))
        self.users.set_password(user, body.newPassword)
        return HttpResponse.ok(Message(message="Password updated successfully"))
