from io.micronaut.docs.aop.binding import Audit
from jakarta.inject import Singleton


# tag::example[]
@Singleton
@Audit("security", reason="card data")  # <1>
@Audit("billing")
class PaymentService:
    def pay(self) -> None:  # <2>
        pass  # charge the payment

    @Audit("billing")  # <3>
    def refund(self) -> None:
        pass  # return the payment
# end::example[]
