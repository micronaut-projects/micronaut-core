package org.particleframework.http;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collection;
import java.util.Locale;

/**
 * <p>Common interface for HTTP request implementations</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HttpRequest<T> extends HttpMessage<T> {

    /**
     * @return The request method
     */
    HttpMethod getMethod();

    /**
     * @return The request URI
     */
    URI getUri();

    @Override
    default Locale getLocale() {
        return getHeaders().findFirst(HttpHeaders.ACCEPT_LANGUAGE)
                .map(Locale::new)
                .orElse(null);
    }

    /**
     * @return The request character encoding
     */
    default Charset getCharacterEncoding() {
        MediaType contentType = getContentType();
        String charset = contentType.getParameters().get("charset");
        try {
            if(charset != null) {
                return Charset.forName(charset);
            }
            else {
                return getHeaders().findFirst(HttpHeaders.ACCEPT_CHARSET)
                            .map(Charset::forName)
                            .orElse(null);
            }
        } catch (UnsupportedCharsetException e) {
            return null;
        }
    }

}