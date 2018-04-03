package io.micronaut.http.client.multipart;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

/**
 * An abstract base class representing multiple parts in the {@link MultipartBody} to build a Netty multipart request.
 *
 * @author Puneet Behl
 * @since 1.0
 */
abstract class Part {
    protected final String name;

    /**
     *
     * @param name parameter name
     */
    Part(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Adding parts with a null name is not allowed");
        }
        this.name = name;
    }

    /**
     *
     * @param request associated request
     * @param factory An object of class extending {@link HttpDataFactory}, to enable creation of InterfaceHttpData objects from {@link Part}
     * @return {@link InterfaceHttpData} object
     */
    abstract InterfaceHttpData getData(HttpRequest request, HttpDataFactory factory);
}
