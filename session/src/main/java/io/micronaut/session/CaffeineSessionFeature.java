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
package io.micronaut.session;

import com.oracle.svm.core.annotate.AutomaticFeature;
import io.micronaut.core.annotation.Internal;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * A native image feature that configures Caffeine as used in the session module.
 *
 * @author Tim Yates
 * @since 4.0.0
 */
@Internal
@AutomaticFeature
public class CaffeineSessionFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeClassInitialization.initializeAtBuildTime("com.github.benmanes.caffeine.cache.RemovalCause");

        registerFields(access, "com.github.benmanes.caffeine.cache.BLCHeader$DrainStatusRef", "drainStatus");
        registerFields(access, "com.github.benmanes.caffeine.cache.BBHeader$ReadCounterRef", "readCounter");
        registerFields(access, "com.github.benmanes.caffeine.cache.BBHeader$ReadAndWriteCounterRef", "writeCounter");
        registerFields(access, "com.github.benmanes.caffeine.cache.StripedBuffer", "tableBusy");

        registerFieldsAndDeclaredConstructors(access, "com.github.benmanes.caffeine.cache.SSLA");
        registerFieldsAndDeclaredConstructors(access, "com.github.benmanes.caffeine.cache.PS", "key", "value");
        registerFieldsAndDeclaredConstructors(access, "com.github.benmanes.caffeine.cache.PSW", "writeTime");

        registerFields(access, "java.lang.Thread", "threadLocalRandomProbe");
    }

    private void registerFieldsAndDeclaredConstructors(BeforeAnalysisAccess access, String clz, String... fields) {
        RuntimeReflection.register(access.findClassByName(clz));
        RuntimeReflection.register(access.findClassByName(clz).getDeclaredConstructors());
        registerFields(access, clz, fields);
    }

    private void registerFields(BeforeAnalysisAccess access, String clz, String... fields) {
        for (Field field : access.findClassByName(clz).getDeclaredFields()) {
            if (Arrays.asList(fields).contains(field.getName())) {
                RuntimeReflection.register(field);
            }
        }
    }
}
