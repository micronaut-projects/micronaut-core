/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty.configuration;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import org.particleframework.config.ConfigurationProperties;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.netty.NettyHttpServer;

import java.util.Collections;
import java.util.Map;

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

    public static class Worker extends EventLoopConfig{
    }

    public static class Parent extends EventLoopConfig {
    }

    public static abstract class EventLoopConfig {
        protected int threads;

        /**
         * @return The number of threads to use
         */
        public int getNumOfThreads() {
            return threads;
        }
    }
}
