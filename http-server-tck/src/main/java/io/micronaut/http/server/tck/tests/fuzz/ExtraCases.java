/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.tck.tests.fuzz;

import io.micronaut.http.server.tck.tests.fuzz.RawHttp.Req;
import io.micronaut.http.server.tck.tests.fuzz.RawHttp.Resp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import static io.micronaut.http.server.tck.tests.fuzz.RawHttp.describe;
import static io.micronaut.http.server.tck.tests.fuzz.HttpServerFuzzTest.utf8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Keep-alive pair matrix, AsyncRequestBody.copy() stress, mutate-only filters, multipart order and abort cases.
 */
@SuppressWarnings("checkstyle:InnerTypeLast")
final class ExtraCases {
    private final HttpServerFuzzTest t;

    ExtraCases(HttpServerFuzzTest t) {
        this.t = t;
    }

    record Step(String name, Req req, Predicate<Resp> ok) {
    }

    List<Step> steps() {
        byte[] small = utf8("payloadé");
        return List.of(
            new Step("get-plain", new Req("GET", "/r/plain"), r -> r.status() == 200 && r.text().equals("hello")),
            new Step("post-string", t.post("/f/string", "text/plain", small), r -> r.status() == 200 && r.text().equals(describe(small))),
            new Step("post-ignore-2MB", t.post("/f/ignore", "application/octet-stream", t.randomBytes(2_000_000)), r -> r.status() == 200 && r.text().equals("ignored")),
            new Step("post-unread-1MB", t.post("/fn/unread", "application/octet-stream", t.randomBytes(1_000_000)), r -> r.status() == 202),
            new Step("post-malformed-json", t.post("/f/pojo", "application/json", utf8("{\"na")), r -> r.status() == 400),
            new Step("get-err-sync", new Req("GET", "/r/err-sync"), r -> r.status() == 500),
            new Step("get-404", new Req("GET", "/does-not-exist"), r -> r.status() == 404),
            new Step("post-404-500KB", t.post("/does-not-exist", "text/plain", t.randomBytes(500_000)), r -> r.status() == 404),
            new Step("get-flux-stream", new Req("GET", "/r/flux-stream?n=3"), r -> r.status() == 200 && !r.truncated()),
            new Step("post-bodyless", t.post("/f/string", "text/plain", null), r -> r.status() == 400),
            new Step("post-is-chunked", t.post("/f/is", "application/octet-stream", small).chunked(5), r -> r.status() == 200 && r.text().equals(describe(small))),
            new Step("post-415", t.post("/fn/json", "text/plain", utf8("{}")), r -> r.status() == 415),
            new Step("post-large-form", t.post("/fn/form", "application/x-www-form-urlencoded", utf8("a=" + "x".repeat(300_000))), r -> r.status() == 200));
    }

    void keepAlivePairs() {
        List<String> names = steps().stream().map(Step::name).toList();
        for (int i = 0; i < names.size(); i++) {
            for (int j = 0; j < names.size(); j++) {
                // by default a third of the pairs, chosen by the seed so that other seeds cover the others
                if (!HttpServerFuzzTest.FULL && (i + j) % 3 != Math.floorMod(HttpServerFuzzTest.SEED, 3)) {
                    continue;
                }
                int a = i;
                int b = j;
                String name = "keepalive " + names.get(a) + " -> " + names.get(b);
                t.add(name, () -> {
                    try (RawHttp h = new RawHttp(t.port)) {
                        Step first = steps().get(a);
                        Resp r1 = h.send(first.req(), t.port);
                        assertTrue(first.ok().test(r1), name + " first: " + r1);
                        if (r1.eof() || "close".equalsIgnoreCase(r1.header("connection"))) {
                            t.results.put(name, "closed after first");
                            return; // announced close: legal
                        }
                        Step second = steps().get(b);
                        Resp r2;
                        try {
                            r2 = h.send(second.req(), t.port);
                        } catch (java.net.SocketException | java.net.SocketTimeoutException e) {
                            fail(name + ": the connection that was not announced to be closed failed for the next request: " + e + " (first: " + r1 + ")");
                            return;
                        }
                        if (r2.status() == -1) {
                            fail(name + ": the connection that was not announced to be closed was dropped without a response (first: " + r1 + ")");
                        }
                        assertTrue(second.ok().test(r2), name + " second: " + r2 + " (first: " + r1 + ")");
                    }
                });
            }
        }
    }

    void register() {
        keepAlivePairs();
        // the new 5.3 AsyncRequestBody.copy(): both views read concurrently, the original must be complete
        for (int size : HttpServerFuzzTest.FULL ? new int[]{5000, 300_000, 1_500_000} : new int[]{5000, 300_000}) {
            for (int chunk : new int[]{0, 1000, 8192}) {
                String name = "asyncbody copy concurrent size=" + size + " chunk=" + chunk;
                t.add(name, () -> {
                    Random r = new Random(HttpServerFuzzTest.SEED + size + chunk);
                    int bad = 0;
                    String first = "";
                    for (int i = 0; i < 25; i++) {
                        byte[] b = new byte[size];
                        r.nextBytes(b);
                        Req q = t.post("/fn/copy", "application/octet-stream", b);
                        if (chunk > 0) {
                            q.chunked(chunk);
                        }
                        Resp resp = t.call(q);
                        String exp = "copy=" + describe(b) + " orig=" + describe(b);
                        if (!exp.equals(resp.text())) {
                            bad++;
                            if (first.isEmpty()) {
                                first = resp.status() + " " + resp.text() + " expected " + exp;
                            }
                        }
                    }
                    t.results.put(name, "bad=" + bad + "/25 " + first);
                    assertEquals(0, bad, "the original body differs from what was sent in " + bad + "/25 requests, e.g. " + first);
                });
            }
        }
        t.add("asyncbody copy sequential 1.5MB chunked", () -> {
            byte[] b = t.randomBytes(HttpServerFuzzTest.LARGE);
            Resp resp = t.call(t.post("/fn/copy-sequential", "application/octet-stream", b).chunked(8192));
            HttpServerFuzzTest.assertOk("copy-sequential", resp, "copy=" + describe(b) + " orig=" + describe(b));
        });
        t.add("echo /f/future application/octet-stream", () -> {
            Resp resp = t.call(t.post("/f/future", "application/octet-stream", utf8("hello")));
            t.record("future octet-stream", resp);
            HttpServerFuzzTest.assertOk("future", resp, describe(utf8("hello")));
        });
        t.add("echo /f/future application/json", () ->
            HttpServerFuzzTest.assertOk("future json", t.call(t.post("/f/future", "application/json", utf8("hello"))), describe(utf8("hello"))));
        // request filters that mutate the request without reading the body
        for (String mode : List.of("replace", "clear", "wrap", "uri")) {
            for (String ep : List.of("/flt/opt", "/flt/req", "/flt/bytes", "/flt/form")) {
                for (String bk : List.of("small", "empty", "none", "large")) {
                    String name = "filter-mutate-only " + mode + " " + ep + " " + bk;
                    t.add(name, () -> {
                        boolean form = ep.equals("/flt/form");
                        String ct = form ? "application/x-www-form-urlencoded" : "text/plain";
                        byte[] body = switch (bk) {
                            case "small" -> utf8(form ? "a=1&b=2" : "hello");
                            case "large" -> utf8(form ? "a=" + "x".repeat(HttpServerFuzzTest.LARGE) : "h".repeat(HttpServerFuzzTest.LARGE));
                            case "empty" -> new byte[0];
                            default -> null;
                        };
                        Resp resp = t.call(t.post(ep, ct, body).header("x-mode2", mode));
                        t.record(name, resp);
                        assertFalse(resp.truncated(), resp.toString());
                        boolean hasBody = body != null && body.length > 0;
                        String effective = switch (mode) {
                            case "replace" -> "replacement";
                            case "clear" -> null;
                            default -> hasBody ? new String(body, StandardCharsets.UTF_8) : null;
                        };
                        if (effective == null) {
                            assertTrue(ep.equals("/flt/opt")
                                ? resp.status() == 200 && resp.text().equals("body null") || resp.status() == 400
                                : resp.status() == 400, name + ": " + resp);
                        } else if (form && mode.equals("replace")) {
                            assertTrue(resp.status() < 500, name + ": " + resp);
                        } else if (!form) {
                            assertEquals(200, resp.status(), name + ": " + resp);
                            assertEquals(ep.equals("/flt/bytes") ? "bytes " + describe(utf8(effective)) : "body " + effective, resp.text());
                        } else {
                            assertEquals(200, resp.status(), name + ": " + resp);
                        }
                    });
                }
            }
        }
        t.add("mp part order is the arrival order", () -> {
            String mp = "--bnd\r\nContent-Disposition: form-data; name=\"t1\"\r\n\r\nv1\r\n"
                + "--bnd\r\nContent-Disposition: form-data; name=\"f1\"; filename=\"a\"\r\nContent-Type: image/png\r\n\r\nAAA\r\n"
                + "--bnd\r\nContent-Disposition: form-data; name=\"t0\"\r\n\r\nv0\r\n"
                + "--bnd\r\nContent-Disposition: form-data; name=\"t1\"\r\n\r\nv1b\r\n"
                + "--bnd\r\nContent-Disposition: form-data; name=\"f0\"; filename=\"b\"\r\n\r\nBBB\r\n--bnd--\r\n";
            Resp resp = t.call(t.post("/fn/parts", "multipart/form-data; boundary=bnd", utf8(mp)));
            assertEquals(200, resp.status(), resp.toString());
            assertEquals(List.of("t1", "f1", "t0", "t1", "f0"), Arrays.stream(resp.text().split("\n")).map(l -> l.split("\\|")[0]).toList());
        });
        t.add("mp same-name files arrive in order (Publisher<CompletedFileUpload>)", () -> {
            String mp = "--bnd\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\nContent-Type: text/plain\r\n\r\nAAA\r\n"
                + "--bnd\r\nContent-Disposition: form-data; name=\"file\"; filename=\"b.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n" + "B".repeat(100000) + "\r\n"
                + "--bnd\r\nContent-Disposition: form-data; name=\"file\"; filename=\"c.txt\"\r\nContent-Type: text/plain\r\n\r\n\r\n--bnd--\r\n";
            for (int i = 0; i < 30; i++) {
                Resp resp = t.call(t.post("/f/mp-multi", "multipart/form-data; boundary=bnd", utf8(mp)));
                assertEquals(200, resp.status(), resp.toString());
                assertEquals(List.of("a.txt", "b.bin", "c.txt"), Arrays.stream(resp.text().split(";")).map(l -> l.split("\\|")[0]).toList(), "iteration " + i);
            }
        });
        t.add("mp boundary-like bytes inside a file", () -> {
            String b = "----fuzzBoundary7MA4YWxkTrZu0gW";
            byte[] data = utf8("a\r\n--" + b + "x\r\n\r\nb");
            Resp resp = t.call(t.post("/fn/parts", "multipart/form-data; boundary=" + b,
                HttpServerFuzzTest.multipart(b, List.of(new HttpServerFuzzTest.P("file", "f.txt", "text/plain", data)), true)));
            t.record("mp boundary-like", resp);
            // RFC 2046 5.1.1: the boundary must not occur in the content, so a parser may also end the part at the
            // boundary-like line (the content is then cut after "a"); it must not fail or hang
            assertEquals(200, resp.status(), resp.toString());
            String exact = "file|f.txt|text/plain|" + describe(data);
            String cut = "file|f.txt|text/plain|" + describe(utf8("a"));
            assertTrue(resp.text().equals(exact) || resp.text().equals(cut), resp.text());
        });
        t.add("mp unicode filename is decoded as UTF-8", () -> {
            Resp resp = t.call(t.post("/fn/parts", "multipart/form-data; boundary=bnd",
                utf8("--bnd\r\nContent-Disposition: form-data; name=\"file\"; filename=\"café €.txt\"\r\n\r\nx\r\n--bnd--\r\n")));
            assertEquals(200, resp.status(), resp.toString());
            assertEquals("file|café €.txt|-|" + describe(utf8("x")), resp.text());
        });
        // an aborted upload must not affect the next requests
        for (String ep : List.of("/f/is", "/f/bytes", "/fn/bytes", "/f/mp-basic", "/f/mp-streaming", "/fn/parts")) {
            t.add("transport next request after an aborted chunked upload " + ep, () -> {
                try (RawHttp h = new RawHttp(t.port)) {
                    boolean mp = ep.startsWith("/f/mp") || ep.equals("/fn/parts");
                    String head = "POST " + ep + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: " + (mp ? "multipart/form-data; boundary=zz" : "application/octet-stream")
                        + "\r\nTransfer-Encoding: chunked\r\n\r\n";
                    String chunk = mp ? "--zz\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a\"\r\n\r\nxxxxxxxxxx" : "0123456789abcdef";
                    h.out.write((head + Integer.toHexString(chunk.length()) + "\r\n" + chunk + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    h.out.flush();
                    Thread.sleep(300);
                }
                Thread.sleep(300);
                for (int i = 0; i < 3; i++) {
                    Resp r = t.call(new Req("GET", "/r/plain"));
                    HttpServerFuzzTest.assertOk("get after abort #" + i, r, "hello");
                }
            });
        }
    }
}
