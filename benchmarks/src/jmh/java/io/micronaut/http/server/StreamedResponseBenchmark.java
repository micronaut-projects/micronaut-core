package io.micronaut.http.server;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.ContextlessMessageBodyHandlerRegistry;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.netty.body.NettyJsonHandler;
import io.micronaut.http.netty.body.PieceReaderBenchmarkSupport.Book;
import io.micronaut.json.JsonMapper;
import io.micronaut.runtime.ApplicationConfiguration;
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
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * A response whose body is a publisher, encoded by the server: each element written by its
 * writer, the pieces concatenated into the body of the response, and the body read to the end.
 * {@code text} writes strings with the plain text writer, so that the plumbing between the
 * publisher and the body dominates; {@code json} writes objects as the elements of a JSON array.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class StreamedResponseBenchmark {

    @Param({"text", "json"})
    String format;

    @Param({"1000"})
    int elements;

    private final HttpRequest<?> request = HttpRequest.GET("/");
    private ResponseLifecycle lifecycle;
    private List<Object> values;
    private MediaType mediaType;

    @Setup
    public void setup() {
        ContextlessMessageBodyHandlerRegistry registry = new ContextlessMessageBodyHandlerRegistry(new ApplicationConfiguration(), NettyByteBufferFactory.DEFAULT);
        registry.add(MediaType.APPLICATION_JSON_TYPE, new NettyJsonHandler<>(JsonMapper.createDefault()));
        lifecycle = new ResponseLifecycle(null, registry, ConversionService.SHARED, ByteBodyFactory.createDefault(NettyByteBufferFactory.DEFAULT)) {
            @Override
            protected Executor ioExecutor() {
                return Runnable::run;
            }
        };
        values = new ArrayList<>(elements);
        for (int i = 0; i < elements; i++) {
            if ("json".equals(format)) {
                Book book = new Book();
                book.id = i;
                book.title = "Book " + i;
                book.pages = i;
                values.add(book);
            } else {
                values.add("Element " + i + " of the streamed response, about fifty bytes");
            }
        }
        mediaType = "json".equals(format) ? MediaType.APPLICATION_JSON_TYPE : MediaType.TEXT_PLAIN_TYPE;
        if (write() == 0) {
            throw new IllegalStateException("Nothing was written");
        }
    }

    @Benchmark
    public long write() {
        HttpResponse<?> response = HttpResponse.ok(Flux.fromIterable(values)).contentType(mediaType);
        ByteBodyHttpResponse<?> encoded = Objects.requireNonNull(lifecycle.encodeHttpResponseSafe(request, response).tryCompleteValue());
        try (CloseableAvailableByteBody body = Objects.requireNonNull(InternalByteBody.bufferFlow(encoded.byteBody()).tryCompleteValue())) {
            return body.length();
        }
    }
}
