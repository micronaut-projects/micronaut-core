"""Server-rendered browser routes.

Each route returns the model for one screen, and Micronaut Views React renders
the `App` component on GraalJS with that model as its props. The same component
tree then hydrates in the browser from `/static/client.js`, so the first paint
comes from the server and the page stays interactive afterwards.

Every screen also works without a server-supplied model: `frontend/src/App.jsx`
falls back to fetching from the API when `initial` is absent. That is what keeps
the hydration real rather than decorative.

The handlers are named `*_page` so their operation ids cannot collide with the
API operations of the same name — `/login` the page and `/api/v1/login` the
endpoint both exist. A collision is not an error; Micronaut OpenAPI quietly
appends a number, and the generated client grows a `signup1` whose digit
depends on declaration order.
"""

from typing import Annotated

from java.util import UUID
from micronaut.http.annotation import Controller, Get, QueryValue
from micronaut.security.annotation import Secured
from micronaut.security.authentication import Authentication
from micronaut.security.rules import SecurityRule
from micronaut.views import View

from ..mappers import item_public, user_public
from ..paging import page_request
from ..services.items import ItemService
from ..services.users import UserService

APP_VIEW = "App"


def _model(page: str, **data) -> dict:
    """The shape frontend/src/App.jsx dispatches on."""
    return {"page": page, "data": data}


@Controller
class ViewController:
    def __init__(self, users: UserService, items: ItemService):
        self.users = users
        self.items = items

    def _current(self, authentication: Authentication):
        return self.users.by_id(UUID.fromString(str(authentication.getName())))

    # -- anonymous screens --------------------------------------------------
    @Get("/login")
    @View(APP_VIEW)
    @Secured(SecurityRule.IS_ANONYMOUS)
    def login_page(self, error: Annotated[bool, QueryValue(defaultValue="false")] = False) -> dict:
        """The sign-in screen."""
        return _model("login", error=error)

    @Get("/signup")
    @View(APP_VIEW)
    @Secured(SecurityRule.IS_ANONYMOUS)
    def signup_page(self) -> dict:
        """The registration screen."""
        return _model("signup")

    @Get("/recover-password")
    @View(APP_VIEW)
    @Secured(SecurityRule.IS_ANONYMOUS)
    def recover_password_page(self) -> dict:
        """The "email me a reset link" screen."""
        return _model("recoverPassword")

    @Get("/reset-password")
    @View(APP_VIEW)
    @Secured(SecurityRule.IS_ANONYMOUS)
    def reset_password_page(self, token: Annotated[str, QueryValue(defaultValue="")] = "") -> dict:
        """The "set a new password" screen, reached from the recovery email."""
        return _model("resetPassword", token=token)

    # -- authenticated screens ----------------------------------------------
    @Get("/")
    @View(APP_VIEW)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    def dashboard_page(self, authentication: Authentication) -> dict:
        """The dashboard."""
        return _model("dashboard", user=user_public(self._current(authentication)))

    @Get("/items")
    @View(APP_VIEW)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    def items_page(self, authentication: Authentication) -> dict:
        """The item list, server-rendered with its first page already filled in."""
        user = self._current(authentication)
        result = self.items.list_for(user, page_request())
        return _model(
            "items",
            user=user_public(user),
            items=[item_public(item) for item in result.getContent()],
            count=result.getTotalSize(),
        )

    @Get("/settings")
    @View(APP_VIEW)
    @Secured(SecurityRule.IS_AUTHENTICATED)
    def settings_page(self, authentication: Authentication) -> dict:
        """The account settings screen."""
        return _model("settings", user=user_public(self._current(authentication)))

    @Get("/admin")
    @View(APP_VIEW)
    @Secured(["ROLE_SUPERUSER"])
    def admin_page(self, authentication: Authentication) -> dict:
        """The user administration screen. Superuser only."""
        result = self.users.page(page_request())
        return _model(
            "admin",
            user=user_public(self._current(authentication)),
            users=[user_public(user) for user in result.getContent()],
            count=result.getTotalSize(),
        )
