/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.netty.channel;

import io.micronaut.core.annotation.Internal;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.unix.DomainSocketAddress;

import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;

/**
 * Builds the UNIX domain socket address type that matches the selected transport. The JDK NIO
 * transport requires a {@link UnixDomainSocketAddress}, while the epoll and kqueue transports
 * require netty's own {@link DomainSocketAddress}.
 *
 * @author Shubham Jain
 * @since 5.3.0
 */
@Internal
public final class DomainSocketAddresses {

    private DomainSocketAddresses() {
    }

    /**
     * @param path  The socket path
     * @param group The event loop group the channel will be registered with
     * @return The address to bind or connect to
     */
    public static SocketAddress of(String path, EventLoopGroup group) {
        boolean nio = group instanceof IoEventLoopGroup ioEventLoopGroup
            && ioEventLoopGroup.isIoType(NioIoHandler.class);
        return nio ? UnixDomainSocketAddress.of(path) : NettyAddressHolder.of(path);
    }

    /**
     * Defers loading netty's native transport class so that a missing dependency reports a useful
     * error instead of failing at class initialization.
     */
    private static final class NettyAddressHolder {
        private static SocketAddress of(String path) {
            try {
                return new DomainSocketAddress(path);
            } catch (NoClassDefFoundError e) {
                throw new UnsupportedOperationException("Netty domain socket support not on classpath", e);
            }
        }
    }
}
