/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.runtime.jbang;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.runtime.Micronaut;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides integration for running Micronaut application's as JBang scripts.
 *
 * @author graemerocher
 * @since 2.5.5
 */
@Internal
@Experimental
public final class JBangIntegration {

    public static final String CONFIG = "//M:CONFIG ";

    /**
     * The JBang integration hook.
     * @param appClasses The application classes
     * @param pomFile The POM file
     * @param repositories the repositories
     * @param originalDeps The original dependencies
     * @param comments The comments
     * @param nativeImage Is this a native image
     * @return The integration data
     */
    public static Map<String, Object> postBuild(Path appClasses,
                                                Path pomFile,
                                                List<Map.Entry<String, String>> repositories,
                                                List<Map.Entry<String, Path>> originalDeps,
                                                List<String> comments,
                                                boolean nativeImage) {
        Map<String, Object> integration = new HashMap<>(4);
        integration.put("main-class", Micronaut.class.getName());

        List<String> javaArgs = new ArrayList<>();
        for (String comment : comments) {
            if (comment.startsWith(CONFIG)) {
                String conf = comment.substring(CONFIG.length()).trim();
                int equals = conf.indexOf("=");
                if (equals == -1) {
                    javaArgs.add("-D" + conf + "=true");
                } else {
                    final String n = conf.substring(0, equals);
                    final String v = conf.substring(equals + 1);
                    javaArgs.add("-D" + n + "=" + v);
                }
            }
        }
        if (!javaArgs.isEmpty()) {
            integration.put("java-args", javaArgs);
        }
        return integration;
    }
}
