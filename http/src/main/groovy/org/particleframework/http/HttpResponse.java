package org.particleframework.http;


import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Map;

/**
 * <p>Common interface for HTTP response implementations</p>
 *
 * @since 1.0
 * @author Graeme Rocher
 */
public interface HttpResponse<B> extends HttpMessage<B> {
    /**
     * @return The headers for the response
     */
    @Override
    MutableHttpHeaders getHeaders();

    /**
     * Sets the response encoding
     *
     * @param encoding The encoding to use
     */
    HttpResponse setCharacterEncoding(CharSequence encoding);

    /**
     * Sets the response encoding
     *
     * @param encoding The encoding to use
     */
    default HttpResponse setCharacterEncoding(Charset encoding) {
        return setCharacterEncoding(encoding.toString());
    }

    /**
     * Sets the response status
     *
     * @param status The status
     */
    HttpResponse setStatus(HttpStatus status, CharSequence message);
    /**
     * Set a response header
     *
     * @param name The name of the header
     * @param value The value of the header
     */
    default HttpResponse addHeader(CharSequence name, CharSequence value) {
        getHeaders().add(name, value);
        return this;
    }

    /**
     * Set multiple headers
     *
     * @param namesAndValues The names and values
     */
    default HttpResponse addHeaders(Map<CharSequence,CharSequence> namesAndValues) {
        MutableHttpHeaders headers = getHeaders();
        namesAndValues.forEach(headers::add);
        return this;
    }

    /**
     * Sets the content length
     *
     * @param length The length
     * @return This HttpResponse
     */
    default HttpResponse setContentLength(long length) {
        getHeaders().add(HttpHeaders.CONTENT_LENGTH, String.valueOf(length));
        return this;
    }

    /**
     * Set the response content type
     *
     * @param contentType The content type
     */
    default HttpResponse setContentType(CharSequence contentType) {
        getHeaders().add(HttpHeaders.CONTENT_TYPE, contentType);
        return this;
    }

    /**
     * Sets the locale to use and will apply the appropriate {@link HttpHeaders#CONTENT_LANGUAGE} header to the response
     *
     * @param locale The locale
     * @return This response
     */
    default HttpResponse setLocale(Locale locale) {
        getHeaders().add(HttpHeaders.CONTENT_TYPE, locale.toString());
        return this;
    }

    /**
     * Sets the response status
     *
     * @param status The status
     */
    default HttpResponse setStatus(int status) {
        return setStatus(HttpStatus.valueOf(status));
    }

    /**
     * Sets the response status
     *
     * @param status The status
     */
    default HttpResponse setStatus(int status, CharSequence message) {
        return setStatus(HttpStatus.valueOf(status), message);
    }

    /**
     * Sets the response status
     *
     * @param status The status
     */
    default HttpResponse setStatus(HttpStatus status) {
        return setStatus(status, null);
    }


}