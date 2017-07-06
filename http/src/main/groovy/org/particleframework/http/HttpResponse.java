package org.particleframework.http;


import java.util.Map;

/**
 * <p>Common interface for HTTP response implementations</p>
 *
 * @since 1.0
 */
public interface HttpResponse {

    /**
     * Set a response header
     *
     * @param name The name of the header
     * @param value The value of the header
     */
    void addHeader(String name, String value);

    /**
     * Set a single named value header
     * @param nameAndValue The name and value. Example header(foo:"bar")
     */
    void addHeader(Map<String,String> nameAndValue);

    /**
     * Set multiple headers
     *
     * @param namesAndValues The names and values
     */
    void addheaders(Map<String,String> namesAndValues);

    /**
     * Set the response content type
     *
     * @param contentType
     */
    void contentType(CharSequence contentType);

    /**
     * Sets the response encoding
     *
     * @param encoding
     */
    void encoding(String encoding);

    /**
     * Sets the response status
     *
     * @param status The status
     */
    void status(int status);

    /**
     * Sets the response status
     *
     * @param status The status
     */
    void status(int status, String message);

    /**
     * Sets the response status
     *
     * @param status The status
     */
    void status(HttpStatus status);

    /**
     * Sets the response status
     *
     * @param status The status
     */
    void status(HttpStatus status, String message);
}