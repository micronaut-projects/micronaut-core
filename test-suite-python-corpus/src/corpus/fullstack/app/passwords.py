"""Password hashing.

Micronaut Security does not ship a password encoder, so we use Spring Security
Crypto's. It is a standalone artifact — no Spring context, no Spring Boot, no
auto-configuration — and it is pure Java with no JNI, which matters here: a
JNI-backed Argon2 binding would add a second blocker to a future native image,
and GraalJS is already the only one.

``DelegatingPasswordEncoder`` stores an ``{id}`` prefix with each hash and
re-encodes on a successful login when the stored hash uses an older scheme.
That reproduces ``pwdlib``'s ``verify_and_update`` behaviour in the upstream
template, so an algorithm change later is a configuration change rather than a
migration.

See PLAN.md section 7.3, and the Micronaut guide "Building a REST API —
Spring Boot vs Micronaut: Security Basic Auth", which uses the same pairing.

This lives at the top level rather than in ``app/security/`` on purpose.
Pyronaut generates a package ``__init__.py`` that eagerly imports every module
in the package, so ``app.services`` importing anything from ``app.security``
would drag in ``app.security.provider``, which imports ``app.services.users``
right back — a circular import at startup. Keeping the hasher outside the
security package makes the dependency one-directional: security depends on
services, never the reverse.
"""

from jakarta.inject import Singleton
from org.springframework.security.crypto.bcrypt import BCryptPasswordEncoder
from org.springframework.security.crypto.factory import PasswordEncoderFactories


@Singleton
class PasswordHasher:
    """Hashes and verifies user passwords."""

    def __init__(self):
        # Encodes as bcrypt; still verifies any scheme the delegating encoder
        # knows, so hashes written by an earlier configuration keep working.
        self._encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
        self._bcrypt = BCryptPasswordEncoder()

    def hash(self, raw_password: str) -> str:
        return str(self._encoder.encode(raw_password))

    def verify(self, raw_password: str, stored_hash: str) -> bool:
        if raw_password is None or stored_hash is None:
            return False
        if stored_hash.startswith("{"):
            return bool(self._encoder.matches(raw_password, stored_hash))
        # A hash written before the delegating prefix was introduced.
        return bool(self._bcrypt.matches(raw_password, stored_hash))

    def needs_rehash(self, stored_hash: str) -> bool:
        """True when the stored hash should be replaced on next successful login."""
        return not str(stored_hash).startswith("{bcrypt}")
