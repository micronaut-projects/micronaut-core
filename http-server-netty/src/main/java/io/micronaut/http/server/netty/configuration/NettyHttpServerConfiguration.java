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
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.NettyHttpServer;
import io.micronaut.runtime.ApplicationConfiguration;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Allows configuring Netty within {@link NettyHttpServer}
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

    public NettyHttpServerConfiguration() {
    }

    @Inject
    public NettyHttpServerConfiguration(ApplicationConfiguration applicationConfiguration) {
        super(applicationConfiguration);
    }

    /**
     * @return The Netty child channel options.
     * @see ServerBootstrap#childOptions()
     */
    public Map<ChannelOption, Object> getChildOptions() {
        return childOptions;
    }

    /**
     * @return The Netty channel options.
     * @see ServerBootstrap#options()
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
     * Configuration for Netty worker
     */
    @ConfigurationProperties("worker")
    public static class Worker extends EventLoopConfig {
    }

    /**
     * Configuration for Netty parent
     */
    @ConfigurationProperties("parent")
    public static class Parent extends EventLoopConfig {
    }

    /**
     * Abstract class for configuring the Netty event loop
     */
    public static abstract class EventLoopConfig {
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
