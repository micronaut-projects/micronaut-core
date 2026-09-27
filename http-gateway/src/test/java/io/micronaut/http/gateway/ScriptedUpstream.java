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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An upstream on a plain server socket whose answers the tests script byte for byte, e.g. a
 * malformed or truncated response, a reset, an interim response or a protocol switch.
 */
final class ScriptedUpstream implements AutoCloseable {

    /**
     * Answers a request on a connection.
     */
    @FunctionalInterface
    interface Script {
        /**
         * @param request The request head, the body not read yet
         * @param in      The rest of the input of the connection
         * @param out     The output of the connection
         * @param socket  The connection
         * @return Whether the connection can take another request
         */
        boolean answer(Request request, InputStream in, OutputStream out, Socket socket) throws Exception;
    }

    record Request(String method, String target, String version, Map<String, List<String>> headers, List<String> headerLines) {
        String header(String name) {
            List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
            return values == null ? null : values.get(0);
        }

        List<String> headers(String name) {
            List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
            return values == null ? List.of() : values;
        }

        byte[] readBody(InputStream in) throws IOException {
            String length = header("content-length");
            if (length != null) {
                return in.readNBytes(Integer.parseInt(length.strip()));
            }
            String te = header("transfer-encoding");
            if (te != null && te.toLowerCase(Locale.ROOT).contains("chunked")) {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                while (true) {
                    String size = line(in);
                    int n = Integer.parseInt(size.split(";")[0].strip(), 16);
                    if (n == 0) {
                        while (!line(in).isEmpty()) {
                            // trailers
                        }
                        return body.toByteArray();
                    }
                    body.write(in.readNBytes(n));
                    line(in);
                }
            }
            return new byte[0];
        }
    }

    private final ServerSocket serverSocket;
    private final Thread acceptor;
    private final Map<String, Script> scripts = new LinkedHashMap<>();
    final List<Request> requests = new CopyOnWriteArrayList<>();
    final AtomicInteger connections = new AtomicInteger();
    final BlockingQueue<String> events = new LinkedBlockingQueue<>();
    private final List<Socket> sockets = new CopyOnWriteArrayList<>();

    ScriptedUpstream() throws IOException {
        serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        acceptor = new Thread(this::accept, "scripted-upstream");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /**
     * @param pathPrefix The prefix of the paths the script answers
     * @param script     The script
     * @return This upstream
     */
    synchronized ScriptedUpstream on(String pathPrefix, Script script) {
        scripts.put(pathPrefix, script);
        return this;
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + serverSocket.getLocalPort());
    }

    int port() {
        return serverSocket.getLocalPort();
    }

    private void accept() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                sockets.add(socket);
                connections.incrementAndGet();
                Thread thread = new Thread(() -> serve(socket), "scripted-upstream-connection");
                thread.setDaemon(true);
                thread.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            while (true) {
                Request request = readHead(in);
                if (request == null) {
                    events.add("closed");
                    return;
                }
                requests.add(request);
                events.add("request " + request.method() + " " + request.target());
                Script script = script(request.target());
                if (script == null) {
                    out.write("HTTP/1.1 404 Not Found\r\ncontent-length: 0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                    continue;
                }
                if (!script.answer(request, in, out, socket)) {
                    return;
                }
            }
        } catch (Exception e) {
            events.add("error " + e);
        }
    }

    private synchronized Script script(String target) {
        Script found = null;
        int longest = -1;
        for (Map.Entry<String, Script> entry : scripts.entrySet()) {
            if (target.startsWith(entry.getKey()) && entry.getKey().length() > longest) {
                found = entry.getValue();
                longest = entry.getKey().length();
            }
        }
        return found;
    }

    static Request readHead(InputStream in) throws IOException {
        String requestLine;
        try {
            requestLine = line(in);
        } catch (java.io.EOFException | java.net.SocketException e) {
            return null;
        }
        while (requestLine.isEmpty()) {
            requestLine = line(in);
        }
        String[] parts = requestLine.split(" ", 3);
        Map<String, List<String>> headers = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>();
        String line;
        while (!(line = line(in)).isEmpty()) {
            lines.add(line);
            int colon = line.indexOf(':');
            headers.computeIfAbsent(line.substring(0, colon).strip().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(line.substring(colon + 1).strip());
        }
        return new Request(parts[0], parts[1], parts.length > 2 ? parts[2] : "", headers, lines);
    }

    static String line(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') {
                int length = line.length();
                if (length > 0 && line.charAt(length - 1) == '\r') {
                    line.setLength(length - 1);
                }
                return line.toString();
            }
            line.append((char) c);
        }
        throw new java.io.EOFException();
    }

    static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        for (Socket socket : sockets) {
            socket.close();
        }
    }
}
