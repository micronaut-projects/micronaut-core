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

package io.micronaut.graal.reflect;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.runtime.server.EmbeddedServer;

/**
 * A main class that can be used to analyze the classloading requirements of a Micronaut application.
 *
 * @author graemerocher
 * @since 1.0
 */
public final class GraalClassLoadingAnalyzer {

    /**
     * Main method.
     * @param args The arguments
     */
    public static void main(String... args) {
        // enable Graal class analysis
        System.setProperty(GraalClassLoadingReporter.GRAAL_CLASS_ANALYSIS, Boolean.TRUE.toString());

        if (ArrayUtils.isNotEmpty(args)) {
            System.setProperty(GraalClassLoadingReporter.REFLECTION_JSON_FILE, args[0]);
        }

        try {
            ApplicationContext applicationContext = ApplicationContext.run();
            // following beans may impact classloading, so load them.
            applicationContext.findBean(EmbeddedServer.class);
            applicationContext.getBeansOfType(ExecutableMethodProcessor.class);
            // finish up
            applicationContext.stop();
        } catch (Throwable e) {
            System.err.println("An error occurred analyzing class requirements: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}
