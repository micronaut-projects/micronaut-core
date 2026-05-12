from dataclasses import dataclass

from micronaut.core.annotation import Introspected, ReflectiveAccess


# tag::class[]
@ReflectiveAccess
@Introspected
@dataclass
class Headline:
    title: str | None = None
    description: str | None = None

    def getTitle(self) -> str | None:
        return self.title

    def getDescription(self) -> str | None:
        return self.description

    def setTitle(self, title: str) -> None:
        self.title = title

    def setDescription(self, description: str) -> None:
        self.description = description
# end::class[]
