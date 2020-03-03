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
package io.micronaut.core.reflect;

import io.micronaut.core.util.Toggleable;
import org.slf4j.LoggerFactory;

import static io.micronaut.core.reflect.ClassUtils.CLASS_LOADING_REPORTERS;
import static io.micronaut.core.reflect.ClassUtils.CLASS_LOADING_REPORTER_ENABLED;

/**
 * An interface that can be implemented by classes that wish to listen to the classloading requirements for the an application. The {@link #close()} method will be called when the application terminates.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface ClassLoadingReporter extends AutoCloseable, Toggleable {

    /**
     * Called when a class is present.
     *
     * @param type The type
     */
    void onPresent(Class<?> type);

    /**
     * Called when a bean is present. Essentially the same as {@link #onPresent(Class)} but listeners may want to treat POJO beans differently.
     *
     * @param type The type
     */
    void onBeanPresent(Class<?> type);

    /**
     * Called when a class is missing.
     *
     * @param name The name of the class
     */
    void onMissing(String name);

    @Override
    void close();

    /**
     * Whether report is enabled.
     * @return True if it is
     */
    static boolean isReportingEnabled() {
        return CLASS_LOADING_REPORTER_ENABLED;
    }

    /**
     * Report a class that is present.
     *
     * @param type The type
     */
    static void reportPresent(Class<?> type) {
        if (CLASS_LOADING_REPORTER_ENABLED) {
            for (ClassLoadingReporter reporter : CLASS_LOADING_REPORTERS) {
                reporter.onPresent(type);
            }
        }
    }

    /**
     * Report a class that is present.
     *
     * @param type The type
     */
    static void reportBeanPresent(Class<?> type) {
        if (CLASS_LOADING_REPORTER_ENABLED) {
            for (ClassLoadingReporter reporter : CLASS_LOADING_REPORTERS) {
                reporter.onBeanPresent(type);
            }
        }
    }

    /**
     * Report a class that is present.
     *
     * @param type The type
     */
    static void reportMissing(String type) {
        if (CLASS_LOADING_REPORTER_ENABLED) {
            for (ClassLoadingReporter reporter : CLASS_LOADING_REPORTERS) {
                reporter.onMissing(type);
            }
        }
    }


    /**
     * Finish reporting classloading.
     */
    static void finish() {
        if (CLASS_LOADING_REPORTER_ENABLED) {
            for (ClassLoadingReporter classLoadingReporter : ClassUtils.CLASS_LOADING_REPORTERS) {
                try {
                    classLoadingReporter.close();
                } catch (Throwable e) {
                    LoggerFactory.getLogger(ClassLoadingReporter.class).warn("Error reporting classloading with loader [" + classLoadingReporter + "]: " + e.getMessage(), e);
                }
            }
        }
    }
}
