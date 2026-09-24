"""User management.

Docstrings on these methods become the operation descriptions in the OpenAPI
document that ``pyronaut process`` generates, which in turn become the doc
comments on the generated TypeScript client. They are the API reference, so
write them for the person calling the endpoint.
"""

from typing import Annotated

from jakarta.validation import Valid
from java.util import UUID
from micronaut.http import HttpResponse, HttpStatus
from micronaut.http.annotation import Body, Controller, Delete, Get, Patch, Post, QueryValue
from micronaut.security.annotation import Secured
from micronaut.security.authentication import Authentication
from micronaut.security.rules import SecurityRule

from ..dto import (
    ApiError,
    Message,
    UpdatePassword,
    UserCreate,
    UserPublic,
    UserRegister,
    UserUpdate,
    UserUpdateMe,
    UsersPublic,
)
from ..mappers import user_public, users_public
from ..paging import DEFAULT_PAGE_SIZE, page_request
from ..security.provider import ROLE_SUPERUSER
from ..services.mail import MailService
from ..services.users import EmailAlreadyUsed, UserService


def _email_conflict() -> HttpResponse:
    """409 for a duplicate email, in the one API error shape.

    ``EmailAlreadyUsed`` is a Python exception, and Micronaut's
    ``ExceptionHandler<T extends Throwable, R>`` bound only accepts Java
    throwables — so this is caught in the controller rather than handled by a
    bean like the validation failures in ``errors.py``.
    """
    return HttpResponse.status(HttpStatus.CONFLICT).body(
        ApiError(
            message="A user with this email already exists",
            errors={"email": "Already registered"},
        )
    )


@Controller("/api/v1/users")
@Secured(SecurityRule.IS_AUTHENTICATED)
class UserController:
    def __init__(self, users: UserService, mail: MailService):
        self.users = users
        self.mail = mail

    def _current(self, authentication: Authentication):
        """Resolve the authenticated principal to a user row.

        The principal name is the user's id, set by the authentication
        provider, so this is a primary-key lookup rather than a search.
        """
        return self.users.by_id(UUID.fromString(str(authentication.getName())))

    # -- collection ------------------------------------------------------
    @Get
    @Secured([ROLE_SUPERUSER])
    def list_users(
        self,
        # Annotation values must be compile-time constants: the processor reads
        # them from source and cannot evaluate an expression, so a computed
        # default silently drops out and the parameter is published as required.
        page: Annotated[int, QueryValue(defaultValue="0")] = 0,
        size: Annotated[int, QueryValue(defaultValue="100")] = DEFAULT_PAGE_SIZE,
    ) -> UsersPublic:
        """List all users. Superuser only.

        Paginated with `page` and `size`. The total count is returned alongside
        the page so a client can render pagination controls without a second
        request.
        """
        result = self.users.page(page_request(page, size))
        return users_public(result.getContent(), result.getTotalSize())

    @Post
    @Secured([ROLE_SUPERUSER])
    def create_user(self, body: Annotated[UserCreate, Body, Valid]) -> HttpResponse:
        """Create a user. Superuser only.

        If email delivery is configured the new user is sent their credentials.
        """
        try:
            user = self.users.create(body)
        except EmailAlreadyUsed:
            return _email_conflict()
        self.mail.send_new_account_email(user.email, user.email, body.password)
        return HttpResponse.status(HttpStatus.CREATED).body(user_public(user))

    # -- the authenticated user -------------------------------------------
    @Get("/me")
    def read_me(self, authentication: Authentication) -> UserPublic:
        """Return the currently authenticated user."""
        return user_public(self._current(authentication))

    @Patch("/me")
    def update_me(
        self, authentication: Authentication, body: Annotated[UserUpdateMe, Body, Valid]
    ) -> UserPublic:
        """Update the authenticated user's own name or email."""
        return user_public(self.users.update_me(self._current(authentication), body))

    @Patch("/me/password")
    def update_my_password(
        self, authentication: Authentication, body: Annotated[UpdatePassword, Body, Valid]
    ) -> HttpResponse:
        """Change the authenticated user's password.

        Requires the current password. Returns 400 if it does not match, or if
        the new password is the same as the current one.
        """
        user = self._current(authentication)
        if not self.users.verify_password(user, body.currentPassword):
            return HttpResponse.badRequest(Message(message="Incorrect password"))
        if body.currentPassword == body.newPassword:
            return HttpResponse.badRequest(
                Message(message="The new password must differ from the current one")
            )
        self.users.set_password(user, body.newPassword)
        return HttpResponse.ok(Message(message="Password updated successfully"))

    @Delete("/me")
    def delete_me(self, authentication: Authentication) -> HttpResponse:
        """Delete the authenticated user's own account.

        A superuser may not delete themselves; doing so could leave the system
        with no administrator.
        """
        user = self._current(authentication)
        if user.isSuperuser:
            return HttpResponse.badRequest(
                Message(message="Superusers are not allowed to delete themselves")
            )
        self.users.delete(user)
        return HttpResponse.ok(Message(message="User deleted successfully"))

    # -- registration ------------------------------------------------------
    @Post("/signup")
    @Secured(SecurityRule.IS_ANONYMOUS)
    def signup(self, body: Annotated[UserRegister, Body, Valid]) -> HttpResponse:
        """Register a new account.

        Open registration: the created user is always active and never a
        superuser, whatever the request asks for.
        """
        try:
            user = self.users.register(body)
        except EmailAlreadyUsed:
            return _email_conflict()
        return HttpResponse.status(HttpStatus.CREATED).body(user_public(user))

    # -- by id --------------------------------------------------------------
    @Get("/{userId}")
    def read_user(self, userId: UUID, authentication: Authentication) -> HttpResponse:
        """Fetch a user by id.

        A user may always read their own record; reading anyone else's requires
        superuser.
        """
        current = self._current(authentication)
        if str(current.id) != str(userId) and not current.isSuperuser:
            return HttpResponse.status(HttpStatus.FORBIDDEN).body(
                Message(message="The user doesn't have enough privileges")
            )
        user = self.users.by_id(userId)
        return HttpResponse.notFound() if user is None else HttpResponse.ok(user_public(user))

    @Patch("/{userId}")
    @Secured([ROLE_SUPERUSER])
    def update_user(
        self, userId: UUID, body: Annotated[UserUpdate, Body, Valid]
    ) -> HttpResponse:
        """Update any user. Superuser only."""
        user = self.users.by_id(userId)
        if user is None:
            return HttpResponse.notFound()
        try:
            return HttpResponse.ok(user_public(self.users.update(user, body)))
        except EmailAlreadyUsed:
            return _email_conflict()

    @Delete("/{userId}")
    @Secured([ROLE_SUPERUSER])
    def delete_user(self, userId: UUID, authentication: Authentication) -> HttpResponse:
        """Delete any user, and their items. Superuser only.

        A superuser may not delete their own account through this endpoint
        either.
        """
        user = self.users.by_id(userId)
        if user is None:
            return HttpResponse.notFound()
        if str(user.id) == str(self._current(authentication).id):
            return HttpResponse.badRequest(
                Message(message="Superusers are not allowed to delete themselves")
            )
        self.users.delete(user)
        return HttpResponse.ok(Message(message="User deleted successfully"))
