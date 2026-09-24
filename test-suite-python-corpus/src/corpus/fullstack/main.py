"""Logging configuration.

Pyronaut routes Python's ``logging`` module onto Logback, so the standard
``dictConfig`` shape configures the whole application — Micronaut's own loggers
included.
"""

from logback.config import dictConfig

LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {
        "standard": {
            "format": "%(asctime)s [%(levelname)s] %(name)s: %(message)s"
        }
    },
    "handlers": {
        "console": {
            "class": "logging.StreamHandler",
            "level": "INFO",
            "formatter": "standard",
            "stream": "ext://sys.stdout",
        }
    },
    "root": {"level": "INFO", "handlers": ["console"]},
    "loggers": {
        # Set to TRACE to print the resolved HTTP routes at startup.
        "io.micronaut.web.router": {"level": "INFO", "handlers": ["console"]},
    },
}

dictConfig(LOGGING)
