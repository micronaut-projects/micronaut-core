/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.netty.configuration;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.Objects;

/**
 * Netty listener configuration.
 *
 * @author yawkat
 * @since 3.5.0
 */
public final class NettyListenerConfiguration {
    private Family family = Family.TCP;
    private boolean ssl;
    @Nullable
    private String host;
    private int port;
    private String path;
    private boolean exposeDefaultRoutes = true;

    /**
     * Create a TCP listener configuration.
     *
     * @param host The host to bind to
     * @param port The port to bind to
     * @param ssl Whether to enable SSL
     * @return The configuration with the given settings
     */
    @Internal
    public static NettyListenerConfiguration createTcp(@Nullable String host, int port, boolean ssl) {
        NettyListenerConfiguration configuration = new NettyListenerConfiguration();
        configuration.setFamily(Family.TCP);
        configuration.setHost(host);
        configuration.setPort(port);
        configuration.setSsl(ssl);
        return configuration;
    }

    /**
     * The address family of this listener.
     * @return The address family of this listener.
     */
    public Family getFamily() {
        return family;
    }

    /**
     * The address family of this listener.
     * @param family The address family of this listener.
     */
    public void setFamily(@NonNull Family family) {
        Objects.requireNonNull(family, "family");
        this.family = family;
    }

    /**
     * Whether to enable SSL on this listener. Also requires {@link io.micronaut.http.ssl.SslConfiguration#isEnabled()}
     * to be set.
     * @return Whether to enable SSL on this listener.
     */
    public boolean isSsl() {
        return ssl;
    }

    /**
     * Whether to enable SSL on this listener. Also requires {@link io.micronaut.http.ssl.SslConfiguration#isEnabled()}
     * to be set.
     * @param ssl Whether to enable SSL on this listener.
     */
    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    /**
     * For TCP listeners, the host to bind to, or {@code null} to bind to all hosts.
     * @return For TCP listeners, the host to bind to, or {@code null} to bind to all hosts.
     */
    @Nullable
    public String getHost() {
        return host;
    }

    /**
     * For TCP listeners, the host to bind to, or {@code null} to bind to all hosts.
     * @param host For TCP listeners, the host to bind to, or {@code null} to bind to all hosts.
     */
    public void setHost(@Nullable String host) {
        this.host = host;
    }

    /**
     * The TCP port to bind to. May be {@code -1} to bind to a random port.
     * @return The TCP port to bind to. May be {@code -1} to bind to a random port.
     */
    public int getPort() {
        return port;
    }

    /**
     * The TCP port to bind to. May be {@code -1} to bind to a random port.
     * @param port The TCP port to bind to. May be {@code -1} to bind to a random port.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * For UNIX domain sockets, the path of the socket. For abstract domain sockets, this should start with a NUL byte.
     * @return For UNIX domain sockets, the path of the socket. For abstract domain sockets, this should start with a NUL byte.
     */
    public String getPath() {
        return path;
    }

    /**
     * For UNIX domain sockets, the path of the socket. For abstract domain sockets, this should start with a NUL byte.
     * @param path For UNIX domain sockets, the path of the socket. For abstract domain sockets, this should start with a NUL byte.
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Whether to expose default routes on this listener.
     * @return Whether to expose default routes on this listener.
     */
    @Internal
    public boolean isExposeDefaultRoutes() {
        return exposeDefaultRoutes;
    }

    /**
     * Whether to expose default routes on this listener.
     * @param exposeDefaultRoutes Whether to expose default routes on this listener.
     */
    @Internal
    public void setExposeDefaultRoutes(boolean exposeDefaultRoutes) {
        this.exposeDefaultRoutes = exposeDefaultRoutes;
    }

    /**
     * Address family enum.
     */
    public enum Family {
        /**
         * TCP socket.
         */
        TCP,
        /**
         * UNIX domain socket.
         */
        UNIX,
    }
}
