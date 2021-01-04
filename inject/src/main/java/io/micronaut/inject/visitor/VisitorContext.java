/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.visitor;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementFactory;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
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
    String MICRONAUT_PROCESSING_PROJECT_DIR = "micronaut.processing.project.dir";
    String MICRONAUT_PROCESSING_GROUP = "micronaut.processing.group";
    String MICRONAUT_PROCESSING_MODULE = "micronaut.processing.module";

    /**
     * Gets the element factory for this visitor context.
     * @return The element factory
     * @since 2.3.0
     */
    @NonNull ElementFactory<?, ?, ?, ?> getElementFactory();

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
     * @deprecated Visiting a file should supply the originating elements. Use {@link #visitMetaInfFile(String, Element...)} instead
     */
    @Override
    @Experimental
    @Deprecated
    default Optional<GeneratedFile> visitMetaInfFile(String path) {
        return visitMetaInfFile(path, Element.EMPTY_ELEMENT_ARRAY);
    }

    /**
     * Visit a file within the META-INF directory.
     *
     * @param path The path to the file
     * @return An optional file it was possible to create it
     */
    @Override
    @Experimental
    Optional<GeneratedFile> visitMetaInfFile(String path, Element...originatingElements);

    /**
     * Visit a file that will be located within the generated source directory.
     *
     * @param path The path to the file
     * @return An optional file it was possible to create it
     */
    @Override
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
     * Obtain the project directory.
     *
     * @return An optional wrapping the project directory
     */
    default Optional<Path> getProjectDir() {
        Optional<Path> projectDir = get(MICRONAUT_PROCESSING_PROJECT_DIR, Path.class);
        if (projectDir.isPresent()) {
            return projectDir;
        }
        // let's find the projectDir
        Optional<GeneratedFile> dummyFile = visitGeneratedFile("dummy");
        if (dummyFile.isPresent()) {
            URI uri = dummyFile.get().toURI();
            // happens in tests 'mem:///CLASS_OUTPUT/dummy'
            if (uri.getScheme() != null && !uri.getScheme().equals("mem")) {
                // assume files are generated in 'build' or 'target' directories
                Path dummy = Paths.get(uri).normalize();
                while (dummy != null) {
                    Path dummyFileName = dummy.getFileName();
                    if (dummyFileName != null && ("build".equals(dummyFileName.toString()) || "target".equals(dummyFileName.toString()))) {
                        projectDir = Optional.ofNullable(dummy.getParent());
                        put(MICRONAUT_PROCESSING_PROJECT_DIR, dummy.getParent());
                        break;
                    }
                    dummy = dummy.getParent();
                }
            }
        }

        return projectDir;
    }

    /**
     * Provide the Path to the annotation processing classes output directory, i.e. the parent of META-INF.
     *
     * <p>This might, for example, be used as a convenience for {@link TypeElementVisitor} classes to provide
     * relative path strings to {@link VisitorContext#addGeneratedResource(String)}</p>
     * <pre>
     * Path resource = ... // absolute path to the resource
     * visitorContext.getClassesOutputPath().ifPresent(path ->
     *     visitorContext.addGeneratedResource(path.relativize(resource).toString()));
     * </pre>
     *
     * @return Path pointing to the classes output directory
     */
    @Experimental
    default Optional<Path> getClassesOutputPath() {
        Optional<GeneratedFile> dummy = visitMetaInfFile("dummy");
        if (dummy.isPresent()) {
            // we want the parent directory of META-INF/dummy
            Path classesOutputDir = Paths.get(dummy.get().toURI()).getParent().getParent();
            return Optional.of(classesOutputDir);
        }
        return Optional.empty();
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

    /**
     * Provide a collection of generated classpath resources that other TypeElement visitors might want to consume.
     * The generated resources are intended to be strings paths relative to the classpath root.
     *
     * @return a possibly empty collection of resource paths
     */
    @Experimental
    default Collection<String> getGeneratedResources() {
        info("EXPERIMENTAL: Compile time resource contribution to the context is experimental", null);
        return Collections.emptyList();
    }

    /**
     * Some TypeElementVisitors generate classpath resources that other visitors might be interested in.
     * The generated resources are intended to be strings paths relative to the classpath root
     *
     * @param resource the relative path to add
     */
    @Experimental
    default void addGeneratedResource(String resource) {
        info("EXPERIMENTAL: Compile time resource contribution to the context is experimental", null);
    }
}
