from org.junit.jupiter.api import Test
from micronaut.test.extensions.junit5.annotation import MicronautTest

from .PaymentAuditInterceptor import PaymentAuditInterceptor
from .PaymentService import PaymentService


@MicronautTest
class AuditBindingSpec:

    # tag::test[]
    @Test
    def test_every_occurrence_must_match(self, payment_service: PaymentService,
                                         interceptor: PaymentAuditInterceptor):
        payment_service.pay()
        payment_service.refund()

        assert interceptor.audited == ["pay"]
    # end::test[]
