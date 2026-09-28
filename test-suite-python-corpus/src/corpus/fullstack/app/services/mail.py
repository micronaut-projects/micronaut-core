"""Transactional email.

The email bodies are **React components**, rendered server-side on GraalJS by
Micronaut Views React — the same engine and the same bundle that renders the
browser pages. Nothing is pre-compiled to HTML at build time and no Node
process runs in production.

This is the counterpart to the upstream template's ``packages/react-email``
workspace, which compiles React Email components to Jinja templates that
``app/utils.py`` then renders.
"""

from io.micronaut.email import BodyType, Email
from io.micronaut.email.template import TemplateBody
from io.micronaut.views import ModelAndView
from jakarta.inject import Singleton
from micronaut.email import EmailSender

from ..config import AppConfig

# View names resolve to exports of views/ssr-components.mjs; see frontend/server.jsx.
TEST_EMAIL = "TestEmail"
NEW_ACCOUNT_EMAIL = "NewAccountEmail"
RESET_PASSWORD_EMAIL = "ResetPasswordEmail"


@Singleton
class MailService:
    def __init__(self, sender: EmailSender, config: AppConfig):
        self.sender = sender
        self.config = config

    def _send(self, to: str, subject: str, view: str, props: dict) -> None:
        props = {"projectName": self.config.name, **props}
        self.sender.send(
            Email.builder()
            .to(to)
            .subject(subject)
            .body(TemplateBody(BodyType.HTML, ModelAndView(view, props)))
        )

    def send_test_email(self, to: str) -> None:
        self._send(
            to,
            f"{self.config.name} - Test email",
            TEST_EMAIL,
            {"email": to},
        )

    def send_new_account_email(self, to: str, username: str, password: str) -> None:
        self._send(
            to,
            f"{self.config.name} - New account for user {username}",
            NEW_ACCOUNT_EMAIL,
            {
                "username": username,
                "password": password,
                "link": self.config.frontend_host,
            },
        )

    def send_reset_password_email(self, to: str, email: str, token: str) -> None:
        self._send(
            to,
            f"{self.config.name} - Password recovery for user {email}",
            RESET_PASSWORD_EMAIL,
            {
                "username": email,
                "email": to,
                "link": f"{self.config.frontend_host}/reset-password?token={token}",
                "validHours": self.config.email_reset_token_expire_hours,
            },
        )

    def render_reset_password_html(self, to: str, email: str, token: str) -> str:
        """Used by the superuser-only endpoint that returns the email HTML."""
        raise NotImplementedError(
            "Wire to ViewsRenderer once the email render path is verified; see PLAN.md 11.6"
        )
