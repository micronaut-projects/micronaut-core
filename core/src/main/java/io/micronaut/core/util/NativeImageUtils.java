/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.core.util;

import io.micronaut.core.annotation.Internal;

/**
 * Utility class to retrieve information about the context in which code gets executed.
 * Partial fork of {@code org.graalvm.nativeimage.ImageInfo} to avoid a dependency on {@code org.graalvm.sdk:nativeimage}.
 *
 * @since 4.8.0
 */
@Internal
public final class NativeImageUtils {
    /**
     * Holds the string that is the name of the system property providing information about the
     * context in which code is currently executing. If the property returns the string given by
     * {@link #PROPERTY_IMAGE_CODE_VALUE_BUILDTIME} the code is executing in the context of image
     * building (e.g. in a static initializer of a class that will be contained in the image). If
     * the property returns the string given by {@link #PROPERTY_IMAGE_CODE_VALUE_RUNTIME} the code
     * is executing at image runtime. Otherwise, the property is not set.
     */
    public static final String PROPERTY_IMAGE_CODE_KEY = "org.graalvm.nativeimage.imagecode";

    /**
     * Holds the string that will be returned by the system property for
     * {@link NativeImageUtils#PROPERTY_IMAGE_CODE_KEY} if code is executing in the context of image
     * building (e.g. in a static initializer of class that will be contained in the image).
     */
    public static final String PROPERTY_IMAGE_CODE_VALUE_BUILDTIME = "buildtime";

    /**
     * Holds the string that will be returned by the system property for
     * {@link NativeImageUtils#PROPERTY_IMAGE_CODE_KEY} if code is executing at image runtime.
     */
    public static final String PROPERTY_IMAGE_CODE_VALUE_RUNTIME = "runtime";

    /**
     * Native image produces an error at runtime if a JFR event class is initialized, even if that
     * event is not enabled. This flag guards class initialization of any JFR events.
     */
    public static final boolean JFR_AVAILABLE = !inImageCode();

    private NativeImageUtils() {
    }

    /**
     * Returns true if (at the time of the call) code is executing in the context of image building
     * or during image runtime, else false. This method will be const-folded so that it can be used
     * to hide parts of an application that only work when running on the JVM. For example:
     * {@code if (!ImageInfo.inImageCode()) { ... JVM specific code here ... }}
     * @return true if (at the time of the call) code is executing in the context of image building or during image runtime, else false
     */
    public static boolean inImageCode() {
        return inImageBuildtimeCode() || inImageRuntimeCode();
    }

    /**
     * Returns true if (at the time of the call) code is executing at image runtime. This method
     * will be const-folded. It can be used to hide parts of an application that only work when
     * running as native image.
     * @return true if (at the time of the call) code is executing at image runtime.
     */
    public static boolean inImageRuntimeCode() {
        return PROPERTY_IMAGE_CODE_VALUE_RUNTIME.equals(System.getProperty(PROPERTY_IMAGE_CODE_KEY));
    }

    /**
     * Returns true if (at the time of the call) code is executing in the context of image building
     * (e.g. in a static initializer of class that will be contained in the image).
     * @return true if (at the time of the call) code is executing in the context of image building
     */
    public static boolean inImageBuildtimeCode() {
        return PROPERTY_IMAGE_CODE_VALUE_BUILDTIME.equals(System.getProperty(PROPERTY_IMAGE_CODE_KEY));
    }
}
