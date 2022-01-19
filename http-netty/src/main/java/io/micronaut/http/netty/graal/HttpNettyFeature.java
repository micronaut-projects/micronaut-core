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
                "io.netty",
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
        RuntimeClassInitialization.initializeAtBuildTime("io.netty.util.internal.shaded.org.jctools");

        RuntimeClassInitialization.initializeAtBuildTime(
                "io.netty.util.internal.logging.InternalLoggerFactory",
                "io.netty.util.internal.logging.Slf4JLoggerFactory",
                "io.netty.util.internal.logging.LocationAwareSlf4JLogger"
        );

        // force netty to use slf4j logging
        try {
            InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
        } catch (Throwable e) {
            // can fail if no-op logger is on the classpath
        }

        registerClasses(access,
                "io.netty.channel.kqueue.KQueueChannelOption", "io.netty.channel.epoll.EpollChannelOption");

        registerMethods(access, "io.netty.buffer.AbstractByteBufAllocator", "toLeakAwareBuffer");
        registerMethods(access, "io.netty.buffer.AdvancedLeakAwareByteBuf", "touch", "recordLeakNonRefCountingOperation");
        registerMethods(access, "io.netty.util.ReferenceCountUtil", "touch");

        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        ImageSingletons.lookup(SystemPropertiesSupport.class).initializeProperty("io.netty.tryReflectionSetAccessible", "true");
        try {
            RuntimeReflection.register(access.findClassByName("java.nio.DirectByteBuffer").getDeclaredConstructor(long.class, int.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        Class<?> unsafeOld = access.findClassByName("sun.misc.Unsafe");
        if (unsafeOld != null) {
            try {
                RuntimeReflection.register(unsafeOld.getDeclaredMethod("allocateUninitializedArray", Class.class, int.class));
            } catch (NoSuchMethodException ignored) {
            }
        }
        Class<?> unsafeNew = access.findClassByName("jdk.internal.misc.Unsafe");
        if (unsafeNew != null) {
            try {
                RuntimeReflection.register(unsafeNew.getDeclaredMethod("allocateUninitializedArray", Class.class, int.class));
            } catch (NoSuchMethodException ignored) {
            }
        }
    }

    private void registerClasses(BeforeAnalysisAccess access, String... classes) {
        for (String clazz : classes) {
            AutomaticFeatureUtils.registerClassForRuntimeReflection(access, clazz);
            AutomaticFeatureUtils.registerFieldsForRuntimeReflection(access, clazz);
        }
    }

    private void registerMethods(BeforeAnalysisAccess access, String clz, String... methods) {
        for (Method declaredMethod : access.findClassByName(clz).getDeclaredMethods()) {
            if (Arrays.asList(methods).contains(declaredMethod.getName())) {
                RuntimeReflection.register(declaredMethod);
            }
        }
    }

}
