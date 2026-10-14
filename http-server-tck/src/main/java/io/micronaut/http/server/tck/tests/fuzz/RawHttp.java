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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal raw HTTP/1.1 client over a {@link Socket}, for exact control of what is on the wire.
 */
@SuppressWarnings("checkstyle:InnerTypeLast")
final class RawHttp implements AutoCloseable {
    static final int TIMEOUT_MS = 30_000;
    /** Set when the server under test only accepts TLS: the client then speaks HTTP/1.1 over TLS. */
    static volatile SSLSocketFactory tls;

    final Socket socket;
    final InputStream in;
    final OutputStream out;

    RawHttp(int port) throws IOException {
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress("127.0.0.1", port), 5000);
        SSLSocketFactory factory = tls;
        if (factory == null) {
            socket = plain;
        } else {
            SSLSocket ssl = (SSLSocket) factory.createSocket(plain, "localhost", port, true);
            SSLParameters parameters = ssl.getSSLParameters();
            parameters.setApplicationProtocols(new String[]{"http/1.1"});
            ssl.setSSLParameters(parameters);
            ssl.startHandshake();
            socket = ssl;
        }
        socket.setSoTimeout(TIMEOUT_MS);
        in = new java.io.BufferedInputStream(socket.getInputStream(), 65536);
        out = socket.getOutputStream();
    }

    /**
     * Makes the client speak TLS, trusting any certificate: the servers under test use a self-signed one.
     */
    @SuppressWarnings("java:S4830") // a test client of a local server with a self-signed certificate
    static void useTls() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            TrustManager trustAll = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    // trust all
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // trust all
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };
            context.init(null, new TrustManager[]{trustAll}, null);
            tls = context.getSocketFactory();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    record Resp(int status, Map<String, String> headers, byte[] body, boolean eof, boolean truncated) {
        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        String header(String name) {
            return headers.get(name.toLowerCase());
        }

        @Override
        public String toString() {
            String t = text();
            return status + " " + headers + " body[" + body.length + "]=" + (t.length() > 300 ? t.substring(0, 300) + "..." : t) + (truncated ? " TRUNCATED" : "");
        }
    }

    /** A request: head lines + body, either with content-length or chunked. */
    static final class Req {
        final String method;
        final String path;
        final Map<String, String> headers = new LinkedHashMap<>();
        byte[] body;
        int[] chunkSizes; // when non-null: chunked

        Req(String method, String path) {
            this.method = method;
            this.path = path;
        }

        Req header(String n, String v) {
            headers.put(n, v);
            return this;
        }

        Req body(byte[] b) {
            this.body = b;
            return this;
        }

        Req chunked(int... sizes) {
            this.chunkSizes = sizes;
            return this;
        }

        Req contentType(String ct) {
            return header("Content-Type", ct);
        }
    }

    Resp send(Req r, int port) throws IOException {
        return send(r, port, false);
    }

    Resp send(Req r, int port, boolean connectionClose) throws IOException {
        write(r, port, connectionClose);
        return read("HEAD".equals(r.method));
    }

    void write(Req r, int port, boolean connectionClose) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(r.method).append(' ').append(r.path).append(" HTTP/1.1\r\n");
        sb.append("Host: localhost:").append(port).append("\r\n");
        for (var e : r.headers.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
        }
        if (connectionClose) {
            sb.append("Connection: close\r\n");
        }
        if (r.chunkSizes != null) {
            sb.append("Transfer-Encoding: chunked\r\n");
        } else if (r.body != null) {
            sb.append("Content-Length: ").append(r.body.length).append("\r\n");
        }
        sb.append("\r\n");
        // write on a separate thread: a server may answer (and stop reading) before the body is sent
        byte[] head = sb.toString().getBytes(StandardCharsets.ISO_8859_1);
        Thread t = new Thread(() -> {
            try {
                out.write(head);
                if (r.chunkSizes != null) {
                    int pos = 0;
                    int i = 0;
                    byte[] b = r.body == null ? new byte[0] : r.body;
                    while (pos < b.length) {
                        int n = Math.min(r.chunkSizes[i++ % r.chunkSizes.length], b.length - pos);
                        out.write((Integer.toHexString(n) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                        out.write(b, pos, n);
                        out.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
                        pos += n;
                    }
                    out.write("0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                } else if (r.body != null) {
                    int pos = 0;
                    while (pos < r.body.length) {
                        int n = Math.min(65536, r.body.length - pos);
                        out.write(r.body, pos, n);
                        pos += n;
                    }
                }
                out.flush();
            } catch (IOException ignored) {
                // server closed early
            }
        }, "raw-writer");
        t.setDaemon(true);
        t.start();
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                String s = bos.toString(StandardCharsets.ISO_8859_1);
                return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
            }
            bos.write(c);
        }
        return bos.size() == 0 ? null : bos.toString(StandardCharsets.ISO_8859_1);
    }

    Resp read(boolean head) throws IOException {
        String line = readLine();
        while (line != null && line.isEmpty()) {
            line = readLine();
        }
        if (line == null) {
            return new Resp(-1, Map.of(), new byte[0], true, false);
        }
        String[] sl = line.split(" ", 3);
        int status = Integer.parseInt(sl[1]);
        Map<String, String> headers = new LinkedHashMap<>();
        for (line = readLine(); !line.isEmpty(); line = readLine()) {
            int i = line.indexOf(':');
            String k = line.substring(0, i).trim().toLowerCase();
            String v = line.substring(i + 1).trim();
            headers.merge(k, v, (a, b) -> a + "," + b);
        }
        if (status == 100) {
            return read(head);
        }
        if (head || status == 204 || status == 304 || status / 100 == 1) {
            return new Resp(status, headers, new byte[0], false, false);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        boolean truncated = false;
        boolean eof = false;
        try {
            String te = headers.get("transfer-encoding");
            if (te != null && te.toLowerCase().contains("chunked")) {
                while (true) {
                    String size = readLine();
                    if (size == null) {
                        truncated = true;
                        eof = true;
                        break;
                    }
                    int sc = size.indexOf(';');
                    int n = Integer.parseInt((sc >= 0 ? size.substring(0, sc) : size).trim(), 16);
                    if (n == 0) {
                        String trailer = readLine();
                        while (!trailer.isEmpty()) {
                            trailer = readLine(); // skip the trailers
                        }
                        break;
                    }
                    byte[] buf = in.readNBytes(n);
                    body.write(buf);
                    if (buf.length < n) {
                        truncated = true;
                        eof = true;
                        break;
                    }
                    readLine();
                }
            } else if (headers.containsKey("content-length")) {
                long n = Long.parseLong(headers.get("content-length"));
                byte[] buf = in.readNBytes((int) n);
                body.write(buf);
                if (buf.length < n) {
                    truncated = true;
                    eof = true;
                }
            } else {
                body.write(in.readAllBytes());
                eof = true;
            }
        } catch (IOException | RuntimeException e) {
            truncated = true;
            eof = true;
        }
        return new Resp(status, headers, body.toByteArray(), eof, truncated);
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // ignore
        }
    }

    static String describe(byte[] b) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(b);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return b.length + ":" + sb;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
