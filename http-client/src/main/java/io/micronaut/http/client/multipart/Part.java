package io.micronaut.http.client.multipart;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

/**
 * The base class representing multiple parts in the {@link MultipartBody} to build a Netty multipart request.
 *
 * @author Puneet Behl
 * @since 1.0
 */
abstract class Part {

    /**
     * Name of the parameter in Multipart request body
     */
    protected final String name;

    /**
     *
     * @param name Name of the parameter
     */
    Part(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Adding parts with a null name is not allowed");
        }
        this.name = name;
    }

    /**
     *
     * @param request Associated request
     * @param factory The factory used to create the {@link InterfaceHttpData}
     * @return {@link InterfaceHttpData} object to build Netty multipart request body
     */
    abstract InterfaceHttpData getData(HttpRequest request, HttpDataFactory factory);
}
