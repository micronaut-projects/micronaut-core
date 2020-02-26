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
package io.micronaut.inject.visitor;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Provides a way for {@link TypeElementVisitor} classes to log messages during compilation and fail compilation.
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
public interface VisitorContext extends MutableConvertibleValues<Object>, ClassWriterOutputVisitor {
    String MICRONAUT_BASE_OPTION_NAME = "micronaut";
    /**
     * Allows printing informational messages.
     *
     * @param message The message
     * @param element The element
     */
    void info(String message, @Nullable Element element);

    /**
     * Allows printing informational messages.
     *
     * @param message The message
     */
    void info(String message);

    /**
     * Allows failing compilation for a given element with the given message.
     *
     * @param message The message
     * @param element The element
     */
    void fail(String message, @Nullable Element element);

    /**
     * Allows printing a warning for the given message and element.
     *
     * @param message The message
     * @param element The element
     */
    void warn(String message, @Nullable Element element);

    /**
     * Visit a file within the META-INF directory.
     *
     * @param path The path to the file
     * @return An optional file it was possible to create it
     */
    @Experimental
    Optional<GeneratedFile> visitMetaInfFile(String path);


    /**
     * Visit a file that will be located within the generated source directory.
     *
     * @param path The path to the file
     * @return An optional file it was possible to create it
     */
    @Experimental
    Optional<GeneratedFile> visitGeneratedFile(String path);

    /**
     * Obtain a set of resources from the user classpath.
     *
     * @param path The path
     * @return An iterable of resources
     */
    @Experimental
    default @NonNull Iterable<URL> getClasspathResources(@NonNull String path) {
        return Collections.emptyList();
    }

    /**
     * This method will lookup another class element by name. If it cannot be found an empty optional will be returned.
     *
     * @param name The name
     * @return The class element
     */
    default Optional<ClassElement> getClassElement(String name) {
        return Optional.empty();
    }

    /**
     * This method will lookup another class element by name. If it cannot be found an empty optional will be returned.
     *
     * @param type The name
     * @return The class element
     */
    default Optional<ClassElement> getClassElement(Class<?> type) {
        if (type != null) {
            return getClassElement(type.getName());
        }
        return Optional.empty();
    }

    /**
     * Find all the classes within the given package and having the given annotation.
     * @param aPackage The package
     * @param stereotypes The stereotypes
     * @return The class elements
     */
    default @NonNull ClassElement[] getClassElements(@NonNull String aPackage, @NonNull String... stereotypes) {
        return new ClassElement[0];
    }

    /**
     * The annotation processor environment custom options.
     * <p><b>All options names MUST start with {@link VisitorContext#MICRONAUT_BASE_OPTION_NAME}</b></p>
     * @return A Map with annotation processor runtime options
     * @see javax.annotation.processing.ProcessingEnvironment#getOptions()
     */
    @Experimental
    default Map<String, String> getOptions() {
        return Collections.emptyMap();
    }
}
