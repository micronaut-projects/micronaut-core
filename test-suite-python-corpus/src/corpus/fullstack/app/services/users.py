"""User service.

Corresponds to ``crud.py`` plus the user half of ``api/routes/users.py`` in the
upstream template. Keeping the rules here rather than in the controllers means
the same checks apply whether a user is created through the API, through signup
or by the startup bootstrap.
"""

from java.util import UUID
from jakarta.inject import Singleton
from jakarta.transaction import Transactional
from micronaut.data.model import Page, Pageable

from ..dto import UserCreate, UserRegister, UserUpdate, UserUpdateMe
from ..entities import User
from ..repositories import UserRepository
from ..passwords import PasswordHasher


def _java_uuid(value) -> UUID:
    """A java.util.UUID, whether the caller had one or a Python uuid.UUID."""
    return UUID.fromString(str(value))



class EmailAlreadyUsed(Exception):
    """Raised when an email address is already registered."""


@Singleton
class UserService:
    def __init__(self, users: UserRepository, passwords: PasswordHasher):
        self.users = users
        self.passwords = passwords

    # -- lookups ----------------------------------------------------------
    def by_email(self, email: str) -> User | None:
        return self.users.findByEmail(email)

    def by_id(self, user_id: UUID) -> User | None:
        # Normalise before querying. Reading an entity hands back a *Python* uuid.UUID for a
        # java.util.UUID column, and passing that straight into a repository matches nothing at
        # all -- an empty result rather than an error. See CLAUDE.md.
        return self.users.findById(_java_uuid(user_id)).orElse(None)

    def count(self) -> int:
        return int(self.users.count())

    def page(self, pageable: Pageable) -> Page[User]:
        """One page of users, newest first, with the total count attached."""
        return self.users.findAllOrdered(pageable)

    def verify_password(self, user: User, password: str) -> bool:
        """Check a password against a user's stored hash, without side effects."""
        return self.passwords.verify(password, user.hashedPassword)

    # -- authentication ---------------------------------------------------
    @Transactional
    def authenticate(self, email: str, password: str) -> User | None:
        """Verify credentials, upgrading the stored hash when it is outdated."""
        user = self.users.findByEmail(email)
        if user is None:
            return None
        if not self.passwords.verify(password, user.hashedPassword):
            return None
        if self.passwords.needs_rehash(user.hashedPassword):
            user.hashedPassword = self.passwords.hash(password)
            self.users.update(user)
        return user

    # -- creation ---------------------------------------------------------
    @Transactional
    def create(self, data: UserCreate) -> User:
        if self.users.existsByEmail(data.email):
            raise EmailAlreadyUsed(data.email)
        return self.users.save(
            User(
                email=data.email,
                hashedPassword=self.passwords.hash(data.password),
                fullName=data.fullName,
                isActive=data.isActive,
                isSuperuser=data.isSuperuser,
            )
        )

    @Transactional
    def register(self, data: UserRegister) -> User:
        return self.create(
            UserCreate(
                email=data.email,
                password=data.password,
                fullName=data.fullName,
                isActive=True,
                isSuperuser=False,
            )
        )

    # -- updates ----------------------------------------------------------
    @Transactional
    def update(self, user: User, data: UserUpdate) -> User:
        if data.email is not None and data.email != user.email:
            if self.users.existsByEmail(data.email):
                raise EmailAlreadyUsed(data.email)
            user.email = data.email
        if data.fullName is not None:
            user.fullName = data.fullName
        if data.isActive is not None:
            user.isActive = data.isActive
        if data.isSuperuser is not None:
            user.isSuperuser = data.isSuperuser
        if data.password is not None:
            user.hashedPassword = self.passwords.hash(data.password)
        return self.users.update(user)

    @Transactional
    def update_me(self, user: User, data: UserUpdateMe) -> User:
        return self.update(
            user, UserUpdate(email=data.email, fullName=data.fullName)
        )

    @Transactional
    def set_password(self, user: User, new_password: str) -> User:
        user.hashedPassword = self.passwords.hash(new_password)
        return self.users.update(user)

    @Transactional
    def delete(self, user: User) -> None:
        # Items are removed by the ON DELETE CASCADE in V1__initial_schema.sql.
        self.users.delete(user)
