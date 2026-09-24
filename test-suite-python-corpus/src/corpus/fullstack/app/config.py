"""Application configuration.

``@ConfigurationProperties`` binds the whole ``app`` prefix to one class, the
way ``pydantic-settings`` binds a ``BaseSettings`` model — but the binding and
the ``jakarta.validation`` constraints are generated at build time, and the
constraints are checked when the application starts rather than when a request
first touches them.

Values come from ``config/application.toml``, then ``application-<env>.toml``,
then environment variables (``APP_SECRET_KEY`` sets ``app.secret-key``), and in
the dev and test environments from a ``.env`` file — see ``dotenv.py``.
"""

from typing import Annotated

from jakarta.annotation import PostConstruct
from jakarta.validation.constraints import Min, NotBlank
from micronaut.context.annotation import ConfigurationProperties
from micronaut.context.env import Environment


@ConfigurationProperties("app")
class AppConfig:
    """Settings for the application itself, mirroring the upstream `Settings`."""

    name: Annotated[str, NotBlank] = "Pyronaut Full Stack Project"
    secret_key: Annotated[str, NotBlank] = "changethis"
    first_superuser: Annotated[str, NotBlank] = "admin@example.com"
    first_superuser_password: Annotated[str, NotBlank] = "changethis"
    frontend_host: str = "http://localhost:8080"
    emails_from_email: str = "info@example.com"
    emails_from_name: str = "Pyronaut Full Stack Project"
    email_reset_token_expire_hours: Annotated[int, Min(1)] = 48

    # SMTP. Read by mail_session.ConfiguredSessionProvider rather than by
    # Micronaut Email's own `javamail.properties` binding — see that module for
    # why. Defaults point at a local Mailpit.
    smtp_host: str = "localhost"
    smtp_port: Annotated[int, Min(1)] = 1025
    smtp_user: str = ""
    smtp_tls: bool = False


DEFAULT_SECRETS = ("secret_key", "first_superuser_password")


@ConfigurationProperties("app")
class AppConfigGuard:
    """Refuses to start with placeholder secrets outside development.

    The upstream template does the same check in a pydantic ``model_validator``.
    Keeping it is worth the few lines: a template's defaults get deployed.
    """

    def __init__(self, config: AppConfig, environment: Environment):
        self.config = config
        self.environment = environment

    @PostConstruct
    def check(self) -> None:
        development = any(
            name in ("dev", "test") for name in self.environment.getActiveNames()
        )
        offenders = [
            name for name in DEFAULT_SECRETS if getattr(self.config, name) == "changethis"
        ]
        if not offenders:
            return
        message = (
            "The following settings still have their placeholder value "
            f"'changethis': {', '.join('app.' + n.replace('_', '-') for n in offenders)}. "
            "Set them before running outside development."
        )
        if development:
            import logging

            logging.getLogger(__name__).warning(message)
        else:
            raise ValueError(message)
