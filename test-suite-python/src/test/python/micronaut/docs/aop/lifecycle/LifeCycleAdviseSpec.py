from typing import Annotated

from jakarta.inject import Inject
from org.junit.jupiter.api import Test
from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest

from .Product import Product
from .ProductService import ProductService


@MicronautTest
class LifeCycleAdviseSpec:
    application_context: Annotated[ApplicationContext, Inject] = None
    product_service: Annotated[ProductService, Inject] = None

    @Test
    def test_life_cycle_advise(self):
        # tag::test[]
        product_service = self.product_service

        product = self.application_context.createBean(Product, "Apple")  # <1>
        assert product.is_active()
        assert product_service.find_product("APPLE") is not None

        self.application_context.destroyBean(product)  # <2>
        assert not product.is_active()
        assert product_service.find_product("APPLE") is None
        # end::test[]
