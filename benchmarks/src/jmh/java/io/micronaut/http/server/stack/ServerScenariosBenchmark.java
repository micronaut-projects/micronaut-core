package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Assertions;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark covering server scenarios that the TechEmpower-like benchmarks do not exercise:
 * multipart uploads, url-encoded forms, larger JSON request bodies, chunked (streaming) request
 * bodies and streaming (reactive) JSON responses.
 *
 * <p>All requests are pre-encoded to bytes and written to an {@link EmbeddedChannel} built from
 * the real server pipeline, so the measured time is the full server-side stack minus the socket.
 */
public class ServerScenariosBenchmark {
    private static final int LARGE_ITEMS = 500;
    private static final int STREAM_ITEMS = 200;
    private static final int FILE_SIZE = 64 * 1024;
    private static final int CHUNK_SIZE = 4096;

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(ServerScenariosBenchmark.class.getName() + ".*")
            .warmupIterations(10)
            .measurementIterations(10)
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.MICROSECONDS)
            .forks(1)
            .build();

        new Runner(opt).run();
    }

    @Benchmark
    public void test(Holder holder) {
        holder.exchangeAndVerify();
    }

    @State(Scope.Thread)
    public static class Holder {
        @Param
        Scenario scenario;

        ApplicationContext ctx;
        EmbeddedChannel channel;
        ByteBuf requestBytes;
        ByteBuf responseBytes;

        @Setup
        public void setUp() {
            ctx = ApplicationContext.run(Map.of(
                "spec.name", "ServerScenariosBenchmark",
                "micronaut.python.enabled", false,
                "micronaut.server.date-header", false // disabling this makes the response identical each time
            ));
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            channel = ((NettyHttpServer) server).buildEmbeddedChannel(false);

            EmbeddedChannel clientChannel = new EmbeddedChannel();
            clientChannel.pipeline().addLast(new HttpClientCodec());
            clientChannel.pipeline().addLast(new HttpObjectAggregator(100_000_000));

            for (Object msg : scenario.request()) {
                clientChannel.writeOutbound(msg);
            }
            clientChannel.flushOutbound();

            requestBytes = NettyUtil.readAllOutboundContiguous(clientChannel);

            // sanity check: run req/resp once and see that the response is correct
            responseBytes = exchange();
            clientChannel.writeInbound(responseBytes.retainedDuplicate());
            FullHttpResponse response = clientChannel.readInbound();
            try {
                scenario.verifyResponse(response);
            } finally {
                response.release();
            }
        }

        private void send() {
            channel.writeInbound(requestBytes.retainedDuplicate());
            channel.runPendingTasks();
            // some scenarios may complete on another thread (e.g. blocking executor); wait for output
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (channel.outboundMessages().isEmpty()) {
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException("No response produced for " + scenario);
                }
                Thread.onSpinWait();
                channel.runPendingTasks();
            }
        }

        /**
         * Used once during setup to capture the expected response as one contiguous buffer.
         */
        ByteBuf exchange() {
            send();
            ByteBuf out = PooledByteBufAllocator.DEFAULT.buffer();
            // make sure the whole response (including trailing chunks) has been flushed
            while (true) {
                ByteBuf part = channel.readOutbound();
                if (part == null) {
                    channel.runPendingTasks();
                    part = channel.readOutbound();
                    if (part == null) {
                        break;
                    }
                }
                out.writeBytes(part);
                part.release();
            }
            return out;
        }

        /**
         * Benchmark body: send the request and compare each outbound part against the expected
         * response without building a composite (composite comparison is far more expensive than
         * the server work being measured).
         */
        void exchangeAndVerify() {
            send();
            int offset = 0;
            int expectedLength = responseBytes.readableBytes();
            while (true) {
                ByteBuf part = channel.readOutbound();
                if (part == null) {
                    channel.runPendingTasks();
                    part = channel.readOutbound();
                    if (part == null) {
                        break;
                    }
                }
                int n = part.readableBytes();
                boolean ok = offset + n <= expectedLength
                    && ByteBufUtil.equals(responseBytes, responseBytes.readerIndex() + offset, part, part.readerIndex(), n);
                part.release();
                if (!ok) {
                    throw new AssertionError("Response did not match at offset " + offset);
                }
                offset += n;
            }
            if (offset != expectedLength) {
                throw new AssertionError("Response length " + offset + " != " + expectedLength);
            }
        }

        @TearDown
        public void tearDown() {
            ctx.close();
            requestBytes.release();
            responseBytes.release();
        }
    }

    public enum Scenario {
        /**
         * Small multipart form: one text field and one 64KiB file, bound with {@link CompletedFileUpload}.
         */
        MULTIPART_FILE_COMPLETED {
            @Override
            List<Object> request() {
                return List.of(multipart("/scenarios/multipart/completed"));
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("text/plain", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
                Assertions.assertEquals("title=hello,file=upload.bin," + FILE_SIZE, response.content().toString(StandardCharsets.UTF_8));
            }
        },
        /**
         * Same multipart body, but bound to a raw {@code byte[]} part.
         */
        MULTIPART_FILE_BYTES {
            @Override
            List<Object> request() {
                return List.of(multipart("/scenarios/multipart/bytes"));
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("title=hello,file=" + FILE_SIZE, response.content().toString(StandardCharsets.UTF_8));
            }
        },
        /**
         * Same multipart body, delivered as 4KiB chunks with chunked transfer encoding (exercises the
         * streaming path through the form demuxer).
         */
        MULTIPART_FILE_CHUNKED {
            @Override
            List<Object> request() {
                FullHttpRequest full = multipart("/scenarios/multipart/completed");
                return chunked(full);
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("title=hello,file=upload.bin," + FILE_SIZE, response.content().toString(StandardCharsets.UTF_8));
            }
        },
        /**
         * application/x-www-form-urlencoded body bound to an introspected POJO.
         */
        FORM_URLENCODED_POJO {
            @Override
            List<Object> request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/scenarios/form",
                    Unpooled.wrappedBuffer("name=Fred&age=42&email=fred%40example.com&tags=a&tags=b&tags=c".getBytes(StandardCharsets.UTF_8)));
                request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED);
                request.headers().add(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());
                request.headers().add(HttpHeaderNames.ACCEPT, MediaType.TEXT_PLAIN);
                return List.of(request);
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("Fred/42/fred@example.com/3", response.content().toString(StandardCharsets.UTF_8));
            }
        },
        /**
         * A ~30KiB JSON array request body deserialized into a {@code List<Item>} in one go.
         */
        JSON_POST_LARGE_LIST {
            @Override
            List<Object> request() {
                return List.of(jsonList("/scenarios/json/list"));
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
                Assertions.assertEquals("{\"count\":" + LARGE_ITEMS + ",\"sum\":" + sumIds() + "}", response.content().toString(StandardCharsets.UTF_8));
            }
        },
        /**
         * Same JSON array but sent as 4KiB chunks with chunked transfer encoding (exercises buffering
         * of a streamed body before deserialization).
         */
        JSON_POST_LARGE_LIST_CHUNKED {
            @Override
            List<Object> request() {
                return chunked(jsonList("/scenarios/json/list"));
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("{\"count\":" + LARGE_ITEMS + ",\"sum\":" + sumIds() + "}", response.content().toString(StandardCharsets.UTF_8));
            }
        },
        /**
         * Same JSON array bound as {@code Flux<Item>} (incremental JSON parsing of a streamed body).
         */
        JSON_POST_LARGE_LIST_REACTIVE_CHUNKED {
            @Override
            List<Object> request() {
                return chunked(jsonList("/scenarios/json/flux"));
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("{\"count\":" + LARGE_ITEMS + ",\"sum\":" + sumIds() + "}", response.content().toString(StandardCharsets.UTF_8));
            }
        },
        /**
         * GET producing a {@code Flux<Item>} of 200 elements serialized as a JSON array (chunked response).
         */
        JSON_STREAM_RESPONSE {
            @Override
            List<Object> request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/scenarios/json/stream");
                request.headers().add(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON);
                return List.of(request);
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals("application/json", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
                String body = response.content().toString(StandardCharsets.UTF_8);
                Assertions.assertTrue(body.startsWith("[{\"id\":0,"), body);
                Assertions.assertTrue(body.endsWith("}]"), body);
                // 3 commas inside each item plus one separator between items
                Assertions.assertEquals(STREAM_ITEMS * 3L + STREAM_ITEMS - 1, body.chars().filter(c -> c == ',').count());
            }
        },
        /**
         * GET producing a {@code Flux<Item>} of 200 elements as application/x-json-stream (NDJSON style).
         */
        JSON_STREAM_RESPONSE_NDJSON {
            @Override
            List<Object> request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/scenarios/json/stream-ndjson");
                request.headers().add(HttpHeaderNames.ACCEPT, MediaType.APPLICATION_JSON_STREAM);
                return List.of(request);
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals(MediaType.APPLICATION_JSON_STREAM, response.headers().get(HttpHeaderNames.CONTENT_TYPE));
                String body = response.content().toString(StandardCharsets.UTF_8);
                Assertions.assertTrue(body.startsWith("{\"id\":0,"), body);
                Assertions.assertFalse(body.startsWith("["), body);
                Assertions.assertEquals(STREAM_ITEMS, countOccurrences(body, "{\"id\":"));
            }
        },
        /**
         * GET producing a 64KiB text/plain body (single large write).
         */
        LARGE_TEXT_RESPONSE {
            @Override
            List<Object> request() {
                FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/scenarios/text/large");
                request.headers().add(HttpHeaderNames.ACCEPT, MediaType.TEXT_PLAIN);
                return List.of(request);
            }

            @Override
            void verifyResponse(FullHttpResponse response) {
                Assertions.assertEquals(HttpResponseStatus.OK, response.status());
                Assertions.assertEquals(FILE_SIZE, response.content().readableBytes());
                Assertions.assertEquals(FILE_SIZE, response.headers().getInt(HttpHeaderNames.CONTENT_LENGTH));
            }
        };

        abstract List<Object> request();

        abstract void verifyResponse(FullHttpResponse response);

        static FullHttpRequest multipart(String uri) {
            String boundary = "----MicronautBenchmarkBoundary";
            ByteBuf body = Unpooled.buffer();
            body.writeCharSequence("--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"title\"\r\n\r\n" +
                "hello\r\n" +
                "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"upload.bin\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n", StandardCharsets.US_ASCII);
            body.writeBytes(fileBytes());
            body.writeCharSequence("\r\n--" + boundary + "--\r\n", StandardCharsets.US_ASCII);
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri, body);
            request.headers().add(HttpHeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);
            request.headers().add(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes());
            request.headers().add(HttpHeaderNames.ACCEPT, MediaType.TEXT_PLAIN);
            return request;
        }

        static FullHttpRequest jsonList(String uri) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < LARGE_ITEMS; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"id\":").append(i)
                    .append(",\"name\":\"item-").append(i).append("\"")
                    .append(",\"enabled\":").append(i % 2 == 0)
                    .append(",\"score\":").append(i * 1.5)
                    .append('}');
            }
            sb.append(']');
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri,
                Unpooled.wrappedBuffer(sb.toString().getBytes(StandardCharsets.UTF_8)));
            request.headers().add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            request.headers().add(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());
            request.headers().add(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON);
            return request;
        }

        /**
         * Turn a full request into a header + chunked-transfer-encoded content sequence.
         */
        static List<Object> chunked(FullHttpRequest full) {
            List<Object> msgs = new ArrayList<>();
            HttpRequest head = new DefaultHttpRequest(full.protocolVersion(), full.method(), full.uri());
            head.headers().set(full.headers());
            head.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
            head.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            msgs.add(head);
            ByteBuf content = full.content();
            while (content.isReadable()) {
                int n = Math.min(CHUNK_SIZE, content.readableBytes());
                msgs.add(new DefaultHttpContent(content.readRetainedSlice(n)));
            }
            msgs.add(new DefaultLastHttpContent());
            full.release();
            return msgs;
        }

        static byte[] fileBytes() {
            byte[] bytes = new byte[FILE_SIZE];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) ('a' + (i % 26));
            }
            return bytes;
        }

        static int countOccurrences(String haystack, String needle) {
            int count = 0;
            int idx = 0;
            while ((idx = haystack.indexOf(needle, idx)) != -1) {
                count++;
                idx += needle.length();
            }
            return count;
        }

        static long sumIds() {
            return (long) LARGE_ITEMS * (LARGE_ITEMS - 1) / 2;
        }
    }

    @Introspected
    public record Item(int id, String name, boolean enabled, double score) {
    }

    @Introspected
    public record Summary(int count, long sum) {
    }

    @Introspected
    public record Person(String name, int age, String email, List<String> tags) {
    }

    @Controller("/scenarios")
    @Requires(property = "spec.name", value = "ServerScenariosBenchmark")
    static class ScenariosController {
        private static final byte[] LARGE_TEXT = Scenario.fileBytes();

        @Post(value = "/multipart/completed", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String multipartCompleted(@Part String title, @Part CompletedFileUpload file) throws IOException {
            try {
                return "title=" + title + ",file=" + file.getFilename() + "," + file.getBytes().length;
            } finally {
                file.close();
            }
        }

        @Post(value = "/multipart/bytes", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String multipartBytes(@Part String title, @Part byte[] file) {
            return "title=" + title + ",file=" + file.length;
        }

        @Post(value = "/form", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String form(@Body Person person) {
            return person.name() + "/" + person.age() + "/" + person.email() + "/" + person.tags().size();
        }

        @Post(value = "/json/list", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
        Summary jsonList(@Body List<Item> items) {
            long sum = 0;
            for (Item item : items) {
                sum += item.id();
            }
            return new Summary(items.size(), sum);
        }

        @Post(value = "/json/flux", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
        Mono<Summary> jsonFlux(@Body Flux<Item> items) {
            return items.reduce(new long[2], (acc, item) -> {
                acc[0]++;
                acc[1] += item.id();
                return acc;
            }).map(acc -> new Summary((int) acc[0], acc[1]));
        }

        @Get(value = "/json/stream", produces = MediaType.APPLICATION_JSON)
        Flux<Item> jsonStream() {
            return Flux.range(0, STREAM_ITEMS).map(i -> new Item(i, "item-" + i, i % 2 == 0, i * 1.5));
        }

        @Get(value = "/json/stream-ndjson", produces = MediaType.APPLICATION_JSON_STREAM)
        Flux<Item> jsonStreamNdjson() {
            return Flux.range(0, STREAM_ITEMS).map(i -> new Item(i, "item-" + i, i % 2 == 0, i * 1.5));
        }

        @Get(value = "/text/large", produces = MediaType.TEXT_PLAIN)
        byte[] largeText() {
            return LARGE_TEXT;
        }
    }
}
