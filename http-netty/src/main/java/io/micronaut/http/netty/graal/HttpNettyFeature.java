/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.netty.graal;

import com.oracle.svm.core.annotate.AutomaticFeature;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graal.AutomaticFeatureUtils;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

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
                "io.netty.channel.epoll",
                "io.netty.channel.kqueue",
                "io.netty.channel.unix"
        );

        registerClasses(access,
                "io.netty.channel.kqueue.KQueueChannelOption", "io.netty.channel.epoll.EpollChannelOption");
    }

    private void registerClasses(BeforeAnalysisAccess access, String... classes) {
        for (String clazz : classes) {
            AutomaticFeatureUtils.registerClassForRuntimeReflection(access, clazz);
            AutomaticFeatureUtils.registerFieldsForRuntimeReflection(access, clazz);
        }
    }

}
