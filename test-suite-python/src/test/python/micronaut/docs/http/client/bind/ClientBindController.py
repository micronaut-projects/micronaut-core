from typing import Annotated

from jakarta.annotation import Nullable
from micronaut.http.annotation import Controller, Get, Header, QueryValue


@Controller
class ClientBindController:

    @Get("/client/bind")
    def test(self, version: Annotated[str, Header("X-Metadata-Version")]) -> str:
        return version

    @Get("/client/authorized-resource{?name}")
    def authorized(self, name: Annotated[str | None, QueryValue, Nullable]) -> str:
        return f"Hello, {name}"
