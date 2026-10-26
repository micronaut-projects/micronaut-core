from decimal import Decimal
from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .DecimalPriceRepository import DecimalPrice, DecimalPriceRepository


@MicronautTest(transactional=False)
@Property(name="datasources.default.url", value="jdbc:h2:mem:decimalDb;DB_CLOSE_ON_EXIT=FALSE")
@Property(name="datasources.default.schema-generate", value="CREATE_DROP")
@Property(name="datasources.default.dialect", value="H2")
class DecimalPriceRepositorySpec:
    repository: Annotated[DecimalPriceRepository, Inject] = None

    @Test
    def decimal_prices_round_trip_through_jdbc(self) -> None:
        assert self.repository.findByCode("P100") is None
        saved = self.repository.save(DecimalPrice(None, "P100", Decimal("14.50")))
        assert saved.id is not None
        found = self.repository.findByCode("P100")
        assert isinstance(found.amount, Decimal)
        assert found.amount == Decimal("14.50")

        updated = Decimal("-12345678901234567.12345678901234567890")
        assert self.repository.updateByCode("P100", updated) == 1
        found = self.repository.findByCode("P100")
        assert isinstance(found.amount, Decimal)
        assert found.amount == updated
        assert found.amount.as_tuple().exponent == -20
