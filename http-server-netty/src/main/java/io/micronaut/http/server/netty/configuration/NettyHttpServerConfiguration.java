/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.http.server.netty.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.ReadableBytes;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Allows configuring Netty within {@link io.micronaut.http.server.netty.NettyHttpServer}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties("netty")
public class NettyHttpServerConfiguration extends HttpServerConfiguration {

    protected Map<ChannelOption, Object> childOptions = Collections.emptyMap();
    protected Map<ChannelOption, Object> options = Collections.emptyMap();
    protected Worker worker;
    protected Parent parent;
    @ReadableBytes
    protected int maxInitialLineLength = 4096;
    @ReadableBytes
    protected int maxHeaderSize = 8192;
    @ReadableBytes
    protected int maxChunkSize = 8192;
    protected boolean chunkedSupported = true;
    protected boolean validateHeaders = true;
    protected int initialBufferSize = 128;
    protected LogLevel logLevel;

    /**
     * Default empty constructor.
     */
    public NettyHttpServerConfiguration() {
    }

    /**
     * @param applicationConfiguration The application configuration
     */
    @Inject
    public NettyHttpServerConfiguration(ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
    }

    /**
     * The server {@link LogLevel} to enable.
     *
     * @return The server {@link LogLevel} to enable
     */
    public Optional<LogLevel> getLogLevel() {
        return Optional.ofNullable(logLevel);
    }

    /**
     * The maximum length of the initial HTTP request line. Defaults to 4096.
     *
     * @return The maximum length of the initial HTTP request line
     */
    public int getMaxInitialLineLength() {
        return maxInitialLineLength;
    }

    /**
     * The maximum size of an individual HTTP setter. Defaults to 8192.
     *
     * @return The maximum size of an individual HTTP setter
     */
    public int getMaxHeaderSize() {
        return maxHeaderSize;
    }

    /**
     * The maximum chunk size. Defaults to 8192.
     *
     * @return The maximum chunk size
     */
    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    /**
     * Whether chunked requests are supported.
     *
     * @return Whether chunked requests are supported.
     */
    public boolean isChunkedSupported() {
        return chunkedSupported;
    }

    /**
     * Whether to validate headers.
     *
     * @return Whether to validate headers
     */
    public boolean isValidateHeaders() {
        return validateHeaders;
    }

    /**
     * The initial buffer size. Defaults to 128.
     *
     * @return The initial buffer size.
     */
    public int getInitialBufferSize() {
        return initialBufferSize;
    }

    /**
     * @return The Netty child channel options.
     * @see io.netty.bootstrap.ServerBootstrap#childOptions()
     */
    public Map<ChannelOption, Object> getChildOptions() {
        return childOptions;
    }

    /**
     * @return The Netty channel options.
     * @see io.netty.bootstrap.ServerBootstrap#options()
     */
    public Map<ChannelOption, Object> getOptions() {
        return options;
    }

    /**
     * @return Configuration for the worker {@link io.netty.channel.EventLoopGroup}
     */
    public Worker getWorker() {
        return worker;
    }

    /**
     * @return Configuration for the parent {@link io.netty.channel.EventLoopGroup}
     */
    public Parent getParent() {
        return parent;
    }

    /**
     * Configuration for Netty worker.
     */
    @ConfigurationProperties("worker")
    public static class Worker extends EventLoopConfig {
    }

    /**
     * Configuration for Netty parent.
     */
    @ConfigurationProperties("parent")
    public static class Parent extends EventLoopConfig {
    }

    /**
     * Abstract class for configuring the Netty event loop.
     */
    public abstract static class EventLoopConfig {
        protected int threads;
        protected Integer ioRatio;
        protected String executor;

        /**
         * @return The number of threads to use
         */
        public int getNumOfThreads() {
            return threads;
        }

        /**
         * @return The I/O ratio to use
         */
        public OptionalInt getIoRatio() {
            if (ioRatio != null) {
                return OptionalInt.of(ioRatio);
            }
            return OptionalInt.empty();
        }

        /**
         * @return The name of the configured executor to use
         */
        public Optional<String> getExecutorName() {
            if (executor != null) {
                return Optional.of(executor);
            }
            return Optional.empty();
        }
    }
}
