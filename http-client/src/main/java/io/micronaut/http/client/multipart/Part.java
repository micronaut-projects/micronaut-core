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

    Part(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Adding parts with a null name is not allowed");
        }
        this.name = name;
    }

    abstract InterfaceHttpData getData(HttpRequest request, HttpDataFactory factory);
}
