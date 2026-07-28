from dataclasses import dataclass
from typing import Annotated

# tag::imports[]
from micronaut.context.annotation import ConfigurationProperties
from micronaut.context.annotation import Requires
from jakarta.validation.constraints import NotNull
from java.math import BigDecimal
# end::imports[]


@Requires(property="spec.name", value="ValueAddedTaxConfigurationTest")
# tag::class[]
@ConfigurationProperties("vat")
@dataclass(frozen=True)
class ValueAddedTaxConfiguration:
    percentage: Annotated[BigDecimal, NotNull]  # <1>
# end::class[]
