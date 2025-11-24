from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest


from micronaut.context import ApplicationContext
from jakarta.inject import Inject
from typing import Annotated
import java

@MicronautTest
class OrderTest:
    context : Annotated[ApplicationContext, Inject] = None

    @Test
    def test_order_on_factories(self):
        RateLimit = java.type("micronaut.docs.config.env.RateLimit")
        rateLimits = self.context.getBeansOfType(RateLimit)

        assert rateLimits.size() == 2, "Should have two rate limits"
        assert rateLimits.get(0).asPolyglotValue().limit == 1000, "High rate limit should be first"
        assert rateLimits.get(1).asPolyglotValue().limit == 100, "Low rate limit should be last"

