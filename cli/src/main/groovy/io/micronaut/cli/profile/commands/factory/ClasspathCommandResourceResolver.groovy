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
package io.micronaut.cli.profile.commands.factory

import groovy.transform.CompileStatic
import io.micronaut.cli.io.support.PathMatchingResourcePatternResolver
import io.micronaut.cli.io.support.Resource
import io.micronaut.cli.profile.Profile

/**
 * A {@link CommandResourceResolver} that resolves commands from the classpath under the directory META-INF/commands
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ClasspathCommandResourceResolver implements CommandResourceResolver {
    final Collection<String> matchingFileExtensions
    ClassLoader classLoader

    private Collection<Resource> resources = null

    ClasspathCommandResourceResolver(Collection<String> matchingFileExtensions) {
        this.matchingFileExtensions = matchingFileExtensions
    }

    @Override
    Collection<Resource> findCommandResources(Profile profile) {
        if (resources != null) return resources
        def classLoader = classLoader ?: Thread.currentThread().contextClassLoader
        PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver(classLoader)

        try {
            resources = []
            for (String ext in matchingFileExtensions) {
                resources.addAll resourcePatternResolver.getResources("classpath*:META-INF/commands/*.$ext").toList()
            }
            return resources
        } catch (Throwable e) {
            return []
        }
    }
}
