from java.util.concurrent import CompletableFuture
from java.util.function import Function, UnaryOperator
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@MicronautTest(startApplication=False)
class NullArgumentSpec:

    @Test
    def voidStageCompletesTheLambdaWithNone(self):
        stage = CompletableFuture.completedFuture(None)
        assert stage.thenApply(lambda done: "done " + repr(done)).join() == "done None"

    @Test
    def nullArgumentReachesTheLambdaAsNone(self):
        assert CompletableFuture.completedFuture(None).thenCompose(
            lambda done: CompletableFuture.completedFuture(repr(done))).join() == "None"
        assert Function.identity().andThen(lambda value: repr(value)).apply(None) == "None"
        assert UnaryOperator.identity().andThen(lambda value: repr(value)).apply(None) == "None"
        received = []
        CompletableFuture.completedFuture(None).thenAccept(lambda value: received.append(value)).join()
        assert received == [None]
        assert CompletableFuture.completedFuture(None).thenCombine(
            CompletableFuture.completedFuture("x"), lambda left, right: repr((left, right))).join() == "(None, 'x')"
