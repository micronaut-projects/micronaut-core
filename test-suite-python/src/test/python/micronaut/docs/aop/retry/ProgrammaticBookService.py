import java
from jakarta.inject import Singleton
from micronaut.context.annotation import Requires

from .Book import Book

CircuitBreakerOperationsFactory = java.type("io.micronaut.retry.CircuitBreakerOperationsFactory")
CircuitBreakerPolicy = java.type("io.micronaut.retry.CircuitBreakerPolicy")
Duration = java.type("java.time.Duration")
Flux = java.type("reactor.core.publisher.Flux")
CompletableFuture = java.type("java.util.concurrent.CompletableFuture")
RetryOperationsFactory = java.type("io.micronaut.retry.RetryOperationsFactory")
RetryPolicy = java.type("io.micronaut.retry.RetryPolicy")


@Requires(property="spec.name", value="ProgrammaticRetrySpec")
@Singleton
class ProgrammaticBookService:
    def __init__(self,
                 retry_operations_factory: RetryOperationsFactory,
                 circuit_breaker_operations_factory: CircuitBreakerOperationsFactory):
        # tag::programmatic-policy[]
        retry_policy = RetryPolicy.builder() \
            .maxAttempts(5) \
            .delay(Duration.ofMillis(5)) \
            .build()
        circuit_breaker_policy = CircuitBreakerPolicy.builder() \
            .maxAttempts(3) \
            .delay(Duration.ofMillis(5)) \
            .resetTimeout(Duration.ofMillis(100)) \
            .build()
        # end::programmatic-policy[]
        self.retry_operations = retry_operations_factory.createRetryOperations(retry_policy)
        self.circuit_breaker_operations = circuit_breaker_operations_factory.createCircuitBreakerOperations(circuit_breaker_policy)
        self.reset()

    def reset(self) -> None:
        self.sync_counter = 0
        self.reactive_counter = 0
        self.async_counter = 0
        self.circuit_counter = 0

    # tag::programmatic-sync[]
    def list_books(self) -> list[Book]:
        def supplier():
            self.sync_counter += 1
            if self.sync_counter < 3:
                raise java.type("java.lang.IllegalStateException")("Temporary failure")
            return [Book("The Stand")]
        return self.retry_operations.execute(supplier)
    # end::programmatic-sync[]

    # tag::programmatic-reactive[]
    def stream_books(self):
        def supplier():
            def publisher_supplier():
                self.reactive_counter += 1
                if self.reactive_counter < 3:
                    return Flux.error(java.type("java.lang.IllegalStateException")("Temporary failure"))
                return Flux.just(Book("The Stand"))
            return Flux.defer(publisher_supplier)
        return self.retry_operations.executePublisher(supplier)
    # end::programmatic-reactive[]

    # tag::programmatic-async[]
    def find_book(self, title: str):
        def supplier():
            def async_supplier():
                self.async_counter += 1
                if self.async_counter < 3:
                    raise java.type("java.lang.IllegalStateException")("Temporary failure")
                return Book(title)
            return CompletableFuture.supplyAsync(async_supplier)
        return self.retry_operations.executeCompletionStage(supplier)
    # end::programmatic-async[]

    # tag::programmatic-circuit[]
    def find_book_with_circuit_breaker(self, title: str) -> Book:
        def supplier():
            self.circuit_counter += 1
            if self.circuit_counter < 4:
                raise java.type("java.lang.IllegalStateException")("Circuit failure")
            return Book(title)
        return self.circuit_breaker_operations.execute(supplier)
    # end::programmatic-circuit[]
