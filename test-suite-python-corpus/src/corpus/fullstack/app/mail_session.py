"""The Jakarta Mail session, built in Python.

Micronaut Email's JavaMail integration normally builds its session from a
`javamail.properties` map bound out of configuration. That binding is avoided
here, because resolving those properties while Micronaut Test Resources is
active sends the property resolver into an infinite recursion: Test Resources
is consulted for the unknown key, resolving its own required properties
re-enters the same resolver, and the stack overflows. It only bites beans built
lazily during a request, which is exactly when the mail beans are built.

Implementing `SessionProvider` directly sidesteps the whole binding. The values
come from `AppConfig`, which is bound once at startup and so never touches the
lazy resolver — and it is another demonstration that a Micronaut Java interface
can be implemented from Python.

`@Secondary` on Micronaut's own `DefaultSessionProvider` means this bean wins
without needing `@Replaces`.
"""

from jakarta.inject import Singleton
from jakarta.mail import Session
from java.util import Properties
from micronaut.email.javamail.sender import SessionProvider

from .config import AppConfig


@Singleton
class ConfiguredSessionProvider(SessionProvider):
    """Builds an SMTP session from `app.smtp-*` configuration."""

    def __init__(self, config: AppConfig):
        self.config = config

    def session(self) -> Session:
        properties = Properties()
        properties.put("mail.smtp.host", self.config.smtp_host)
        properties.put("mail.smtp.port", str(self.config.smtp_port))
        properties.put("mail.smtp.auth", "true" if self.config.smtp_user else "false")
        properties.put("mail.smtp.starttls.enable", "true" if self.config.smtp_tls else "false")
        # No Authenticator: Mailpit accepts unauthenticated mail, and a real
        # relay should be configured through a SessionProvider of its own.
        return Session.getInstance(properties, None)
