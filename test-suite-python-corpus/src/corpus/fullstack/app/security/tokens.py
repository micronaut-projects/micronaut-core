"""Password-reset tokens.

A reset token is deliberately *not* an access token. Access tokens are JWTs
issued and validated by Micronaut Security (see `config/application.toml`);
these are short-lived, single-purpose, opaque strings signed with the same
application secret. Keeping them separate means a reset link can never be
replayed as a session, and a stolen session can never be replayed as a reset.

The implementation is plain Python: `hmac`, `hashlib` and `base64` from the
standard library, running on GraalPy. No Java interop, nothing to configure —
a reminder that ordinary Python still works here when it is the simplest thing.
"""

import base64
import hashlib
import hmac
import json
import time

from jakarta.inject import Singleton

from ..config import AppConfig

PURPOSE = "password-reset"


def _b64encode(raw: bytes) -> str:
    """URL-safe base64 without padding, so the token is safe in a query string."""
    return base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")


def _b64decode(text: str) -> bytes:
    padding = "=" * (-len(text) % 4)
    return base64.urlsafe_b64decode(text + padding)


@Singleton
class PasswordResetTokens:
    """Issues and verifies password-reset tokens."""

    def __init__(self, config: AppConfig):
        self.config = config

    @property
    def _key(self) -> bytes:
        return self.config.secret_key.encode("utf-8")

    def _sign(self, payload: str) -> str:
        digest = hmac.new(self._key, payload.encode("ascii"), hashlib.sha256).digest()
        return _b64encode(digest)

    def issue(self, email: str) -> str:
        """Create a token for `email`, valid for the configured number of hours."""
        expires_at = int(time.time()) + self.config.email_reset_token_expire_hours * 3600
        payload = _b64encode(
            json.dumps(
                {"sub": email, "purpose": PURPOSE, "exp": expires_at},
                separators=(",", ":"),
                sort_keys=True,
            ).encode("utf-8")
        )
        return f"{payload}.{self._sign(payload)}"

    def verify(self, token: str) -> str | None:
        """Return the email the token was issued for, or None if it is not usable.

        Every failure — malformed, wrong signature, expired, or issued for a
        different purpose — returns None, so a caller cannot tell them apart and
        neither can an attacker.
        """
        if not token or "." not in token:
            return None
        payload, _, signature = token.partition(".")
        # Constant-time comparison: a byte-by-byte check would leak how much of
        # a forged signature was correct.
        if not hmac.compare_digest(signature, self._sign(payload)):
            return None
        try:
            claims = json.loads(_b64decode(payload))
        except (ValueError, TypeError):
            return None
        if claims.get("purpose") != PURPOSE:
            return None
        if int(claims.get("exp", 0)) < int(time.time()):
            return None
        subject = claims.get("sub")
        return str(subject) if subject else None
