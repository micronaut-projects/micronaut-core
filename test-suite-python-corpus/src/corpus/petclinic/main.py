"""Logging configuration for Pyronaut PetClinic sample.
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
            "stream": "ext://sys.stdout"
        }
    },
    "root": {
        "level": "INFO",
        "handlers": ["console"]
    },
    "loggers": {
        "io.micronaut.web.router": {
            # change to TRACE to view HTTP routes
            "level": "INFO",
            "handlers": ["console", "file"]
        }
    }
}

dictConfig(LOGGING)

