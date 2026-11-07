package io.micronaut.http.server.netty;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * What the server pays per connection for its customizers: the composite specializes every member
 * for the new channel and notifies the result once the pipeline is built. The composite is the
 * one the server registers customizers on, so this measures the empty, single and multiple member
 * cases, with members that keep themselves for the channel and members that specialize.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ServerCustomizerBenchmark {

    /**
     * The number of registered customizers.
     */
    @Param({"0", "1", "4"})
    int members;

    /**
     * Whether a member returns a new customizer for the channel, or itself.
     */
    @Param({"false", "true"})
    boolean specializing;

    CompositeNettyServerCustomizer composite;
    Channel channel;

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(ServerCustomizerBenchmark.class.getName() + ".*")
            .warmupIterations(5)
            .measurementIterations(5)
            .forks(1)
            .build();
        new Runner(opt).run();
    }

    @Setup
    public void setUp() {
        composite = new CompositeNettyServerCustomizer();
        for (int i = 0; i < members; i++) {
            composite.add(specializing ? new SpecializingCustomizer() : new KeepingCustomizer());
        }
        channel = new EmbeddedChannel();
    }

    @TearDown
    public void tearDown() {
        channel.close();
    }

    @Benchmark
    public NettyServerCustomizer connection() {
        NettyServerCustomizer forConnection = composite.specializeForChannel(channel, NettyServerCustomizer.ChannelRole.CONNECTION);
        forConnection.onInitialPipelineBuilt();
        return forConnection;
    }

    /**
     * A customizer that needs nothing per channel.
     */
    static final class KeepingCustomizer implements NettyServerCustomizer {
        int built;

        @Override
        public void onInitialPipelineBuilt() {
            built++;
        }
    }

    /**
     * A customizer that keeps state per channel.
     */
    static final class SpecializingCustomizer implements NettyServerCustomizer {
        private final Channel channel;

        SpecializingCustomizer() {
            this(null);
        }

        private SpecializingCustomizer(Channel channel) {
            this.channel = channel;
        }

        @Override
        public NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            return new SpecializingCustomizer(channel);
        }

        @Override
        public void onInitialPipelineBuilt() {
            if (channel == null) {
                throw new IllegalStateException("not specialized");
            }
        }
    }
}
