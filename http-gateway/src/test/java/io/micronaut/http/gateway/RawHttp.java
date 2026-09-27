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
package io.micronaut.http.gateway;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An HTTP/1.x client on a plain socket, for the tests that need the exact bytes on the wire:
 * the framing, the header names and order, and malformed requests.
 */
final class RawHttp implements AutoCloseable {

    final Socket socket;
    final InputStream in;
    final OutputStream out;

    RawHttp(int port) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
        socket.setSoTimeout(20_000);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    static Response exchange(int port, String request) throws IOException {
        return exchange(port, request.getBytes(StandardCharsets.ISO_8859_1), false);
    }

    static Response exchange(int port, byte[] request, boolean head) throws IOException {
        try (RawHttp http = new RawHttp(port)) {
            http.send(request);
            return http.read(head);
        }
    }

    void send(String request) throws IOException {
        send(request.getBytes(StandardCharsets.ISO_8859_1));
    }

    void send(byte[] request) throws IOException {
        out.write(request);
        out.flush();
    }

    Response read(boolean head) throws IOException {
        return read(in, head);
    }

    /**
     * @return The bytes until the peer closes the connection or the read times out
     */
    byte[] readToEnd() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            int n;
            while ((n = in.read(buffer)) >= 0) {
                bytes.write(buffer, 0, n);
            }
        } catch (SocketTimeoutException | java.net.SocketException e) {
            // the end
        }
        return bytes.toByteArray();
    }

    /**
     * @return Whether the peer closed the connection, within the timeout
     */
    boolean isClosedByPeer(int timeoutMillis) throws IOException {
        socket.setSoTimeout(timeoutMillis);
        try {
            return in.read() < 0;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (java.net.SocketException e) {
            return true;
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    static Response read(InputStream in, boolean head) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        String statusLine = line(in, raw);
        while (statusLine.isEmpty()) {
            statusLine = line(in, raw);
        }
        List<Map.Entry<String, String>> headers = new ArrayList<>();
        String line;
        while (!(line = line(in, raw)).isEmpty()) {
            int colon = line.indexOf(':');
            headers.add(Map.entry(line.substring(0, colon), line.substring(colon + 1).strip()));
        }
        String[] parts = statusLine.split(" ", 3);
        int status = Integer.parseInt(parts[1]);
        Response response = new Response(statusLine, status, headers, new byte[0], false, true, raw.toByteArray());
        if (status / 100 == 1) {
            return response;
        }
        if (head || status == 204 || status == 304) {
            return response;
        }
        String contentLength = response.header("content-length");
        String transferEncoding = response.header("transfer-encoding");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        boolean complete = true;
        boolean chunked = transferEncoding != null && transferEncoding.toLowerCase(Locale.ROOT).contains("chunked");
        try {
            if (chunked) {
                while (true) {
                    String size = line(in, raw);
                    int semicolon = size.indexOf(';');
                    int length = Integer.parseInt((semicolon < 0 ? size : size.substring(0, semicolon)).strip(), 16);
                    if (length == 0) {
                        // trailers
                        String trailer;
                        while (!(trailer = line(in, raw)).isEmpty()) {
                            int colon = trailer.indexOf(':');
                            headers.add(Map.entry("trailer:" + trailer.substring(0, colon), trailer.substring(colon + 1).strip()));
                        }
                        break;
                    }
                    byte[] chunk = in.readNBytes(length);
                    raw.write(chunk);
                    body.write(chunk);
                    if (chunk.length < length) {
                        complete = false;
                        break;
                    }
                    line(in, raw);
                }
            } else if (contentLength != null) {
                int length = Integer.parseInt(contentLength);
                byte[] bytes = in.readNBytes(length);
                raw.write(bytes);
                body.write(bytes);
                complete = bytes.length == length;
            } else {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    raw.write(buffer, 0, n);
                    body.write(buffer, 0, n);
                }
            }
        } catch (EOFException | java.net.SocketException | NumberFormatException e) {
            complete = false;
        }
        return new Response(statusLine, status, headers, body.toByteArray(), chunked, complete, raw.toByteArray());
    }

    private static String line(InputStream in, ByteArrayOutputStream raw) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            raw.write(c);
            if (c == '\n') {
                int length = line.length();
                if (length > 0 && line.charAt(length - 1) == '\r') {
                    line.setLength(length - 1);
                }
                return line.toString();
            }
            line.append((char) c);
        }
        throw new EOFException("The connection closed: " + line);
    }

    record Response(String statusLine, int status, List<Map.Entry<String, String>> headers, byte[] body, boolean chunked,
                    boolean complete, byte[] raw) {

        String header(String name) {
            for (Map.Entry<String, String> header : headers) {
                if (header.getKey().equalsIgnoreCase(name)) {
                    return header.getValue();
                }
            }
            return null;
        }

        List<String> headers(String name) {
            List<String> values = new ArrayList<>();
            for (Map.Entry<String, String> header : headers) {
                if (header.getKey().equalsIgnoreCase(name)) {
                    values.add(header.getValue());
                }
            }
            return values;
        }

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        /**
         * The lines of the body of an inspect answer of the upstream, {@code key=value}.
         */
        Map<String, List<String>> inspected() {
            Map<String, List<String>> values = new LinkedHashMap<>();
            for (String l : text().split("\n")) {
                int eq = l.indexOf('=');
                if (eq > 0) {
                    values.computeIfAbsent(l.substring(0, eq), k -> new ArrayList<>()).add(l.substring(eq + 1));
                }
            }
            return values;
        }

        String inspected(String key) {
            List<String> values = inspected().get(key);
            return values == null ? null : String.join(", ", values);
        }

        @Override
        public String toString() {
            return statusLine + " " + headers + " " + (body.length < 2000 ? text() : body.length + " bytes");
        }
    }
}
