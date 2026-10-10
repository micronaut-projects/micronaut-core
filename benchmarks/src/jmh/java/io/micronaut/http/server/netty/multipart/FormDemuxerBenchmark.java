package io.micronaut.http.server.netty.multipart;

import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.contrib.multipart.PostBodyDecoder;
import org.jspecify.annotations.Nullable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * The fields of a URL encoded form that arrived whole, demuxed and read one field at a time, as
 * the form binding reads them.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class FormDemuxerBenchmark {

    @Param({"200"})
    int fields;

    private final EmbeddedChannel channel = new EmbeddedChannel();
    private NettyByteBodyFactory factory;
    private byte[] form;

    @Setup
    public void setup() {
        factory = new NettyByteBodyFactory(channel);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fields; i++) {
            if (i > 0) {
                builder.append('&');
            }
            builder.append("field").append(i).append('=').append("value").append(i);
        }
        form = builder.toString().getBytes(StandardCharsets.UTF_8);
        if (read() != fields) {
            throw new IllegalStateException("Not every field was read");
        }
    }

    @Benchmark
    public int read() {
        FormDemuxer demuxer = new FormDemuxer(PostBodyDecoder.builder().maxFields(fields).forUrlEncodedData(), channel,
            BodySizeLimits.UNLIMITED, BodySizeLimits.UNLIMITED, factory.adapt(form));
        OneAtATime reader = new OneAtATime();
        demuxer.fields().subscribe(reader);
        if (!reader.complete) {
            throw new IllegalStateException("The form did not complete");
        }
        return reader.count;
    }

    private static final class OneAtATime implements Subscriber<RawFormField> {
        private @Nullable Subscription subscription;
        private int count;
        private boolean complete;

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            s.request(1);
        }

        @Override
        public void onNext(RawFormField field) {
            field.close();
            count++;
            subscription.request(1);
        }

        @Override
        public void onError(Throwable t) {
            throw new IllegalStateException(t);
        }

        @Override
        public void onComplete() {
            complete = true;
        }
    }
}
