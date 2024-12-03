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
package io.micronaut.context.condition;

/**
 * Condition to hide parts of an application that only work when running on the JVM.
 * Internal implementation is identical to {@code if (!ImageInfo.inImageCode()).
 * @author Sergio del Amo
 * @since 4.8.0
 */
public class RunningOnTheJvm implements Condition {
    private static final String SYS_PROP_ORG_GRAALVM_NATIVEIMAGE_IMAGECODE = "org.graalvm.nativeimage.imagecode";
    private static final String VALUE_RUNTIME = "runtime";
    private static final String VALUE_BUILDTIME = "buildtime";

    @Override
    public boolean matches(ConditionContext context) {
        return !inImageCode();
    }

    private static boolean inImageCode() {
        return inImageBuildtimeCode() || inImageRuntimeCode();
    }

    private static boolean inImageRuntimeCode() {
        return VALUE_RUNTIME.equals(System.getProperty(SYS_PROP_ORG_GRAALVM_NATIVEIMAGE_IMAGECODE));
    }

    private static boolean inImageBuildtimeCode() {
        return VALUE_BUILDTIME.equals(System.getProperty(SYS_PROP_ORG_GRAALVM_NATIVEIMAGE_IMAGECODE));
    }
}
