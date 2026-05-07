from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any

from jakarta.inject import Named, Singleton
from micronaut.core.annotation import Introspected, ReflectiveAccess

from .ChristmasTypes import ChristmasPresent, Present, PresentPackaging

# tag::mapper[]
from micronaut.context.annotation import Mapper


@dataclass
@ReflectiveAccess
@Introspected
class Card:
    greeting_card: str


class AdditionalMappers(ABC):
    @Mapper  # <1>
    @abstractmethod
    def merge(
        self,
        packaging: PresentPackaging,
        present: Present,
        christmas_card: Card,
    ) -> ChristmasPresent:
        pass

    @Mapper.Mapping(
        **{"from": "#{update_fields['christmas_card'] + '!!'}", "to": "greeting_card"}
    )  # <2>
    @abstractmethod
    def update(
        self,
        present: ChristmasPresent,
        update_fields: dict[str, Any],
    ) -> ChristmasPresent:
        pass

    @Mapper(
        mergeStrategy="add-numbers",
        value=[Mapper.Mapping(**{"from": "packaging.color", "to": "packaging_color"})],
    )  # <3>
    @abstractmethod
    def merge_with_merge_strategy(
        self,
        packaging: PresentPackaging,
        present: Present,
    ) -> ChristmasPresent:
        pass


@Singleton
@Named("add-numbers")
class MyMergeStrategy(Mapper.MergeStrategy):
    def merge(
        self,
        current_value: Any,
        value: Any,
        value_owner: Any,
        property_name: str,
        mapped_property_name: str,
    ) -> Any:
        if isinstance(current_value, float) and isinstance(value, float):
            return current_value + value
        return value
# end::mapper[]
