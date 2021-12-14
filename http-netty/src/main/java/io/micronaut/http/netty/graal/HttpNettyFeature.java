/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.netty.graal;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.graal.AutomaticFeatureUtils;
import io.micronaut.http.bind.binders.ContinuationArgumentBinder;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.micronaut.http.netty.channel.converters.EpollChannelOptionFactory;
import io.micronaut.http.netty.channel.converters.KQueueChannelOptionFactory;
import io.micronaut.http.netty.websocket.NettyWebSocketSession;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * An HTTP Netty feature that configures the native channels.
 *
 * @author Iván López
 * @since 2.0.0
 */
@Internal
@AutomaticFeature
public class HttpNettyFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeClassInitialization.initializeAtRunTime(
                "io.micronaut.http.server.netty.ServerAttributeKeys",
                "io.micronaut.http.server.netty.handler.accesslog.HttpAccessLogHandler",
                "io.micronaut.session.http.SessionLogElement",
                "io.micronaut.http.client.netty.ConnectTTLHandler",
                "io.micronaut.http.client.netty.DefaultHttpClient",
                "io.micronaut.http.server.netty.websocket.NettyServerWebSocketUpgradeHandler",
                "io.micronaut.buffer.netty.NettyByteBufferFactory"
        );
        RuntimeClassInitialization.initializeAtRunTime(
                NettyWebSocketSession.class,
                NettyThreadFactory.class,
                EpollChannelOptionFactory.class,
                KQueueChannelOptionFactory.class,
                ContinuationArgumentBinder.class,
                ContinuationArgumentBinder.Companion.class
        );
    }

}
