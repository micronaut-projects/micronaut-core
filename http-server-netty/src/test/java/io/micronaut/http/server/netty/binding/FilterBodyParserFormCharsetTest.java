package io.micronaut.http.server.netty.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestAttribute;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.server.filter.FilterBodyParser;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A filter reading the form with {@link FilterBodyParser} and the route binding the form must see the same fields.
 */
class FilterBodyParserFormCharsetTest {
    private static final String SPEC_NAME = "FilterBodyParserFormCharsetTest";

    @Test
    void filterAndRouteSeeTheSameFieldsForAnAsciiIncompatibleCharset() throws Exception {
        Charset ebcdic = Charset.forName("IBM037");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write("who=alice&z=".getBytes(ebcdic));
        body.write('&');
        body.write("who".getBytes(ebcdic));
        body.write('=');
        body.write("bob".getBytes(ebcdic));
        assertEquals("filter-who=bob route-who=bob", post(body.toByteArray(), "IBM037"));
    }

    @Test
    void filterAndRouteSeeTheSameFieldsForUtf8() throws Exception {
        byte[] body = "who=b%C3%B6b&other=café".getBytes(StandardCharsets.UTF_8);
        assertEquals("filter-who=böb route-who=böb", post(body, "UTF-8"));
    }

    private static String post(byte[] body, String charset) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
                socket.setSoTimeout(10_000);
                OutputStream out = socket.getOutputStream();
                out.write(("POST /filter-form-charset HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n"
                    + "Content-Type: application/x-www-form-urlencoded; charset=" + charset + "\r\n"
                    + "Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                out.write(body);
                out.flush();
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return response.substring(response.indexOf("\r\n\r\n") + 4);
            }
        }
    }

    @ServerFilter("/filter-form-charset")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class WhoFilter {
        private final FilterBodyParser parser;

        WhoFilter(FilterBodyParser parser) {
            this.parser = parser;
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @RequestFilter
        void filter(HttpRequest<?> request) throws Exception {
            Map<String, Object> map = parser.parseBody(request).get();
            request.setAttribute("filter-who", String.valueOf(map.get("who")));
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FormController {
        @Post(value = "/filter-form-charset", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String post(@Body("who") String who, @RequestAttribute("filter-who") String filterWho) {
            return "filter-who=" + filterWho + " route-who=" + who;
        }
    }
}
