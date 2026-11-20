package io.micronaut.core.propagation;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Micro benchmarks of the propagated context hot paths. Run once with the default JMH executor
 * (plain {@link ThreadLocal}) and once with
 * {@code -Djmh.executor=CUSTOM -Djmh.executor.class=io.micronaut.http.server.stack.JmhFastThreadLocalExecutor}
 * (netty {@code FastThreadLocal}, the event loop case).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class PropagatedContextBenchmark {

    @Param({"THREAD_LOCAL", "SCOPED_VALUE"})
    public PropagatedContextConfiguration.Mode mode;

    PropagatedContext plain;
    PropagatedContext plainChild;
    PropagatedContext withThreadElement;
    PropagatedContext withTwoThreadElements;
    Supplier<Object> supplier;
    Object value = new Object();

    record Element(int id) implements PropagatedContextElement {
    }

    static final class ThreadElement implements ThreadPropagatedContextElement<Object> {
        static final ThreadLocal<Object> TL = new ThreadLocal<>();
        final Object value = new Object();

        @Override
        public Object updateThreadContext() {
            Object old = TL.get();
            TL.set(value);
            return old;
        }

        @Override
        public void restoreThreadContext(Object oldState) {
            if (oldState == null) {
                TL.remove();
            } else {
                TL.set(oldState);
            }
        }
    }

    @Setup(Level.Trial)
    public void setUp() {
        PropagatedContextConfiguration.set(mode);
        plain = PropagatedContext.empty().plus(new Element(1));
        plainChild = plain.plus(new Element(2));
        withThreadElement = plain.plus(new ThreadElement());
        withTwoThreadElements = withThreadElement.plus(new ThreadElement());
        supplier = () -> value;
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        PropagatedContextConfiguration.reset();
    }

    /** The event loop case: nothing bound, bind a request context for a task. */
    @Benchmark
    public Object propagateFromNone() {
        return plain.propagate(supplier);
    }

    /** Already bound: the short-circuit. */
    @Benchmark
    public Object propagateSame() {
        return plain.propagate(() -> plain.propagate(supplier));
    }

    /** A filter adding an element and propagating inside the request context. */
    @Benchmark
    public Object propagateNested() {
        return plain.propagate(() -> plainChild.propagate(supplier));
    }

    /** An MDC-like thread element. */
    @Benchmark
    public Object propagateThreadElement() {
        return withThreadElement.propagate(supplier);
    }

    @Benchmark
    public Object propagateTwoThreadElements() {
        return withTwoThreadElements.propagate(supplier);
    }

    /** RoutingInBoundHandler: getOrEmpty().plus(request).propagate(...). */
    @Benchmark
    public Object plusAndPropagate() {
        return PropagatedContext.getOrEmpty().plus(new Element(3)).propagate(supplier);
    }

    @Benchmark
    public Object getOrEmptyUnbound() {
        return PropagatedContext.getOrEmpty();
    }

    @Benchmark
    public Object findInside() {
        return plainChild.propagate(() -> PropagatedContext.get().find(Element.class).orElse(null));
    }

    @Benchmark
    public void wrapCurrentInside(Blackhole bh) {
        plain.propagate(() -> {
            Runnable r = PropagatedContext.wrapCurrent(() -> bh.consume(value));
            r.run();
        });
    }
}
