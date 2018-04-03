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
     * Create an object of {@link InterfaceHttpData} from {@link StringPart}
     *
     * @param request associated request
     * @param factory An object of class extending {@link HttpDataFactory}, to enable creation of InterfaceHttpData objects from {@link Part}
     * @return {@link InterfaceHttpData} object
     */
    @Override
    InterfaceHttpData getData(HttpRequest request, HttpDataFactory factory) {
        return factory.createAttribute(request, name, value);
    }
}
