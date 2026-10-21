package io.micronaut.http.server.stack;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Differential fuzzer: sends the same randomly mutated request, framed and fragmented at random, to a
 * functional route and to the equivalent controller of {@link RoutesAllocProfile}, and reports the
 * requests answered differently (status or body), the requests that hang, and the pooled buffers that
 * are still in use afterwards. Run with {@code -Dio.netty.leakDetection.level=paranoid}; options:
 * {@code -Dfuzz.seed=N -Dfuzz.iters=N}.
 */
public class RoutesFuzz {

    record Spec(String name, String fn, String ctl, String method, String type, String body) {
    }

    static final String B = "xBOUNDARYx";
    static final List<Spec> SPECS = List.of(
        new Spec("hello", "/f/hello", "/c/hello", "GET", null, null),
        new Spec("pathvar", "/f/var/7", "/c/var/7", "GET", null, null),
        new Spec("json", "/f/json", "/c/json", "POST", "application/json", "{\"id\":1,\"name\":\"fred\"}"),
        new Spec("json-async", "/f/json-async", "/c/json-async", "POST", "application/json", "{\"id\":1,\"name\":\"fred\"}"),
        new Spec("text-async", "/f/text-async", "/c/text-async", "POST", "text/plain", "hello"),
        new Spec("form", "/f/form", "/c/form", "POST", "application/x-www-form-urlencoded", "name=fred&age=42"),
        new Spec("multipart", "/f/multipart", "/c/multipart", "POST", "multipart/form-data; boundary=" + B,
            "--" + B + "\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\nfred\r\n--" + B + "--\r\n"),
        new Spec("sse", "/f/sse", "/c/sse", "GET", null, null),
        new Spec("filtered", "/f/filtered", "/c/filtered", "GET", null, null),
        new Spec("ctx", "/f/ctx", "/c/ctx", "GET", null, null));

    static final String[] TYPES = {null, "application/json", "text/plain", "application/x-www-form-urlencoded", "application/octet-stream", "multipart/form-data; boundary=" + B, "application/xml"};
    static final String[] BODIES = {"", "{", "{\"id\":\"x\"}", "null", "[]", "{\"id\":1,\"name\":\"a\"}", "name=&age=zz", "name=a&name=b&age=1", "\u00e9\u00e9", "--" + B + "--\r\n", "--" + B + "\r\nContent-Disposition: form-data; name=\"name\"\r\n\r\nz", "x".repeat(5000)};
    static final String[] SUFFIXES = {"", "", "", "/", "?x=1", "?", "?name=q&age=3", "//"};
    static final String[] ACCEPTS = {null, null, "*/*", "text/plain", "application/json", "text/event-stream", "image/png"};
    static final String[] METHODS = {null, null, null, null, "GET", "POST", "PUT", "HEAD", "DELETE", "OPTIONS"};

    record Req(String method, String path, String type, String accept, String body, boolean chunked) {
    }

    static byte[] encode(Req r, Random rnd) {
        StringBuilder head = new StringBuilder();
        head.append(r.method).append(' ').append(r.path).append(" HTTP/1.1\r\nHost: localhost\r\n");
        if (r.type != null) {
            head.append("Content-Type: ").append(r.type).append("\r\n");
        }
        if (r.accept != null) {
            head.append("Accept: ").append(r.accept).append("\r\n");
        }
        byte[] body = r.body == null ? new byte[0] : r.body.getBytes(StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            if (r.chunked) {
                head.append("Transfer-Encoding: chunked\r\n\r\n");
                out.write(head.toString().getBytes(StandardCharsets.UTF_8));
                int pos = 0;
                while (pos < body.length) {
                    int n = Math.min(body.length - pos, 1 + rnd.nextInt(Math.max(1, Math.min(64, body.length))));
                    out.write((Integer.toHexString(n) + "\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(body, pos, n);
                    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
                    pos += n;
                }
                out.write("0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            } else {
                if (body.length > 0 || !r.method.equals("GET") && !r.method.equals("HEAD")) {
                    head.append("Content-Length: ").append(body.length).append("\r\n");
                }
                head.append("\r\n");
                out.write(head.toString().getBytes(StandardCharsets.UTF_8));
                out.write(body);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    /** Status and body of the answer, or a marker if there was none or it could not be decoded. */
    static String exchange(EmbeddedChannel server, byte[] request, Random rnd, boolean head) {
        // fragment the bytes at random points, running the pending tasks in between at random
        int pos = 0;
        while (pos < request.length) {
            int n = Math.min(request.length - pos, 1 + (rnd.nextInt(3) == 0 ? rnd.nextInt(request.length) : rnd.nextInt(24)));
            try {
                server.writeInbound(Unpooled.wrappedBuffer(request, pos, n).retain());
            } catch (Exception e) {
                // answered and closed before the end of the request, e.g. a rejected body
                break;
            }
            pos += n;
            if (rnd.nextBoolean()) {
                server.runPendingTasks();
            }
        }
        EmbeddedChannel client = new EmbeddedChannel(new HttpClientCodec(4096, 8192, 8192, false, true), new HttpObjectAggregator(1 << 20));
        long deadline = System.nanoTime() + 700_000_000L;
        String result = null;
        StringBuilder raw = new StringBuilder();
        while (result == null) {
            server.runPendingTasks();
            Object m;
            while ((m = server.readOutbound()) != null) {
                if (m instanceof ByteBuf b) {
                    if (head) {
                        raw.append(b.toString(StandardCharsets.ISO_8859_1));
                        b.release();
                        continue;
                    }
                    client.writeInbound(b);
                } else {
                    io.netty.util.ReferenceCountUtil.release(m);
                }
            }
            FullHttpResponse response = client.readInbound();
            if (head && raw.indexOf("\r\n\r\n") > 0) {
                String lower = raw.toString().toLowerCase(java.util.Locale.ROOT);
                int ct = lower.indexOf("content-type:");
                result = raw.substring(9, 12) + " " + (ct < 0 ? "" : raw.substring(ct + 13, lower.indexOf("\r\n", ct)).trim().replaceAll(";.*", "")) + " []";
            } else if (response != null) {
                String type = response.headers().get("content-type");
                result = response.status().code() + " " + (type == null ? "" : type.replaceAll(";.*", "")) + " ["
                    + (head ? "" : response.content().toString(StandardCharsets.UTF_8).replace("\r", "\\r").replace("\n", "\\n")) + "]";
                response.release();
            } else if (System.nanoTime() > deadline) {
                result = "HANG";
            } else {
                Thread.onSpinWait();
            }
        }
        client.finishAndReleaseAll();
        // drain what the server still writes (an SSE stream ends after the response was complete)
        server.runPendingTasks();
        server.finishAndReleaseAll();
        return result;
    }

    static long inUse() {
        long n = 0;
        for (var a : PooledByteBufAllocator.DEFAULT.metric().directArenas()) {
            n += a.numActiveAllocations();
        }
        for (var a : PooledByteBufAllocator.DEFAULT.metric().heapArenas()) {
            n += a.numActiveAllocations();
        }
        return n;
    }

    public static void main(String[] args) throws Exception {
        long seed = Long.getLong("fuzz.seed", 1);
        int iters = Integer.getInteger("fuzz.iters", 20_000);
        Random rnd = new Random(seed);
        Map<String, Integer> diffsByCase = new LinkedHashMap<>();
        List<String> samples = new ArrayList<>();
        int hangs = 0;
        Map<String, Integer> hangsBy = new LinkedHashMap<>();
        List<String> hangSamples = new ArrayList<>();
        try (RoutesAllocProfile.Runner runner = new RoutesAllocProfile.Runner()) {
            // a channel for each request: the server closes the connection after some answers
            java.util.function.Supplier<EmbeddedChannel> channels = () -> ((NettyHttpServer) runner.ctx.getBean(EmbeddedServer.class)).buildEmbeddedChannel(false);
            // warm up the lazy state, so that the buffers kept for the connection are not counted
            for (int i = 0; i < 50; i++) {
                exchange(channels.get(), encode(new Req("GET", "/f/hello", null, null, null, false), rnd), rnd, false);
            }
            long baseline = inUse();
            for (int i = 0; i < iters; i++) {
                Spec spec = SPECS.get(rnd.nextInt(SPECS.size()));
                String method = METHODS[rnd.nextInt(METHODS.length)];
                method = method == null ? spec.method : method;
                String type = rnd.nextInt(3) == 0 ? TYPES[rnd.nextInt(TYPES.length)] : spec.type;
                String body = rnd.nextInt(3) == 0 ? BODIES[rnd.nextInt(BODIES.length)] : spec.body;
                String suffix = SUFFIXES[rnd.nextInt(SUFFIXES.length)];
                if ((spec.name.equals("ctx") || spec.name.equals("filtered")) && (suffix.startsWith("/"))) {
                    // the filter of the controller matches the exact path, the filter of the route its route
                    suffix = "";
                }
                String accept = ACCEPTS[rnd.nextInt(ACCEPTS.length)];
                boolean chunked = body != null && !body.isEmpty() && rnd.nextBoolean();
                boolean hasBody = body != null && !method.equals("GET") && !method.equals("HEAD") || chunked;
                if (!hasBody) {
                    body = null;
                    chunked = false;
                }
                long frameSeed = rnd.nextLong();
                String fn;
                String ctl;
                boolean head = method.equals("HEAD");
                fn = exchange(channels.get(), encode(new Req(method, spec.fn + suffix, type, accept, body, chunked), new Random(frameSeed)), new Random(frameSeed), head);
                ctl = exchange(channels.get(), encode(new Req(method, spec.ctl + suffix, type, accept, body, chunked), new Random(frameSeed)), new Random(frameSeed), head);
                if (fn.equals("HANG") || ctl.equals("HANG")) {
                    hangs++;
                    hangsBy.merge(spec.name + (fn.equals("HANG") ? " fn" : "") + (ctl.equals("HANG") ? " ctl" : ""), 1, Integer::sum);
                    if (hangSamples.size() < 12) {
                        hangSamples.add(String.format("%-10s %s %s%s type=%s accept=%s chunked=%s body=%s  -> fn=%s ctl=%s", spec.name, method, spec.fn, suffix, type, accept, chunked,
                            body == null ? null : body.length() > 30 ? body.substring(0, 30) + "..." : body.replace("\r\n", "|"), fn.equals("HANG") ? "HANG" : "ok", ctl.equals("HANG") ? "HANG" : "ok"));
                    }
                }
                // the controller names its arguments, the route its body, and the paths differ by their prefix
                fn = fn.replace("/f/", "/X/").replace("[body]", "[ARG]");
                ctl = ctl.replace("/c/", "/X/").replace("[item]", "[ARG]").replace("[t]", "[ARG]");
                boolean statusOnly = Boolean.getBoolean("fuzz.statusOnly");
                if (statusOnly ? !fn.substring(0, 3).equals(ctl.substring(0, 3)) || fn.startsWith("200") && !fn.equals(ctl) : !fn.equals(ctl)) {
                    diffsByCase.merge(spec.name, 1, Integer::sum);
                    if (samples.size() < Integer.getInteger("fuzz.samples", 40)) {
                        samples.add(String.format("%-10s %s %s%s type=%s accept=%s chunked=%s body=%s%n    fn : %s%n    ctl: %s",
                            spec.name, method, spec.fn, suffix, type, accept, chunked, body == null ? null : body.length() > 40 ? body.substring(0, 40) + "..." : body, fn, ctl));
                    }
                }
            }
            long after = inUse();
            System.out.printf("seed=%d iters=%d differing=%s hangs=%d pooledInUse baseline=%d after=%d%n", seed, iters, diffsByCase, hangs, baseline, after);
            System.out.println("hangs by case: " + hangsBy);
            hangSamples.forEach(h -> System.out.println("HANG " + h));
            for (String s : samples) {
                System.out.println(s);
            }
        }
        System.gc();
        Thread.sleep(500);
        System.exit(0);
    }
}
