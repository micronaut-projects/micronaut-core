package io.micronaut.http.client.http2;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.server.EmbeddedServer;

public class Http2Application {

    public static void main(String[] args) {
        Micronaut
                .build(args)
                .properties(CollectionUtils.mapOf(
                        "micronaut.server.http-version" , "2.0",
                        "micronaut.server.netty.log-level" , "TRACE"
                ))
                .run(EmbeddedServer.class);
    }
}
