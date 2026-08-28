from dataclasses import dataclass
from typing import Annotated

from jakarta.validation.constraints import Min, NotBlank
from micronaut.context.annotation import ConfigurationProperties


@ConfigurationProperties("petclinic")
@dataclass
class ClinicConfiguration:
    name: Annotated[str, NotBlank] = "Pyronaut PetClinic"
    page_size: Annotated[int, Min(1)] = 25
    show_visits: bool = True
    supported_locales: list[str] = None

    def locale_count(self) -> int:
        return len(self.supported_locales or ["en"])


@ConfigurationProperties("petclinic.notifications")
@dataclass
class NotificationConfiguration:
    enabled: bool = True
    sender: Annotated[str, NotBlank] = "clinic@example.test"
    retry_count: Annotated[int, Min(0)] = 3
