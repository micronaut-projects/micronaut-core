package io.micronaut.http.server.netty.binding

import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration

class FullJsonBodyBindingSpec extends JsonBodyBindingSpec {

    @Override
    Map<String, Object> getConfiguration() {
        return super.getConfiguration() + ["micronaut.server.netty.server-type": NettyHttpServerConfiguration.HttpServerType.FULL_CONTENT]
    }
}
