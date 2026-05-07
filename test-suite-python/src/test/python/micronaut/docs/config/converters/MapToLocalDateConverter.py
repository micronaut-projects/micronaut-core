import java
from micronaut.context.annotation import Requires

# tag::imports[]
from micronaut.context.annotation import Prototype
from micronaut.core.convert import ConversionContext
from micronaut.core.convert import ConversionService
from micronaut.core.convert import TypeConverter

from java.time import DateTimeException
from java.time import LocalDate
from java.util import Map
from java.util import Optional
# end::imports[]

Integer = java.type("java.lang.Integer")


@Requires(property="spec.name", value="MyConfigurationPropertiesSpec")
# tag::class[]
@Prototype
class MapToLocalDateConverter(TypeConverter[Map, LocalDate]):  # <1>
    def __init__(self, conversion_service: ConversionService):  # <2>
        self.conversion_service = conversion_service

    def convert(self, property_map: Map, target_type: type[LocalDate], context: ConversionContext):
        day = self.conversion_service.convert(property_map.get("day"), Integer)
        month = self.conversion_service.convert(property_map.get("month"), Integer)
        year = self.conversion_service.convert(property_map.get("year"), Integer)
        if day.isPresent() and month.isPresent() and year.isPresent():
            try:
                return Optional.of(LocalDate.of(year.get(), month.get(), day.get()))  # <3>
            except DateTimeException as e:
                context.reject(property_map, e)  # <4>
                return Optional.empty()

        return Optional.empty()
# end::class[]
