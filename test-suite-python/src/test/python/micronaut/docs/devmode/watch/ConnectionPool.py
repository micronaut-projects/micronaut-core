from typing import Optional


class ConnectionPool:
    """A stand-in for a connection pool such as Hikari's: its URL is fixed when it is created, its credentials can change."""

    def __init__(self, url: Optional[str], username: Optional[str], password: Optional[str]):
        self.url = url
        self.username = username
        self.password = password
        self.evictions = 0

    def set_credentials(self, username: Optional[str], password: Optional[str]) -> None:
        self.username = username
        self.password = password

    def soft_evict_connections(self) -> None:
        """Closes the idle connections and the busy ones once they are returned, so that new ones use the new credentials."""
        self.evictions += 1
