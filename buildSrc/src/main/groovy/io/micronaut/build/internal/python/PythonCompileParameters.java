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
package io.micronaut.build.internal.python;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;

/**
 * The Python source directories of one compile task. They share one destination, so they are
 * compiled one after the other inside a single work item, never concurrently.
 */
public interface PythonCompileParameters extends WorkParameters {

    ListProperty<String> getSourceDirs();

    /**
     * The absolute directory against which relative source directories are resolved. It is handed to the
     * annotation processor as a compiler option rather than compiled into the output, so the output
     * stays free of absolute paths.
     */
    Property<String> getSourceRoot();

    Property<String> getDestinationDir();

    /**
     * The compile classpath. A worker daemon's {@code java.class.path} is Gradle's own, so the
     * compiler cannot fall back to it the way it does under {@code javaexec}.
     */
    ConfigurableFileCollection getClasspath();
}
