package example;

import io.micronaut.http.server.stack.FullHttpStackBenchmark;
import io.micronaut.http.server.stack.TfbLikeBenchmark;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.moditect.jfrunit.EnableEvent;
import org.moditect.jfrunit.JfrEventTest;
import org.moditect.jfrunit.JfrEvents;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JfrEventTest
public class TypeThrashingTest {
    static final String EVENT_NAME = "io.type.pollution.agent.TypeCheckThrashEvent";
    static final int THRESHOLD = 10_000;

    public JfrEvents jfrEvents = new JfrEvents();

    static {
        FullHttpStackBenchmark.checkFtlThread = false;
    }

    /**
     * This is a sample method that demonstrates the thrashing detection. This test should fail
     * when enabled.
     */
    @SuppressWarnings("ConstantValue")
    @Test
    @EnableEvent(TypeThrashingTest.EVENT_NAME)
    @Disabled
    public void sample() {
        Object c = new Concrete();
        int j = 0;
        for (int i = 0; i < THRESHOLD * 2; i++) {
            if (c instanceof A) {
                j++;
            }
            if (c instanceof B) {
                j++;
            }
        }
        System.out.println(j);
    }

    interface A {
    }

    interface B {
    }

    static class Concrete implements A, B {
    }

    @Test
    @EnableEvent(TypeThrashingTest.EVENT_NAME)
    public void testFromJmh() throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(Stream.of(FullHttpStackBenchmark.class, TfbLikeBenchmark.class)
                .map(Class::getName)
                .collect(Collectors.joining("|", "(", ")"))
                + ".*")
            .warmupIterations(0)
            .measurementIterations(1)
            .mode(Mode.SingleShotTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .forks(0)
            .measurementBatchSize(THRESHOLD * 2)
            .shouldFailOnError(true)
            .build();

        Collection<RunResult> results = new Runner(opt).run();
    }

    @AfterEach
    public void verifyNoTypeThrashing() {
        jfrEvents.awaitEvents();
        long total = 0;
        Map<String, ConcreteType> collectors = new HashMap<>();
        for (RecordedEvent evt : (Iterable<RecordedEvent>) jfrEvents.events()::iterator) {
            if (!evt.getEventType().getName().equals(EVENT_NAME)) {
                continue;
            }
            total++;
            String concreteClass = evt.getString("concreteClass");
            String interfaceClass = evt.getString("interfaceClass");

            if (concreteClass.equals(io.netty.handler.codec.http.DefaultFullHttpResponse.class.getName())) {
                String culpritType = evt.getStackTrace().getFrames().stream().filter(rf -> !isAgentFrame(rf)).findFirst().orElseThrow().getMethod().getType().getName();
                if (culpritType.startsWith("io.netty") || culpritType.equals("io.micronaut.http.server.netty.handler.Compressor")) {
                    // these DefaultFullHttpResponse flips are false positives, fixed by franz
                    continue;
                }
            }

            collectors.computeIfAbsent(concreteClass, ConcreteType::new)
                .add(interfaceClass, evt.getStackTrace());
        }
        Assertions.assertTrue(total > 10, "Not enough events recorded. Something went wrong with the agent or JFRUnit");

        boolean fail = false;
        for (ConcreteType concreteType : collectors.values()) {
            if (concreteType.hits < THRESHOLD) {
                continue;
            }
            fail = true;
            System.out.println("Concrete type: " + concreteType.concreteType + " (" + concreteType.hits + " flips)");
            for (InterfaceType interfaceType : concreteType.interfaceTypes.values()) {
                System.out.println("  Interface type: " + interfaceType.interfaceType);
                for (RecordedStackTrace trace : interfaceType.recordedStackTraces) {
                    long hits = interfaceType.recordedHashes.get(hashCode(trace));
                    if (hits < 10) {
                        continue;
                    }
                    System.out.println("    Trace: (" + hits + " hits)");
                    for (RecordedFrame frame : trace.getFrames()) {
                        if (isAgentFrame(frame)) {
                            continue;
                        }
                        System.out.println("     at " + frame.getMethod().getType().getName() + "." + frame.getMethod().getName() + "(:" + frame.getLineNumber() + ")");
                    }
                }
            }
        }
        Assertions.assertFalse(fail, "Found type check thrashing, please check logs");
    }

    private static boolean isAgentFrame(RecordedFrame frame) {
        return frame.getMethod().getType().getName().startsWith("io.type.pollution.");
    }

    private static final class ConcreteType {
        final String concreteType;
        final Map<String, InterfaceType> interfaceTypes = new HashMap<>();
        long hits = 0;

        ConcreteType(String concreteType) {
            this.concreteType = concreteType;
        }

        void add(String interfaceType, RecordedStackTrace trace) {
            hits++;
            interfaceTypes.computeIfAbsent(interfaceType, InterfaceType::new).add(trace);
        }
    }

    private static final class InterfaceType {
        final String interfaceType;
        final Map<Integer, Long> recordedHashes = new HashMap<>();
        final List<RecordedStackTrace> recordedStackTraces = new ArrayList<>();

        private InterfaceType(String interfaceType) {
            this.interfaceType = interfaceType;
        }

        public void add(RecordedStackTrace trace) {
            if (recordedHashes.compute(TypeThrashingTest.hashCode(trace), (k, oldV) -> oldV == null ? 1 : oldV + 1L) > 1L) {
                return;
            }
            recordedStackTraces.add(trace);
        }
    }

    private static int hashCode(RecordedStackTrace trace) {
        int hash = 0;
        for (RecordedFrame frame : trace.getFrames()) {
            hash = hash * 31 + frame.getBytecodeIndex();
            hash = hash * 31 + frame.getMethod().getType().getName().hashCode();
            hash = hash * 31 + frame.getMethod().getName().hashCode();
            hash = hash * 31 + frame.getMethod().getDescriptor().hashCode();
        }
        return hash;
    }
}
