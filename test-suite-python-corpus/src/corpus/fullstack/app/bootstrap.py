"""Startup bootstrap.

Creates the first superuser if it does not already exist. This replaces the
upstream template's ``initial_data.py`` script and the ``prestart.sh`` hook that
runs it: there is no separate step to remember, because it is part of starting
the application.

Flyway has already run by this point — Micronaut orders the datasource
migration ahead of ``StartupEvent``.
"""

import logging

from jakarta.inject import Singleton
from micronaut.context.event import ApplicationEventListener, StartupEvent

from .config import AppConfig
from .dto import UserCreate
from .services.users import UserService

logger = logging.getLogger(__name__)


@Singleton
class FirstSuperuserBootstrap(ApplicationEventListener[StartupEvent]):
    def __init__(self, users: UserService, config: AppConfig):
        self.users = users
        self.config = config

    def onApplicationEvent(self, event: StartupEvent) -> None:
        email = self.config.first_superuser
        if self.users.by_email(email) is not None:
            return
        self.users.create(
            UserCreate(
                email=email,
                password=self.config.first_superuser_password,
                fullName="Administrator",
                isActive=True,
                isSuperuser=True,
            )
        )
        logger.info("Created first superuser %s", email)
