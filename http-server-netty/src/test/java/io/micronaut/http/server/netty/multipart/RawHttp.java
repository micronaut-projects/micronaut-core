package io.micronaut.http.server.netty.multipart;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Sends raw HTTP/1.1 requests and reads one response.
 */
public final class RawHttp {
    private RawHttp() {
    }

    /**
     * Writes the request, in the given chunks with a pause in between, and reads the response
     * head and its content-length body, or until the connection closes or the timeout passes.
     *
     * @param port          The port
     * @param timeoutMillis The read timeout
     * @param chunks        The request bytes
     * @return The response text, empty when nothing arrived
     * @throws IOException on connection failure
     * @throws InterruptedException if interrupted
     */
    public static String exchange(int port, int timeoutMillis, byte[]... chunks) throws IOException, InterruptedException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(timeoutMillis);
            OutputStream out = s.getOutputStream();
            for (int i = 0; i < chunks.length; i++) {
                if (i > 0) {
                    Thread.sleep(300);
                }
                out.write(chunks[i]);
                out.flush();
            }
            InputStream in = s.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            try {
                int n;
                while ((n = in.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                    String text = bos.toString(StandardCharsets.ISO_8859_1);
                    int headEnd = text.indexOf("\r\n\r\n");
                    if (headEnd >= 0) {
                        int cl = text.toLowerCase(Locale.ROOT).indexOf("content-length:");
                        if (cl >= 0 && cl < headEnd) {
                            int len = Integer.parseInt(text.substring(cl + 15, text.indexOf("\r\n", cl)).trim());
                            if (bos.size() >= headEnd + 4 + len) {
                                break;
                            }
                        }
                    }
                }
            } catch (SocketTimeoutException ignored) {
                // return what we have
            }
            return bos.toString(StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * @param head The request head without the final empty line
     * @param body The body
     * @return The request with a Content-Length header
     */
    public static byte[] withBody(String head, byte[] body) {
        byte[] h = (head + "Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
        byte[] r = new byte[h.length + body.length];
        System.arraycopy(h, 0, r, 0, h.length);
        System.arraycopy(body, 0, r, h.length, body.length);
        return r;
    }

    public static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }
}
