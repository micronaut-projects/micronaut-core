/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.cli;

import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "jdk.internal.jrtfs.SystemImage")
@KeepOriginal
public final class SystemImageSubstitutions {
    /**
     * When running the compiler, javac expects the path from JRTFS
     * to end with "/lib" but when compiled in native image, the
     * path we get is the binary name
     * @return the path to Java
     */
    @Substitute
    private static String findHome() {
        var javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            return javaHome;
        }
        javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            return javaHome;
        }
        return null;
    }
}
