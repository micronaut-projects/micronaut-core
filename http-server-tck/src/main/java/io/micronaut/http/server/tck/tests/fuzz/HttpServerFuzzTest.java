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

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.http.server.tck.tests.fuzz.RawHttp.Req;
import io.micronaut.http.server.tck.tests.fuzz.RawHttp.Resp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.http.server.tck.tests.fuzz.FuzzApp.SPEC;
import static io.micronaut.http.server.tck.tests.fuzz.RawHttp.describe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seeded property-style HTTP fuzz suite of the server TCK, run against every server that runs the TCK. The seed is
 * logged and can be overridden with {@code -Dfuzz.seed=N}; every combination is named so that a failure is
 * reproducible. {@code -Dfuzz.only=<regex>} runs only the cases whose name matches, {@code -Dfuzz.runKnown=true}
 * also runs the known failures, and {@code -Dfuzz.full=true} adds the slow cases (every keep-alive pair instead of a
 * third chosen by the seed, the largest async body copies, a slow reader of a 20MB response).
 *
 * <p>Known failures are declared by the server suite, not here: every classpath resource
 * {@value #KNOWN_FAILURES_RESOURCE} (and the file named by {@code -Dfuzz.knownFailures}) is read, one entry per line
 * as {@code key => reason}. The key is a regex matched against the start of the case name, optionally prefixed by
 * {@code engine[,engine]:} where an engine is a lower case fragment of the class name of the embedded server;
 * several alternatives can be joined with {@code |}. Lines starting with {@code #} are comments.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("fuzz")
@SuppressWarnings({"checkstyle:InnerTypeLast", "checkstyle:DeclarationOrder"})
public final class HttpServerFuzzTest {
    /** The resource listing the known failures of a server suite. */
    public static final String KNOWN_FAILURES_RESOURCE = "META-INF/micronaut-tck-fuzz-known-failures.txt";
    static final long SEED = Long.getLong("fuzz.seed", 20260510L);
    static final int LARGE = 1_500_000;
    /** Runs the slow cases too (every keep-alive pair, the largest bodies, a slow reader of 20MB). */
    static final boolean FULL = Boolean.getBoolean("fuzz.full");

    /** key ("[engine[,engine]:]regex" alternatives) to reason. */
    static final Map<String, String> KNOWN_FAILURES = loadKnownFailures();

    ServerUnderTest server;
    int port;
    String engine;
    final Map<String, String> results = new ConcurrentSkipListMap<>();
    final List<DynamicTest> tests = new ArrayList<>();
    Random rnd;

    @BeforeAll
    void start() {
        System.out.println("FUZZ seed=" + SEED);
        rnd = new Random(SEED);
        server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC, Map.of(
            "micronaut.server.max-request-size", "16MB",
            "micronaut.server.max-request-buffer-size", "4MB",
            "micronaut.server.multipart.max-file-size", "16MB"));
        ApplicationContext ctx = server.getApplicationContext();
        port = server.getPort().orElseGet(() -> ctx.getBean(EmbeddedServer.class).getPort());
        EmbeddedServer embedded = ctx.getBean(EmbeddedServer.class);
        engine = embedded.getClass().getName().toLowerCase(Locale.ROOT);
        if ("https".equalsIgnoreCase(embedded.getScheme())) {
            RawHttp.useTls();
        } else {
            RawHttp.tls = null;
        }
        System.out.println("FUZZ engine=" + engine + " port=" + port);
    }

    @AfterAll
    void stop() throws IOException {
        server.close();
        Path out = Path.of("build", "fuzz-results.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, results.entrySet().stream().map(e -> e.getKey() + " => " + e.getValue()).collect(Collectors.joining("\n", "", "\n")));
        System.out.println("FUZZ combinations=" + results.size());
    }

    // ------------------------------------------------------------------ plumbing

    private static final Pattern ENGINE_PREFIX = Pattern.compile("([a-z0-9,]+):");

    static Map<String, String> loadKnownFailures() {
        Map<String, String> known = new LinkedHashMap<>();
        try {
            Enumeration<URL> urls = HttpServerFuzzTest.class.getClassLoader().getResources(KNOWN_FAILURES_RESOURCE);
            while (urls.hasMoreElements()) {
                try (InputStream in = urls.nextElement().openStream()) {
                    parseKnownFailures(new String(in.readAllBytes(), StandardCharsets.UTF_8), known);
                }
            }
            String file = System.getProperty("fuzz.knownFailures");
            if (file != null && !file.isBlank()) {
                parseKnownFailures(Files.readString(Path.of(file)), known);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return known;
    }

    static void parseKnownFailures(String text, Map<String, String> into) {
        for (String line : text.split("\\R")) {
            String l = line.strip();
            int sep = l.indexOf("=>");
            if (l.isEmpty() || l.startsWith("#") || sep < 0) {
                continue;
            }
            into.put(l.substring(0, sep).strip(), l.substring(sep + 2).strip());
        }
    }

    void add(String name, Executable body) {
        String full = name;
        String only = System.getProperty("fuzz.only");
        if (only != null && !Pattern.compile(only).matcher(full).find()) {
            return;
        }
        boolean runKnown = Boolean.getBoolean("fuzz.runKnown");
        tests.add(DynamicTest.dynamicTest(full, () -> {
            for (var e : runKnown ? java.util.Set.<java.util.Map.Entry<String, String>>of() : KNOWN_FAILURES.entrySet()) {
                for (String alt : e.getKey().split("\\|(?=[a-z0-9,]+:)")) {
                    Matcher m = ENGINE_PREFIX.matcher(alt);
                    boolean engineMatches = !m.lookingAt() || Arrays.stream(m.group(1).split(",")).anyMatch(engine::contains);
                    String regex = m.lookingAt() ? alt.substring(m.end()) : alt;
                    if (engineMatches && Pattern.compile(regex).matcher(full).lookingAt()) {
                        results.put(full, "SKIPPED known failure: " + e.getValue());
                        org.junit.jupiter.api.Assumptions.abort("known failure: " + e.getValue());
                    }
                }
            }
            body.execute();
        }));
    }

    void addFull(String name, Executable body) {
        if (FULL) {
            add(name, body);
        }
    }

    Resp call(Req r) throws IOException {
        try (RawHttp h = new RawHttp(port)) {
            return h.send(r, port, false);
        }
    }

    Req post(String path, String ct, byte[] body) {
        Req r = new Req("POST", path);
        if (ct != null) {
            r.contentType(ct);
        }
        return r.body(body);
    }

    void record(String name, Resp r) {
        String b = r.text();
        results.put(name, r.status() + " " + (r.truncated() ? "TRUNC " : "") + (b.length() > 120 ? describe(r.body()) : b.replace("\n", "\\n")));
    }

    static void assertOk(String name, Resp r, String body) {
        assertEquals(200, r.status(), name + ": " + r);
        assertEquals(body, r.text(), name);
        assertFalse(r.truncated(), name + " truncated");
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    String randomText(int chars) {
        String alphabet = "abcXYZ 0189éñü€中文😀-_.~!*'();:@&=+$,/?#[]%\n\r\t";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chars; i++) {
            int cp = alphabet.codePointAt(alphabet.offsetByCodePoints(0, rnd.nextInt(alphabet.codePointCount(0, alphabet.length()))));
            sb.appendCodePoint(cp);
        }
        return sb.toString();
    }

    byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        rnd.nextBytes(b);
        return b;
    }

    int[] randomChunks() {
        int[] c = new int[8];
        for (int i = 0; i < c.length; i++) {
            c[i] = 1 + rnd.nextInt(i % 3 == 0 ? 7 : 40000);
        }
        return c;
    }

    // ------------------------------------------------------------------ the factory

    @TestFactory
    List<DynamicTest> fuzz() {
        echoMatrix();
        jsonMatrix();
        formMatrix();
        multipartMatrix();
        filterMatrix();
        responseMatrix();
        transportMatrix();
        new ExtraCases(this).register();
        return tests;
    }

    // ------------------------------------------------------------------ request body echo

    record BodyKind(String name, byte[] bytes, String ct, boolean none, int[] chunks, String decoded) {
        boolean empty() {
            return bytes == null || bytes.length == 0;
        }
    }

    List<BodyKind> bodyKinds() {
        List<BodyKind> l = new ArrayList<>();
        String small = "hello world";
        l.add(new BodyKind("none", null, "text/plain", true, null, ""));
        l.add(new BodyKind("empty-cl0", new byte[0], "text/plain", false, null, ""));
        l.add(new BodyKind("empty-chunked", new byte[0], "text/plain", false, new int[]{1}, ""));
        l.add(new BodyKind("small", utf8(small), "text/plain", false, null, small));
        String uni = "héllo wörld € 😀 中文";
        l.add(new BodyKind("unicode-utf8", utf8(uni), "text/plain;charset=UTF-8", false, null, uni));
        String latin = "café ñ ü ß";
        l.add(new BodyKind("latin1", latin.getBytes(StandardCharsets.ISO_8859_1), "text/plain;charset=ISO-8859-1", false, null, latin));
        l.add(new BodyKind("small-chunked-random", utf8(uni.repeat(50)), "text/plain;charset=UTF-8", false, randomChunks(), uni.repeat(50)));
        String large = (uni + randomText(10)).repeat(LARGE / 40);
        l.add(new BodyKind("large-cl", utf8(large), "text/plain;charset=UTF-8", false, null, large));
        l.add(new BodyKind("large-chunked", utf8(large), "text/plain;charset=UTF-8", false, randomChunks(), large));
        String huge = ("x" + uni).repeat(100_000);
        l.add(new BodyKind("3MB-chunked-4k", utf8(huge), "text/plain;charset=UTF-8", false, new int[]{4096}, huge));
        String over = ("x" + uni).repeat(160_000);
        l.add(new BodyKind("4.8MB-chunked-over-buffer-limit", utf8(over), "text/plain;charset=UTF-8", false, new int[]{8192}, over));
        return l;
    }

    void echoMatrix() {
        record Ep(String path, boolean stringLike, boolean nullable, boolean bytesCt) {
        }
        List<Ep> eps = List.of(
            new Ep("/f/string", true, false, false), new Ep("/f/string-opt", true, true, false),
            new Ep("/f/bytes", false, false, true), new Ep("/f/is", false, false, true),
            new Ep("/f/pub", false, false, true), new Ep("/f/mono", false, false, true), new Ep("/f/future", false, false, true),
            new Ep("/fn/text", true, false, false), new Ep("/fn/bytes", false, false, true), new Ep("/fn/transfer", false, false, true),
            new Ep("/fn/copy", false, false, true));
        for (Ep ep : eps) {
            for (BodyKind bk : bodyKinds()) {
                String name = "echo " + ep.path + " " + bk.name;
                add(name, () -> {
                    Req r = new Req("POST", ep.path);
                    r.contentType(bk.ct());
                    if (!bk.none()) {
                        r.body(bk.bytes());
                        if (bk.chunks() != null) {
                            r.chunked(bk.chunks());
                        }
                    }
                    Resp resp = call(r);
                    record(name, resp);
                    assertFalse(resp.truncated(), name + " " + resp);
                    byte[] raw = bk.bytes() == null ? new byte[0] : bk.bytes();
                    String expected;
                    if (ep.path.equals("/fn/copy")) {
                        expected = "copy=" + describe(raw) + " orig=" + describe(raw);
                    } else if (ep.stringLike) {
                        expected = describe(utf8(bk.decoded()));
                    } else {
                        expected = describe(raw);
                    }
                    if (bk.empty()) {
                        // missing/empty body: a required argument is a 400, a nullable one gets null; an empty
                        // stream/bytes is acceptable too. Never a 5xx, a hang or garbage.
                        boolean okEmpty = resp.status() == 200 && (resp.text().equals(expected) || resp.text().equals("null") || resp.text().equals("empty")
                            || resp.text().startsWith("copy=0:") || resp.text().startsWith("0:"));
                        assertTrue(resp.status() == 400 || okEmpty, name + ": " + resp);
                        return;
                    }
                    if (ep.path.equals("/fn/bytes-small")) {
                        return;
                    }
                    // buffered String body over the 4MB buffer limit -> 413
                    boolean buffered = Stream.of("/f/string", "/f/string-opt", "/fn/text", "/fn/bytes", "/fn/copy").anyMatch(ep.path::equals) || ep.path.equals("/f/bytes");
                    if (buffered && raw.length > 4 * 1024 * 1024) {
                        assertEquals(413, resp.status(), name + ": " + resp);
                        return;
                    }
                    if (raw.length > 4 * 1024 * 1024 && Stream.of("/f/pub", "/f/mono", "/f/future").anyMatch(ep.path::equals) && resp.status() == 413) {
                        return; // reactive body types may be buffered, subject to the buffer limit
                    }
                    assertOk(name, resp, expected);
                });
            }
        }
    }

    // ------------------------------------------------------------------ json

    void jsonMatrix() {
        String json = "application/json";
        record J(String name, String body, int[] chunks, int status, String expect) {
        }
        List<J> js = List.of(
            new J("valid", "{\"name\":\"Fred\",\"age\":45}", null, 200, "{\"name\":\"Fred\",\"age\":45}"),
            new J("valid-chunked", "{\"name\":\"Fred\",\"age\":45}", new int[]{1, 2, 3}, 200, "{\"name\":\"Fred\",\"age\":45}"),
            new J("unicode", "{\"name\":\"héllo € 😀\",\"age\":1}", null, 200, "{\"name\":\"héllo € 😀\",\"age\":1}"),
            new J("unicode-chunked1", "{\"name\":\"héllo € 😀\",\"age\":1}", new int[]{1}, 200, "{\"name\":\"héllo € 😀\",\"age\":1}"),
            new J("malformed-open", "{\"", null, 400, null),
            new J("malformed-trunc", "{\"name\":", null, 400, null),
            new J("malformed-garbage", "not json", null, 400, null),
            new J("malformed-array", "[]", null, 400, null),
            new J("empty", "", null, 400, null),
            new J("none", null, null, 400, null));
        for (String ep : List.of("/f/pojo", "/fn/json")) {
            for (J j : js) {
                String name = "json " + ep + " " + j.name();
                add(name, () -> {
                    Req r = post(ep, json, j.body() == null ? null : utf8(j.body()));
                    if (j.chunks() != null) {
                        r.chunked(j.chunks());
                    }
                    Resp resp = call(r);
                    record(name, resp);
                    assertEquals(j.status(), resp.status(), name + ": " + resp);
                    if (j.expect() != null) {
                        assertEquals(j.expect(), resp.text(), name);
                    }
                });
            }
        }
        add("json /f/map valid", () -> {
            Resp r = call(post("/f/map", json, utf8("{\"a\":1,\"b\":[1,2],\"c\":{\"d\":\"é\"}}")));
            assertOk("map", r, "{\"a\":1,\"b\":[1,2],\"c\":{\"d\":\"é\"}}");
        });
        add("json /f/json-args", () -> {
            Resp r = call(post("/f/json-args", json, utf8("{\"name\":\"Fred\",\"age\":45}")));
            record("json /f/json-args", r);
            assertOk("json-args", r, "Fred:45");
        });
        add("json /f/pojo wrong media type -> 415 or 400", () -> {
            Resp r = call(post("/f/pojo", "text/plain", utf8("{}")));
            record("json pojo text/plain", r);
            assertTrue(r.status() == 415 || r.status() == 400, r.toString());
        });
        add("json /fn/json wrong media type -> 415", () -> {
            Resp r = call(post("/fn/json", "text/plain", utf8("{}")));
            assertEquals(415, r.status(), r.toString());
        });
        add("json /fn/elements array", () -> assertOk("elements", call(post("/fn/elements", json, utf8("[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"}]"))), "a,b,c"));
        add("json /fn/elements array chunked1", () -> assertOk("elements", call(post("/fn/elements", json, utf8("[{\"name\":\"a\"},{\"name\":\"b\"},{\"name\":\"c\"}]")).chunked(1)), "a,b,c"));
        add("json /fn/elements stream", () -> assertOk("elements", call(post("/fn/elements", "application/x-json-stream", utf8("{\"name\":\"a\"}\n{\"name\":\"b\"}\n{\"name\":\"c\"}\n")).chunked(3)), "a,b,c"));
        add("json /fn/elements 5000", () -> {
            StringBuilder sb = new StringBuilder("[");
            List<String> names = new ArrayList<>();
            for (int i = 0; i < 5000; i++) {
                sb.append(i > 0 ? "," : "").append("{\"name\":\"n").append(i).append("\"}");
                names.add("n" + i);
            }
            Resp r = call(post("/fn/elements", json, utf8(sb.append("]").toString())).chunked(randomChunks()));
            assertOk("elements5000", r, String.join(",", names));
        });
        add("json /fn/elements text/plain -> 415", () -> assertEquals(415, call(post("/fn/elements", "text/plain", utf8("a"))).status()));
    }

    // ------------------------------------------------------------------ url-encoded forms

    static String enc(String s, boolean lowerHex, boolean plusSpace) {
        String e = URLEncoder.encode(s, StandardCharsets.UTF_8);
        if (!plusSpace) {
            e = e.replace("+", "%20");
        }
        if (lowerHex) {
            Matcher m = Pattern.compile("%[0-9A-F]{2}").matcher(e);
            e = m.replaceAll(mr -> mr.group().toLowerCase(Locale.ROOT));
        }
        return e;
    }

    void formMatrix() {
        String ct = "application/x-www-form-urlencoded";
        String[] keys = {"a", "b", "c", "name", "x y", "kéy", "z"};
        for (int i0 = 0; i0 < 40; i0++) {
            final int n = i0;
            long s = SEED + n;
            add("form random#" + n + " seed=" + s, () -> {
                Random r = new Random(s);
                Map<String, List<String>> model = new LinkedHashMap<>();
                List<String[]> order = new ArrayList<>();
                int fields = 1 + r.nextInt(8);
                for (int i = 0; i < fields; i++) {
                    String k = keys[r.nextInt(keys.length)];
                    String alphabet = "abc XYZ09&=+%;é€😀/?#\n";
                    StringBuilder v = new StringBuilder();
                    int len = 1 + r.nextInt(r.nextInt(5) == 0 ? 3000 : 12);
                    for (int j = 0; j < len; j++) {
                        v.append(alphabet.charAt(r.nextInt(alphabet.length())));
                    }
                    String sv = v.toString();
                    if (Character.isHighSurrogate(sv.charAt(sv.length() - 1))) {
                        sv = sv + "\ude00";
                    }
                    sv = sv.replaceAll("(?<![\ud800-\udbff])[\udc00-\udfff]", "?").replaceAll("[\ud800-\udbff](?![\udc00-\udfff])", "?");
                    model.computeIfAbsent(k, x -> new ArrayList<>()).add(sv);
                    order.add(new String[]{k, sv});
                }
                boolean lower = r.nextBoolean();
                boolean plus = r.nextBoolean();
                String body = order.stream().map(kv -> enc(kv[0], lower, plus) + "=" + enc(kv[1], lower, plus)).collect(Collectors.joining("&"));
                int[] chunks = r.nextInt(3) == 0 ? new int[]{1 + r.nextInt(9), 1 + r.nextInt(100)} : null;
                StringBuilder exp = new StringBuilder();
                new TreeMap<>(model).forEach((k, v) -> exp.append(k).append('=').append(v).append(';'));
                Req req = post("/fn/form", ct, utf8(body));
                if (chunks != null) {
                    req.chunked(chunks);
                }
                Resp resp = call(req);
                record("form random#" + n + " fn", resp);
                assertOk("fn/form " + body, resp, exp.toString());
                // controller Map<String,Object>
                Req req2 = post("/f/form-map", ct, utf8(body));
                if (chunks != null) {
                    req2.chunked(chunks);
                }
                Resp resp2 = call(req2);
                record("form random#" + n + " map", resp2);
                Map<String, Object> m = new TreeMap<>();
                model.forEach((k, v) -> m.put(k, v.size() == 1 ? v.get(0) : v));
                assertOk("f/form-map " + body, resp2, m.toString());
            });
        }
        add("form args a/b/c", () -> {
            Resp r = call(post("/f/form-args", ct, utf8("a=1&b=x&b=y%20z&c=%C3%A9")));
            record("form args", r);
            assertOk("form-args", r, "a=1;b=[x, y z];c=é");
        });
        add("form args missing a -> 400", () -> {
            Resp r = call(post("/f/form-args", ct, utf8("b=1")));
            record("form args missing", r);
            assertEquals(400, r.status(), r.toString());
        });
        add("form args single b is a list", () -> {
            Resp r = call(post("/f/form-args", ct, utf8("a=1&b=x")));
            record("form args single b", r);
            assertOk("form-args single", r, "a=1;b=[x];c=null");
        });
        add("form pojo", () -> assertOk("pojo", call(post("/f/form-pojo", ct, utf8("name=F%C3%A9d&age=3"))), "Féd:3"));
        for (String ep : List.of("/fn/form", "/f/form-map")) {
            add("form empty values " + ep, () -> {
                Resp r = call(post(ep, ct, utf8("a=&b=&a=x&c")));
                record("form empty values " + ep, r);
                assertEquals(200, r.status(), r.toString());
            });
            add("form none/empty body " + ep, () -> {
                Resp r = call(post(ep, ct, null));
                record("form none " + ep, r);
                Resp r2 = call(post(ep, ct, new byte[0]));
                record("form empty " + ep, r2);
                assertTrue(r.status() < 500 && r2.status() < 500, r + " / " + r2);
            });
            add("form large " + ep, () -> {
                String v = "vé".repeat(LARGE / 3);
                Resp r = call(post(ep, ct, utf8("a=" + enc(v, false, true) + "&b=1")).chunked(randomChunks()));
                record("form large " + ep, r);
                assertEquals(200, r.status(), describe(r.body()) + r.headers());
                if (ep.equals("/fn/form")) {
                    assertEquals("a=[" + v + "];b=[1];", r.text());
                }
            });
        }
    }

    // ------------------------------------------------------------------ multipart

    record P(String name, String filename, String ct, byte[] data) {
    }

    static byte[] multipart(String boundary, List<P> parts, boolean terminate) {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        for (P p : parts) {
            o.writeBytes(("--" + boundary + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            String cd = "Content-Disposition: form-data; name=\"" + p.name() + "\"" + (p.filename() != null ? "; filename=\"" + p.filename() + "\"" : "") + "\r\n";
            o.writeBytes(cd.getBytes(StandardCharsets.UTF_8));
            if (p.ct() != null) {
                o.writeBytes(("Content-Type: " + p.ct() + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            }
            o.writeBytes("\r\n".getBytes(StandardCharsets.ISO_8859_1));
            o.writeBytes(p.data());
            o.writeBytes("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        if (terminate) {
            o.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        return o.toByteArray();
    }

    void multipartMatrix() {
        String b = "----fuzzBoundary7MA4YWxkTrZu0gW";
        String mct = "multipart/form-data; boundary=" + b;
        // controller: text + file
        record FileCase(String name, byte[] data, String filename, String ct, int[] chunks) {
        }
        List<FileCase> fcs = List.of(
            new FileCase("small", utf8("hello file"), "a.txt", "text/plain", null),
            new FileCase("empty-content", new byte[0], "empty.bin", "application/octet-stream", null),
            new FileCase("binary-all-bytes", allBytes(), "all.bin", "application/octet-stream", new int[]{1, 7, 100}),
            new FileCase("crlf-in-content", utf8("a\r\n\r\nb\r\n--\r\n-"), "crlf.txt", "text/plain", null),
            new FileCase("large-1_5MB", randomBytes(LARGE), "large.bin", "application/octet-stream", null),
            new FileCase("large-chunked", randomBytes(3_000_000), "large2.bin", "application/octet-stream", new int[]{8192}),
            new FileCase("unicode-filename", utf8("x"), "café €.txt", "text/plain; charset=UTF-8", null),
            new FileCase("no-part-content-type", utf8("no ct"), "noct.txt", null, null));
        for (FileCase fc : fcs) {
            String name = "mp /f/mp-basic " + fc.name();
            add(name, () -> {
                byte[] body = multipart(b, List.of(new P("text", null, null, utf8("téxt")), new P("file", fc.filename(), fc.ct(), fc.data())), true);
                Req r = post("/f/mp-basic", mct, body);
                if (fc.chunks() != null) {
                    r.chunked(fc.chunks());
                }
                Resp resp = call(r);
                record(name, resp);
                assertEquals(200, resp.status(), name + resp);
                String text = resp.text();
                assertTrue(text.startsWith("téxt|" + fc.filename() + "|"), name + ": " + text);
                assertTrue(text.endsWith("|" + describe(fc.data())), name + ": " + text + " expected " + describe(fc.data()));
            });
            String n2 = "mp /fn/parts " + fc.name();
            add(n2, () -> {
                byte[] body = multipart(b, List.of(new P("text", null, null, utf8("téxt")), new P("file", fc.filename(), fc.ct(), fc.data())), true);
                Req r = post("/fn/parts", mct, body);
                if (fc.chunks() != null) {
                    r.chunked(fc.chunks());
                }
                Resp resp = call(r);
                record(n2, resp);
                assertEquals(200, resp.status(), n2 + resp);
                String[] lines = resp.text().split("\n");
                assertEquals(2, lines.length, n2 + ": " + resp);
                assertTrue(lines[0].startsWith("text|null|"), n2 + ": " + lines[0]);
                assertTrue(lines[0].endsWith("|" + describe(utf8("téxt"))), n2 + ": " + lines[0]);
                assertEquals("file|" + fc.filename() + "|" + (fc.ct() == null ? "-" : fc.ct().replace("; charset=UTF-8", ";charset=UTF-8")) + "|" + describe(fc.data()),
                    lines[1].replace("; charset=UTF-8", ";charset=UTF-8"), n2);
            });
            String n3 = "mp /fn/formdata-files " + fc.name();
            add(n3, () -> {
                byte[] body = multipart(b, List.of(new P("text", null, null, utf8("téxt")), new P("file", fc.filename(), fc.ct(), fc.data())), true);
                Req r = post("/fn/formdata-files?f=file", mct, body);
                if (fc.chunks() != null) {
                    r.chunked(fc.chunks());
                }
                Resp resp = call(r);
                record(n3, resp);
                assertEquals(200, resp.status(), n3 + resp);
                assertTrue(resp.text().contains("file|" + fc.filename() + "|"), n3 + ": " + resp.text());
                assertTrue(resp.text().contains("|" + describe(fc.data()) + ";"), n3 + ": " + resp.text() + " expected " + describe(fc.data()));
                assertTrue(resp.text().contains("text=[téxt];"), n3 + ": " + resp.text());
            });
            String n4 = "mp /f/mp-streaming " + fc.name();
            add(n4, () -> {
                byte[] body = multipart(b, List.of(new P("file", fc.filename(), fc.ct(), fc.data())), true);
                Req r = post("/f/mp-streaming", mct, body);
                if (fc.chunks() != null) {
                    r.chunked(fc.chunks());
                }
                Resp resp = call(r);
                record(n4, resp);
                assertEquals(200, resp.status(), n4 + resp);
                assertEquals(fc.filename() + "|" + fc.data().length, resp.text(), n4);
            });
            String n5 = "mp /f/mp-partdata " + fc.name();
            add(n5, () -> {
                byte[] body = multipart(b, List.of(new P("file", fc.filename(), fc.ct(), fc.data())), true);
                Resp resp = call(post("/f/mp-partdata", mct, body));
                record(n5, resp);
                assertEquals(200, resp.status(), n5 + resp);
                assertEquals("pd|" + fc.data().length, resp.text(), n5);
            });
        }
        add("mp multiple files same name /f/mp-multi", () -> {
            List<P> ps = List.of(new P("file", "a.txt", "text/plain", utf8("AAA")), new P("file", "b.bin", "application/octet-stream", randomBytes(100000)), new P("file", "c.txt", "text/plain", new byte[0]));
            Resp r = call(post("/f/mp-multi", mct, multipart(b, ps, true)));
            record("mp multi", r);
            assertEquals(200, r.status(), r.toString());
            String exp = ps.stream().map(p -> p.filename() + "|" + p.ct() + "|" + describe(p.data())).collect(Collectors.joining(";"));
            assertEquals(exp, r.text());
        });
        add("mp multiple files same name /fn/formdata-files", () -> {
            List<P> ps = List.of(new P("file", "a.txt", "text/plain", utf8("AAA")), new P("file", "b.bin", "application/octet-stream", randomBytes(100000)), new P("file", "c.txt", "text/plain", new byte[0]));
            Resp r = call(post("/fn/formdata-files?f=file", mct, multipart(b, ps, true)));
            record("mp formdata multi", r);
            assertEquals(200, r.status(), r.toString());
            for (P p : ps) {
                assertTrue(r.text().contains("file|" + p.filename() + "|" + p.ct() + "|" + describe(p.data()) + ";"), r.text());
            }
        });
        add("mp empty filename /f/mp-basic", () -> {
            Resp r = call(post("/f/mp-basic", mct, multipart(b, List.of(new P("text", null, null, utf8("t")), new P("file", "", "application/octet-stream", new byte[0])), true)));
            record("mp empty filename", r);
            assertTrue(r.status() < 500, r.toString());
        });
        add("mp empty filename /fn/parts", () -> {
            Resp r = call(post("/fn/parts", mct, multipart(b, List.of(new P("file", "", "application/octet-stream", utf8("data"))), true)));
            record("mp empty filename parts", r);
            assertEquals(200, r.status(), r.toString());
        });
        add("mp text part charsets /fn/parts", () -> {
            String t = "café €";
            List<P> ps = List.of(new P("a", null, "text/plain; charset=UTF-8", utf8(t)), new P("b", null, "text/plain; charset=ISO-8859-1", "café".getBytes(StandardCharsets.ISO_8859_1)));
            Resp r = call(post("/fn/parts", mct, multipart(b, ps, true)));
            record("mp charsets", r);
            assertEquals(200, r.status(), r.toString());
            String[] l = r.text().split("\n");
            assertTrue(l[0].endsWith("|" + describe(utf8(t))), r.text());
            assertTrue(l[1].endsWith("|" + describe("café".getBytes(StandardCharsets.ISO_8859_1))), r.text());
        });
        add("mp /f/mp-basic text part with ISO-8859-1 charset decoded", () -> {
            List<P> ps = List.of(new P("text", null, "text/plain; charset=ISO-8859-1", "café".getBytes(StandardCharsets.ISO_8859_1)), new P("file", "f.txt", "text/plain", utf8("x")));
            Resp r = call(post("/f/mp-basic", mct, multipart(b, ps, true)));
            record("mp text iso", r);
            assertEquals(200, r.status(), r.toString());
            assertTrue(r.text().startsWith("café|"), r.text());
        });
        for (int i0 = 0; i0 < 25; i0++) {
            final int n = i0;
            long s = SEED + 1000 + n;
            add("mp random#" + n + " seed=" + s, () -> {
                Random r = new Random(s);
                int count = 1 + r.nextInt(6);
                List<P> ps = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    boolean file = r.nextBoolean();
                    int len = r.nextInt(4) == 0 ? r.nextInt(300000) : r.nextInt(50);
                    byte[] data = new byte[len];
                    r.nextBytes(data);
                    if (!file) {
                        data = utf8("v" + i + "é" + "x".repeat(r.nextInt(30)));
                    }
                    ps.add(new P(file ? "f" + r.nextInt(3) : "t" + r.nextInt(3), file ? "n" + i + ".bin" : null,
                        file ? (r.nextBoolean() ? "application/octet-stream" : "image/png") : (r.nextBoolean() ? null : "text/plain; charset=UTF-8"), data));
                }
                Req req = post("/fn/parts", mct, multipart(b, ps, true));
                if (r.nextInt(3) == 0) {
                    req.chunked(1 + r.nextInt(5000), 1 + r.nextInt(50));
                }
                Resp resp = call(req);
                record("mp random#" + n, resp);
                assertEquals(200, resp.status(), resp.toString());
                String[] lines = resp.text().split("\n");
                assertEquals(ps.size(), lines.length, "part count: " + resp);
                for (int i = 0; i < ps.size(); i++) {
                    P p = ps.get(i);
                    String ctExp = p.ct() == null ? "-" : p.ct().replace(" ", "");
                    String got = lines[i].replace("; ", ";");
                    assertEquals(p.name() + "|" + p.filename() + "|" + ctExp + "|" + describe(p.data()), got, "part " + i);
                }
            });
        }
        add("mp no boundary -> 4xx", () -> {
            Resp r = call(post("/fn/parts", "multipart/form-data", utf8("garbage")));
            record("mp no boundary", r);
            assertTrue(r.status() >= 400 && r.status() < 500, r.toString());
        });
        add("mp unterminated /fn/parts", () -> {
            Resp r = call(post("/fn/parts", mct, multipart(b, List.of(new P("a", null, null, utf8("abc"))), false)));
            record("mp unterminated", r);
            assertTrue(r.status() < 500 || r.status() == 500, r.toString());
        });
        add("mp unterminated /f/mp-basic", () -> {
            Resp r = call(post("/f/mp-basic", mct, multipart(b, List.of(new P("text", null, null, utf8("abc")), new P("file", "x", null, utf8("abc"))), false)));
            record("mp unterminated ctl", r);
        });
        add("mp wrong content type on file endpoint (json) -> 415 or 400", () -> {
            Resp r = call(post("/f/mp-basic", "application/json", utf8("{}")));
            record("mp json", r);
            assertTrue(r.status() == 415 || r.status() == 400, r.toString());
        });
        add("mp none body /fn/parts", () -> {
            Resp r = call(post("/fn/parts", mct, null));
            record("mp none", r);
            Resp r2 = call(post("/fn/parts", mct, new byte[0]));
            record("mp empty", r2);
        });
        add("mp 17MB -> 413", () -> {
            Resp r = call(post("/f/mp-streaming", mct, multipart(b, List.of(new P("file", "big", null, new byte[17 * 1024 * 1024])), true)));
            record("mp 17MB", r);
            assertEquals(413, r.status(), r.toString());
        });
    }

    static byte[] allBytes() {
        byte[] x = new byte[256 * 20];
        for (int i = 0; i < x.length; i++) {
            x[i] = (byte) i;
        }
        return x;
    }

    // ------------------------------------------------------------------ filters

    void filterMatrix() {
        record Mode(String name, String header) {
        }
        for (String mode : List.of("none", "read", "replace", "clear", "wrap")) {
            for (String ep : List.of("/flt/opt", "/flt/req", "/flt/bytes", "/flt/form")) {
                for (String bk : List.of("small", "none", "empty", "chunked", "large")) {
                    String name = "filter " + mode + " " + ep + " " + bk;
                    add(name, () -> {
                        boolean form = ep.equals("/flt/form");
                        String ct = form ? "application/x-www-form-urlencoded" : "text/plain";
                        byte[] body = switch (bk) {
                            case "small", "chunked" -> utf8(form ? "a=1&b=2" : "hello");
                            case "large" -> utf8(form ? "a=" + "x".repeat(LARGE) : "h".repeat(LARGE));
                            case "empty" -> new byte[0];
                            default -> null;
                        };
                        Req r = post(ep, ct, body);
                        if (!mode.equals("none")) {
                            r.header("x-mode", mode);
                        }
                        if (bk.equals("chunked")) {
                            r.chunked(2, 3);
                        }
                        Resp resp = call(r);
                        record(name, resp);
                        assertFalse(resp.truncated(), resp.toString());
                        boolean hasBody = body != null && body.length > 0;
                        String effective = switch (mode) {
                            case "replace" -> "replacement";
                            case "clear" -> null;
                            default -> hasBody ? new String(body, StandardCharsets.UTF_8) : null;
                        };
                        if (effective == null) {
                            // no body: required -> 400, nullable -> "null"
                            if (ep.equals("/flt/opt")) {
                                assertTrue(resp.status() == 200 && resp.text().equals("body null") || resp.status() == 400, name + ": " + resp);
                            } else {
                                assertTrue(resp.status() == 400, name + ": " + resp);
                            }
                            return;
                        }
                        if (ep.equals("/flt/form")) {
                            if (mode.equals("replace")) {
                                // "replacement" is not a form
                                assertTrue(resp.status() < 500, name + ": " + resp);
                            } else {
                                assertEquals(200, resp.status(), name + ": " + resp);
                                assertEquals("form " + new TreeMap<>(Map.of("a", bk.equals("large") ? "x".repeat(LARGE) : "1")).toString().replace("}", bk.equals("large") ? "}" : ", b=2}"),
                                    resp.text(), name);
                            }
                            return;
                        }
                        assertEquals(200, resp.status(), name + ": " + describe(resp.body()) + resp);
                        if (ep.equals("/flt/bytes")) {
                            assertEquals("bytes " + describe(utf8(effective)), resp.text(), name);
                        } else {
                            assertEquals("body " + effective, resp.text(), name);
                        }
                    });
                }
            }
        }
        add("filter prematching uri change", () -> assertOk("pre", call(new Req("GET", "/pre/abc")), "target /flt/target abc"));
        add("filter prematching uri change POST with body", () -> {
            Resp r = call(post("/pre/abc", "text/plain", utf8("body")));
            record("pre post", r);
            assertTrue(r.status() == 405 || r.status() == 404, r.toString());
        });
    }

    // ------------------------------------------------------------------ responses

    static byte[] pattern(int n) {
        return FuzzApp.pattern(n);
    }

    void responseMatrix() {
        add("resp plain", () -> {
            Resp r = call(new Req("GET", "/r/plain"));
            assertOk("plain", r, "hello");
            assertEquals("5", r.header("content-length"));
        });
        add("resp json", () -> assertOk("json", call(new Req("GET", "/r/json")), "{\"name\":\"n\",\"age\":1}"));
        add("resp empty", () -> {
            Resp r = call(new Req("GET", "/r/empty"));
            assertEquals(200, r.status());
            assertEquals(0, r.body().length);
        });
        for (int n : new int[]{0, 1, 1000, 65536, 1_048_577, 5_000_000}) {
            add("resp large n=" + n, () -> {
                Resp r = call(new Req("GET", "/r/large?n=" + n));
                assertEquals(200, r.status(), r.toString());
                assertFalse(r.truncated());
                assertEquals(describe(pattern(n)), describe(r.body()));
            });
            add("resp streamed-file n=" + n, () -> {
                Resp r = call(new Req("GET", "/r/streamed-file?n=" + n));
                record("streamed-file " + n, r);
                assertEquals(200, r.status(), r.toString());
                assertFalse(r.truncated());
                assertEquals(describe(pattern(n)), describe(r.body()));
            });
            add("resp streamed-file-len n=" + n, () -> {
                Resp r = call(new Req("GET", "/r/streamed-file-len?n=" + n));
                record("streamed-file-len " + n, r);
                assertEquals(200, r.status(), r.toString());
                assertFalse(r.truncated());
                assertEquals(describe(pattern(n)), describe(r.body()));
            });
        }
        for (int n : new int[]{0, 1, 2, 100, 5000}) {
            add("resp flux-json n=" + n, () -> {
                Resp r = call(new Req("GET", "/r/flux-json?n=" + n));
                assertEquals(200, r.status(), r.toString());
                String exp = "[" + java.util.stream.IntStream.range(0, n).mapToObj(i -> "{\"name\":\"i" + i + "\"}").collect(Collectors.joining(",")) + "]";
                assertEquals(exp, r.text());
            });
            add("resp flux-stream n=" + n, () -> {
                Resp r = call(new Req("GET", "/r/flux-stream?n=" + n));
                assertEquals(200, r.status(), r.toString());
                List<String> got = new ArrayList<>();
                Matcher m = Pattern.compile("\"name\":\"(i\\d+)\"").matcher(r.text());
                while (m.find()) {
                    got.add(m.group(1));
                }
                assertEquals(java.util.stream.IntStream.range(0, n).mapToObj(i -> "i" + i).toList(), got, r.text());
                assertFalse(r.truncated());
            });
            add("resp sse n=" + n, () -> {
                Resp r = call(new Req("GET", "/r/sse?n=" + n));
                assertEquals(200, r.status(), r.toString());
                assertTrue(r.header("content-type").startsWith("text/event-stream"), r.toString());
                List<String> got = new ArrayList<>();
                Matcher m = Pattern.compile("data: ?(e\\d+)").matcher(r.text());
                while (m.find()) {
                    got.add(m.group(1));
                }
                assertEquals(java.util.stream.IntStream.range(0, n).mapToObj(i -> "e" + i).toList(), got, r.text());
                List<String> ids = new ArrayList<>();
                m = Pattern.compile("id: ?(id\\d+)").matcher(r.text());
                while (m.find()) {
                    ids.add(m.group(1));
                }
                assertEquals(java.util.stream.IntStream.range(0, n).mapToObj(i -> "id" + i).toList(), ids, r.text());
            });
            add("resp flux-bytes n=" + n, () -> {
                Resp r = call(new Req("GET", "/r/flux-bytes?n=" + n));
                assertEquals(200, r.status(), r.toString());
                byte[] exp = new byte[n * 1000];
                for (int i = 0; i < n; i++) {
                    System.arraycopy(pattern(1000), 0, exp, i * 1000, 1000);
                }
                assertEquals(describe(exp), describe(r.body()));
            });
        }
        add("resp err-before -> 500", () -> {
            Resp r = call(new Req("GET", "/r/err-before"));
            record("err-before", r);
            assertEquals(500, r.status(), r.toString());
        });
        add("resp err-sync -> 500", () -> {
            Resp r = call(new Req("GET", "/r/err-sync"));
            assertEquals(500, r.status(), r.toString());
        });
        for (String p : List.of("err-after", "err-after-stream")) {
            for (int n : new int[]{0, 1, 50, 20000}) {
                add("resp " + p + " n=" + n, () -> {
                    Resp r = call(new Req("GET", "/r/" + p + "?n=" + n));
                    record("resp " + p + " n=" + n, r);
                    // an error after the first element must not look like a successful complete response: a connection
                    // closed before any response (status -1), as on the JDK server, does not
                    assertTrue(r.status() >= 500 || r.status() == -1 || r.truncated(), "error after elements looked like a clean response: " + r);
                });
            }
        }
        add("resp HEAD /r/head", () -> {
            Resp r = call(new Req("HEAD", "/r/head"));
            record("HEAD", r);
            assertEquals(200, r.status(), r.toString());
            assertEquals(0, r.body().length);
        });
        add("resp HEAD /r/plain (no @Head route)", () -> {
            Resp r = call(new Req("HEAD", "/r/plain"));
            record("HEAD plain", r);
            assertEquals(0, r.body().length);
        });
        add("resp HEAD with Content-Length keeps connection usable", () -> {
            try (RawHttp h = new RawHttp(port)) {
                Resp r1 = h.send(new Req("HEAD", "/r/head"), port);
                assertEquals(200, r1.status(), r1.toString());
                Resp r2 = h.send(new Req("GET", "/r/plain"), port);
                assertOk("after head " + r1, r2, "hello");
            }
        });
    }

    // ------------------------------------------------------------------ transport

    void transportMatrix() {
        add("transport keep-alive 90 sequential small posts one connection", () -> {
            try (RawHttp h = new RawHttp(port)) {
                for (int i = 0; i < 90; i++) { // Tomcat closes a connection after 100 requests
                    byte[] data = utf8("n" + i);
                    Resp r = h.send(post("/f/bytes", "application/octet-stream", data), port);
                    assertOk("seq " + i, r, describe(data));
                    assertFalse("close".equalsIgnoreCase(r.header("connection")), "closed at " + i);
                }
            }
        });
        add("transport Connection: close", () -> {
            try (RawHttp h = new RawHttp(port)) {
                Resp r = h.send(post("/f/bytes", "application/octet-stream", utf8("x")), port, true);
                assertOk("close", r, describe(utf8("x")));
                h.socket.setSoTimeout(5000);
                assertEquals(-1, h.in.read(), "server did not close the connection");
            }
        });
        addFull("transport slow client reads 20MB", () -> {
            try (RawHttp h = new RawHttp(port)) {
                h.socket.setReceiveBufferSize(4096);
                h.write(new Req("GET", "/r/large?n=20000000"), port, true);
                java.io.ByteArrayOutputStream hdr = new java.io.ByteArrayOutputStream();
                int state = 0;
                int c;
                while (state < 4 && (c = h.in.read()) != -1) {
                    hdr.write(c);
                    state = (c == '\r' && (state == 0 || state == 2)) || (c == '\n' && (state == 1 || state == 3)) ? state + 1 : (c == '\r' ? 1 : 0);
                }
                String head = hdr.toString(StandardCharsets.ISO_8859_1);
                assertTrue(head.startsWith("HTTP/1.1 200"), head);
                boolean chunked = head.toLowerCase(Locale.ROOT).contains("transfer-encoding: chunked");
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
                long total = 0;
                byte[] buf = new byte[16384];
                int reads = 0;
                java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
                int n;
                while ((n = h.in.read(buf)) != -1) {
                    raw.write(buf, 0, n);
                    total += n;
                    if (++reads % 40 == 0) {
                        Thread.sleep(reads % 400 == 0 ? 1500 : 10);
                    }
                    if (!chunked && total >= 20_000_000) {
                        break;
                    }
                    if (chunked && raw.size() > 20_000_000 && raw.toString(StandardCharsets.ISO_8859_1).endsWith("0\r\n\r\n")) {
                        break;
                    }
                }
                byte[] body = raw.toByteArray();
                if (chunked) {
                    // decode chunks
                    java.io.ByteArrayOutputStream dec = new java.io.ByteArrayOutputStream();
                    int pos = 0;
                    while (true) {
                        int eol = indexOf(body, pos);
                        int sz = Integer.parseInt(new String(body, pos, eol - pos, StandardCharsets.ISO_8859_1).trim(), 16);
                        if (sz == 0) {
                            break;
                        }
                        dec.write(body, eol + 2, sz);
                        pos = eol + 2 + sz + 2;
                    }
                    body = dec.toByteArray();
                }
                assertEquals(describe(pattern(20_000_000)), describe(body), "slow read body");
            }
        });
        for (String path : List.of("/r/infinite", "/r/infinite-sse")) {
            add("transport client disconnect mid-stream " + path, () -> {
                int before = FuzzApp.CANCELLED.get();
                int startedBefore = FuzzApp.STARTED.get();
                try (RawHttp h = new RawHttp(port)) {
                    h.write(new Req("GET", path), port, false);
                    byte[] buf = new byte[2000];
                    int total = 0;
                    while (total < 6000) {
                        int n = h.in.read(buf);
                        assertTrue(n > 0, "stream ended early");
                        total += n;
                    }
                    try {
                        h.socket.setSoLinger(true, 0); // RST
                    } catch (java.net.SocketException ignored) {
                        // already closed by the server
                    }
                }
                long end = System.currentTimeMillis() + 10_000;
                while (FuzzApp.CANCELLED.get() <= before && System.currentTimeMillis() < end) {
                    Thread.sleep(50);
                }
                results.put("transport client disconnect mid-stream " + path, "started+" + (FuzzApp.STARTED.get() - startedBefore) + " cancelled+" + (FuzzApp.CANCELLED.get() - before));
                assertTrue(FuzzApp.CANCELLED.get() > before, "the producer was not cancelled after the client disconnected");
                assertEquals(200, call(new Req("GET", "/r/plain")).status());
            });
        }
        add("transport client disconnect mid-stream (FIN) /r/infinite", () -> {
            int before = FuzzApp.CANCELLED.get();
            try (RawHttp h = new RawHttp(port)) {
                h.write(new Req("GET", "/r/infinite"), port, false);
                byte[] buf = new byte[2000];
                int total = 0;
                while (total < 6000) {
                    int n = h.in.read(buf);
                    assertTrue(n > 0, "stream ended early");
                    total += n;
                }
            }
            long end = System.currentTimeMillis() + 10_000;
            while (FuzzApp.CANCELLED.get() <= before && System.currentTimeMillis() < end) {
                Thread.sleep(50);
            }
            assertTrue(FuzzApp.CANCELLED.get() > before, "the producer was not cancelled after the client closed");
        });
        for (String ep : List.of("/f/is", "/f/pub", "/f/bytes", "/fn/text", "/fn/bytes", "/f/mp-streaming", "/fn/parts", "/fn/elements")) {
            add("transport client disconnect mid-upload " + ep, () -> {
                String mct = "multipart/form-data; boundary=zz";
                String ct = ep.startsWith("/f/mp") || ep.equals("/fn/parts") ? mct : ep.equals("/fn/elements") ? "application/json" : "application/octet-stream";
                try (RawHttp h = new RawHttp(port)) {
                    String head = "POST " + ep + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: " + ct + "\r\nContent-Length: 1000000\r\n\r\n";
                    h.out.write(head.getBytes(StandardCharsets.ISO_8859_1));
                    String partial = ct.equals(mct) ? "--zz\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a\"\r\n\r\n" + "x".repeat(100000) : ct.equals("application/json") ? "[{\"name\":\"a\"}," : "x".repeat(100000);
                    h.out.write(partial.getBytes(StandardCharsets.ISO_8859_1));
                    h.out.flush();
                    Thread.sleep(200);
                    try {
                        h.socket.setSoLinger(true, 0);
                    } catch (java.net.SocketException ignored) {
                        // already closed by the server
                    }
                }
                Thread.sleep(300);
                // the server must still serve, promptly
                for (int i = 0; i < 5; i++) {
                    assertEquals(200, call(new Req("GET", "/r/plain")).status());
                }
                Resp r = call(post("/f/bytes", "application/octet-stream", utf8("after")));
                assertOk("after disconnect", r, describe(utf8("after")));
            });
        }
        add("transport concurrent mixed requests", () -> {
            ExecutorService ex = Executors.newFixedThreadPool(16);
            try {
                List<Future<String>> fs = new ArrayList<>();
                for (int t = 0; t < 16; t++) {
                    long s = SEED + 5000 + t;
                    fs.add(ex.submit(() -> {
                        Random r = new Random(s);
                        try (RawHttp h = new RawHttp(port)) {
                            RawHttp[] hh = {h};
                            for (int i = 0; i < 15; i++) {
                                byte[] data = new byte[r.nextInt(5) == 0 ? 600_000 : r.nextInt(2000)];
                                r.nextBytes(data);
                                String[] eps = {"/f/bytes", "/f/is", "/f/pub", "/fn/bytes", "/fn/copy", "/f/future"};
                                String ep = eps[r.nextInt(eps.length)];
                                Req q = post(ep, ep.equals("/f/future") ? "text/plain" : "application/octet-stream", data);
                                if (r.nextInt(3) == 0) {
                                    q.chunked(1 + r.nextInt(30000));
                                }
                                if (data.length == 0) {
                                    continue;
                                }
                                Resp resp = hh[0].send(q, port);
                                String exp = ep.equals("/fn/copy") ? "copy=" + describe(data) + " orig=" + describe(data) : describe(data);
                                if (resp.status() != 200 || !resp.text().equals(exp)) {
                                    return "thread " + s + " req " + i + " " + ep + " len=" + data.length + ": " + resp;
                                }
                                if (resp.eof() || "close".equalsIgnoreCase(resp.header("connection"))) {
                                    hh[0].close();
                                    hh[0] = new RawHttp(port);
                                }
                            }
                            hh[0].close();
                        }
                        return null;
                    }));
                }
                for (Future<String> f : fs) {
                    String err = f.get(120, java.util.concurrent.TimeUnit.SECONDS);
                    assertEquals(null, err);
                }
            } finally {
                ex.shutdownNow();
            }
        });
        add("transport concurrent slow streams", () -> {
            ExecutorService ex = Executors.newFixedThreadPool(8);
            try {
                List<Future<Resp>> fs = new ArrayList<>();
                for (int t = 0; t < 8; t++) {
                    fs.add(ex.submit(() -> call(new Req("GET", "/r/slow?n=30"))));
                }
                for (Future<Resp> f : fs) {
                    Resp r = f.get(60, java.util.concurrent.TimeUnit.SECONDS);
                    assertEquals(200, r.status(), r.toString());
                    Matcher m = Pattern.compile("\"name\":\"s(\\d+)\"").matcher(r.text());
                    int expect = 0;
                    while (m.find()) {
                        assertEquals(expect++, Integer.parseInt(m.group(1)));
                    }
                    assertEquals(30, expect, r.text());
                }
            } finally {
                ex.shutdownNow();
            }
        });
        // size limits
        add("limit 17MB declared > max-request-size -> 413 (/f/bytes)", () -> {
            Resp r = call(post("/f/bytes", "application/octet-stream", new byte[17 * 1024 * 1024]));
            record("413 declared bytes", r);
            assertEquals(413, r.status(), r.toString());
        });
        add("limit 17MB chunked > max-request-size -> 413 (/f/is)", () -> {
            Resp r = call(post("/f/is", "application/octet-stream", new byte[17 * 1024 * 1024]).chunked(65536));
            record("413 chunked is", r);
            assertEquals(413, r.status(), r.toString());
        });
        // like the Netty server (STREAMED), a body nobody reads is not checked against max-request-size: the route
        // answers, and the connection is closed past the limit rather than the body read to its end
        add("limit 17MB declared, unread -> route answers (/fn/unread)", () -> {
            Resp r = call(post("/fn/unread", "application/octet-stream", new byte[17 * 1024 * 1024]));
            record("413 declared unread", r);
            assertTrue(r.status() == 202 || r.status() == 413, r.toString());
        });
        // like the Netty server (STREAMED), a body nobody reads is not checked against max-request-size: the route
        // answers, and the connection is closed past the limit rather than the body read to its end
        add("limit 17MB declared, unread -> route answers (/f/ignore)", () -> {
            Resp r = call(post("/f/ignore", "application/octet-stream", new byte[17 * 1024 * 1024]));
            record("413 declared ignore", r);
            assertTrue(r.status() == 200 || r.status() == 413, r.toString());
        });
        add("limit fn/bytes-small over limit -> 413", () -> {
            Resp r = call(post("/fn/bytes-small", "application/octet-stream", new byte[5000]));
            record("fn bytes-small", r);
            assertEquals(413, r.status(), r.toString());
        });
        add("limit fn/bytes-small within limit", () -> {
            Resp r = call(post("/fn/bytes-small", "application/octet-stream", new byte[900]));
            assertOk("bytes-small", r, describe(new byte[900]));
        });
        add("limit discard", () -> {
            Resp r = call(post("/fn/discard", "application/octet-stream", randomBytes(3_000_000)));
            assertOk("discard", r, "discarded");
        });
        add("malformed request line/headers do not hang", () -> {
            try (RawHttp h = new RawHttp(Math.max(port, 1))) {
                h.socket.setSoTimeout(10000);
                h.out.write("GET /r/plain HTTP/1.1\r\nHost: x\r\nContent-Length: abc\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                h.out.flush();
                Resp r = h.read(false);
                results.put("malformed content-length", r.toString());
                assertTrue(r.status() == 400 || r.status() == -1, r.toString());
            }
        });
        add("conflicting content-length and chunked is not smuggled", () -> {
            try (RawHttp h = new RawHttp(port)) {
                h.socket.setSoTimeout(10000);
                String body = "5\r\nhello\r\n0\r\n\r\n";
                String smuggled = "GET /r/plain HTTP/1.1\r\nHost: x\r\n\r\n";
                String req = "POST /f/bytes HTTP/1.1\r\nHost: x\r\nContent-Type: application/octet-stream\r\nTransfer-Encoding: chunked\r\nContent-Length: " + (body.length() + smuggled.length()) + "\r\n\r\n" + body + smuggled;
                h.out.write(req.getBytes(StandardCharsets.ISO_8859_1));
                h.out.flush();
                Resp r1 = h.read(false);
                results.put("te+cl", r1.toString());
                // count the number of responses we can read: at most 2 (the POST and the GET are independent requests) is fine
                // only a 400 or a correct chunked interpretation is acceptable for the POST
                assertTrue(r1.status() == 400 || (r1.status() == 200 && r1.text().equals(describe(utf8("hello")))), r1.toString());
            }
        });
    }

    static int indexOf(byte[] b, int from) {
        for (int i = from; i < b.length - 1; i++) {
            if (b[i] == '\r' && b[i + 1] == '\n') {
                return i;
            }
        }
        throw new IllegalStateException("no CRLF");
    }

    @SuppressWarnings("unused")
    private static Charset unused() {
        return Arrays.asList(Charset.availableCharsets().values().toArray(new Charset[0])).get(0);
    }
}
