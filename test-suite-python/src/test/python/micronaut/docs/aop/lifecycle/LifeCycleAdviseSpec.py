from org.junit.jupiter.api import Disabled, Test

from micronaut.context import ApplicationContext
from micronaut.test.extensions.junit5.annotation import MicronautTest

from .Product import Product
from .ProductService import ProductService


@MicronautTest
class LifeCycleAdviseSpec:
    @Test
    @Disabled("Python lifecycle @InterceptorBinding metadata for AroundConstruct/PostConstruct/PreDestroy is not fully supported yet")
    def test_life_cycle_advise(self):
        context = ApplicationContext.run()
        try:
            # tag::test[]
            product_service = context.getBean(ProductService)

            product = context.createBean(Product, "Apple")  # <1>
            assert product.is_active()
            assert product_service.find_product("APPLE") is not None

            context.destroyBean(product)  # <2>
            assert not product.is_active()
            assert product_service.find_product("APPLE") is None
            # end::test[]
        finally:
            context.close()
