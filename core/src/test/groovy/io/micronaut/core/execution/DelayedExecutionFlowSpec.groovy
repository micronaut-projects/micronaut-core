package io.micronaut.core.execution


import org.apache.groovy.internal.util.Function
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class DelayedExecutionFlowSpec extends Specification {
    def "single thread permutations"(List<Integer> orderOfCompletion) {
        given:
        List<CompletableFuture<String>> futures = [null, new CompletableFuture<String>(), new CompletableFuture<String>(), new CompletableFuture<String>()]
        List<String> results = ["step0", "step2", "step3", "step5"]
        ExecutionFlow<String> inputFlow = new DelayedExecutionFlowImpl<>()
        ExecutionFlow<String> flowStep2 = CompletableFutureExecutionFlow.just(futures[1])
        ExecutionFlow<String> flowStep3 = CompletableFutureExecutionFlow.just(futures[2])
        ExecutionFlow<String> flowStep5 = CompletableFutureExecutionFlow.just(futures[3])
        String output = null
        List<Function<ExecutionFlow<String>, ExecutionFlow<String>>> permTestSteps = [
                (ExecutionFlow<String> prev) -> prev.map {
                    assert it == "step0"
                    return "step1"
                },
                (ExecutionFlow<String> prev) -> prev.flatMap {
                    assert it == "step1"
                    return flowStep2
                },
                (ExecutionFlow<String> prev) -> prev.then {
                    return flowStep3
                },
                (ExecutionFlow<String> prev) -> prev.map {
                    assert it == "step3"
                    throw new RuntimeException("step4")
                },
                (ExecutionFlow<String> prev) -> prev.map {
                    throw new AssertionError("should not be called")
                },
                (ExecutionFlow<String> prev) -> prev.flatMap {
                    throw new AssertionError("should not be called")
                },
                (ExecutionFlow<String> prev) -> prev.then {
                    throw new AssertionError("should not be called")
                },
                (ExecutionFlow<String> prev) -> prev.onErrorResume {
                    assert it.message == "step4"
                    return flowStep5
                },
                (ExecutionFlow<String> prev) -> prev.onComplete((s, t) -> output = s),
        ]

        ExecutionFlow<String> flow = inputFlow
        for (int i = 0; i < permTestSteps.size(); i++) {
            for (int j = 0; j < orderOfCompletion.size(); j++) {
                if (orderOfCompletion[j] == i) {
                    if (j == 0) {
                        inputFlow.complete(results[j])
                    } else {
                        futures[j].complete(results[j])
                    }
                }
            }
            flow = permTestSteps[i](flow)
        }

        where:
        orderOfCompletion << powerSet([0, 1, 2, 3, 4, 5, 6, 7, 8], 4)
    }

    def "mapping"() {
        given:
            DelayedExecutionFlow<String> delayedExecutionFlow = DelayedExecutionFlow.create()
        when:
            def flow = delayedExecutionFlow
                    .map { it + "B"}
                    .map { it + "C"}
                    .map { it + "D"}
                    .map { it + "E"}
                    .flatMap { ExecutionFlow.just(it + "F")}
        then:
            delayedExecutionFlow.complete("A")
            flow.tryComplete().value == "ABCDEF"
            delayedExecutionFlow.@head == null
        when:
            delayedExecutionFlow.complete("X")
        then:
            def e = thrown(IllegalStateException)
            e.message == "Delayed flow has been completed"
    }

    private static <T> List<List<T>> powerSet(List<T> base, int exp) {
        if (exp == 0) {
            return [[]]
        }
        List<List<T>> output = []
        List<List<T>> next = powerSet(base, exp - 1)
        for (T t : base) {
            for (List<T> head : next) {
                output.add(head + t)
            }
        }
        return output
    }
}
