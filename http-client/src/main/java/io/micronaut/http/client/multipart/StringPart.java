package io.micronaut.http.client.multipart;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

/**
 * A class representing a String {@link Part} in {@link MultipartBody} to build a Netty multipart request.
 *
 * @author Puneet Behl
 * @since 1.0
 */
class StringPart extends Part {

    protected final String value;

    /**
     *
     * @param name parameter name
     * @param value String value
     */
    StringPart(String name, String value) {
        super(name);
        if (value == null) {
            this.value = "";
        } else {
            this.value = value;
        }
    }

    /**
     * Create an object of {@link InterfaceHttpData} to build Netty multipart request body
     *
     * @see Part#getData(HttpRequest, HttpDataFactory)
     */
    @Override
    InterfaceHttpData getData(HttpRequest request, HttpDataFactory factory) {
        return factory.createAttribute(request, name, value);
    }
}
