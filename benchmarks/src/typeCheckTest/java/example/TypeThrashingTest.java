package example;

import io.micronaut.http.server.stack.ControllersBenchmark;
import io.micronaut.http.server.stack.FullHttpStackBenchmark;
import io.micronaut.test.typepollution.FocusListener;
import io.micronaut.test.typepollution.ThresholdFocusListener;
import io.micronaut.test.typepollution.TypePollutionTransformer;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeThrashingTest {
    static final int THRESHOLD = 10_000;

    static {
        FullHttpStackBenchmark.checkFtlThread = false;
    }

    private ThresholdFocusListener focusListener;

    @BeforeAll
    static void setupAgent() {
        java.lang.instrument.Instrumentation inst = net.bytebuddy.agent.ByteBuddyAgent.install();
        net.bytebuddy.agent.builder.AgentBuilder.Transformer transformer = io.micronaut.test.typepollution.TypePollutionTransformer.create();
        new net.bytebuddy.agent.builder.AgentBuilder.Default()
            // Ignore Gradle and Byte Buddy internals to avoid resolution issues
            .ignore(net.bytebuddy.matcher.ElementMatchers.nameStartsWith("org.gradle.")
                .or(net.bytebuddy.matcher.ElementMatchers.nameStartsWith("net.bytebuddy.")))
            // Only transform Micronaut classes (the code under test)
            .type(net.bytebuddy.matcher.ElementMatchers.nameStartsWith("io.micronaut."))
            .transform(transformer)
            .with(net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .installOn(inst);
    }

    @BeforeEach
    void setUp() {
        focusListener = new ThresholdFocusListener();
        FocusListener.setFocusListener((concreteType, interfaceType) -> {
            if (concreteType == DefaultFullHttpResponse.class) {
                String culprit = StackWalker.getInstance().walk(s -> s.skip(1).dropWhile(f -> f.getClassName().startsWith("io.micronaut.test.")).findFirst().map(StackWalker.StackFrame::getClassName).orElse(null));
                if (culprit != null && (culprit.startsWith("io.netty") || culprit.equals("io.micronaut.http.server.netty.handler.Compressor"))) {
                    // these DefaultFullHttpResponse flips are false positives, fixed by franz
                    return;
                }
            }

            focusListener.onFocus(concreteType, interfaceType);
        });
    }

    @AfterEach
    void verifyNoTypeThrashing() {
        FocusListener.setFocusListener(null);
        Assertions.assertTrue(focusListener.checkThresholds(THRESHOLD), "Threshold exceeded, check logs.");
    }

    /**
     * This is a sample method that demonstrates the thrashing detection. This test should fail
     * when enabled.
     */
    @SuppressWarnings("ConstantValue")
    @Test
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
    public void testFromJmh() throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(Stream.of(FullHttpStackBenchmark.class, ControllersBenchmark.class)
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

        new Runner(opt).run();
    }
}
