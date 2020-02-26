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
package io.micronaut.cli.profile.commands.io

import groovy.transform.CompileStatic
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.io.support.DefaultResourceLoader
import io.micronaut.cli.io.support.FileSystemResource
import io.micronaut.cli.io.support.PathMatchingResourcePatternResolver
import io.micronaut.cli.io.support.Resource
import io.micronaut.cli.io.support.ResourceLoader
import io.micronaut.cli.io.support.ResourceLocator
import io.micronaut.cli.io.support.ResourceUtils
import io.micronaut.cli.io.support.SpringIOUtils
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.util.CliSettings

/**
 * Utility methods exposed to scripts for interacting with resources (found on the file system or jars) and the file system
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class FileSystemInteractionImpl implements FileSystemInteraction {

    ExecutionContext executionContext
    ResourceLoader resourceLoader
    PathMatchingResourcePatternResolver resourcePatternResolver
    ResourceLocator resourceLocator

    FileSystemInteractionImpl(ExecutionContext executionContext, ResourceLoader resourceLoader = new DefaultResourceLoader()) {
        this.executionContext = executionContext
        this.resourceLoader = resourceLoader
        this.resourceLocator = new ResourceLocator()
        this.resourceLocator.setSearchLocation(executionContext.baseDir.absolutePath)
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver(resourceLoader)
    }

    /**
     * Makes a directory
     *
     * @param path The path to the directory
     */
    @Override
    FileSystemInteractionImpl mkdir(path) {
        file(path)?.mkdirs()
        return this
    }

    /**
     * Deletes a file
     *
     * @param path The path to the file
     */
    @Override
    FileSystemInteractionImpl delete(path) {
        file(path)?.delete()
        return this
    }

    /**
     * Allows Gradle style simple copy specs
     *
     * @param callable The callable
     * @return this
     */
    @Override
    FileSystemInteractionImpl copy(@DelegatesTo(FileSystemInteraction.CopySpec) Closure callable) {
        FileSystemInteraction.CopySpec spec = new FileSystemInteraction.CopySpec()
        callable.delegate = spec
        callable.call()
        if (spec.from && spec.into) {
            if (spec.from instanceof Iterable) {
                copyAll((Iterable) spec.from, spec.into)
            } else {
                copy(spec.from, spec.into)
            }
        }
        return this
    }
    /**
     * Copies a resource to the target destination
     *
     * @param path The path
     * @param destination The destination
     */
    @Override
    FileSystemInteractionImpl copy(path, destination) {
        def from = resource(path)
        def to = file(destination)
        copy(from, to)
        return this
    }

    /**
     * Copies resources to the target destination
     *
     * @param path The path
     * @param destination The destination
     */
    @Override
    FileSystemInteractionImpl copyAll(Iterable resources, destination) {
        mkdir(destination)
        for (path in resources) {
            def from = resource(path)
            def to = file(destination)
            copy(from, to)
        }
        return this
    }

    /**
     * Copy a Resource from the given location to the given directory or location
     *
     * @param from The resource to copy
     * @param to The location to copy to
     * @return The {@FileSystemInteraction} instance
     */
    @Override
    FileSystemInteractionImpl copy(Resource from, File to) {
        if (!to?.exists()) mkdir(to)
        if (from && to) {
            if (to.isDirectory()) {
                mkdir(to)
                to = new File(to, from.filename)
            }
            SpringIOUtils.copy(from, to)
            MicronautConsole.instance.addStatus("Copied ${from.filename} to location ${to.canonicalPath}")
        }
        return this
    }

    /**
     * Obtain a file for the given path
     *
     * @param path The path
     * @return The file
     */
    @Override
    File file(Object path) {
        if (path instanceof File) return (File) path
        else if (path instanceof Resource) return ((Resource) path).file
        else {
            def baseDir = executionContext.baseDir
            new File(baseDir ?: new File("."), path.toString())
        }
    }

    /**
     * @return The target build directory
     */
    @Override
    File getBuildDir() {
        CliSettings.TARGET_DIR
    }

    /**
     * @return The directory where classes are compiled to
     */
    @Override
    File getClassesDir() {
        CliSettings.CLASSES_DIR
    }

    /**
     * Finds a source file for the given class name
     * @param className The class name
     * @return The source resource
     */
    @Override
    Resource source(String className) {
        resourceLocator.findResourceForClassName(className)
    }

    /**
     * The class name of the given resource
     *
     * @param resource The resource
     * @return The class name
     */
    String className(Resource resource) {
        ResourceUtils.getClassName(resource)
    }

    /**
     * Obtain a resource for the given path
     * @param path The path
     * @return The resource
     */
    @Override
    Resource resource(Object path) {
        if (!path) return null
        if (path instanceof Resource) return (Resource) path
        def f = file(path)
        if (f?.exists() && f.isFile()) {
            return new FileSystemResource(f)
        } else {
            def pathStr = path.toString()
            def resource = resourceLoader.getResource(pathStr)
            if (resource.exists()) {
                return resource
            } else {
                def allResources = resources(pathStr)
                if (allResources) {
                    return allResources[0]
                } else {
                    return resource
                }
            }
        }
    }

    /**
     * Obtain resources for the given pattern
     *
     * @param pattern The pattern
     * @return The resources
     */
    @Override
    Collection<Resource> resources(String pattern) {
        try {
            return resourcePatternResolver.getResources(pattern).toList()
        } catch (e) {
            return []
        }
    }

    /**
     * Obtain the path of the resource relative to the current project
     *
     * @param path The path to inspect
     * @return The relative path
     */
    @Override
    String projectPath(Object path) {
        def file = file(path)
        if (file) {
            def basePath = executionContext.baseDir.canonicalPath
            return (file.canonicalPath - basePath).substring(1)
        }
        return ""
    }

    /**
     * Get files matching the given pattern
     *
     * @param pattern The pattern
     * @return the files
     */
    @Override
    Collection<File> files(String pattern) {
        resources(pattern).collect() { Resource res -> res.file }
    }


}
