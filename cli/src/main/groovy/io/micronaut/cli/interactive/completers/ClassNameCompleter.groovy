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
package io.micronaut.cli.interactive.completers

import groovy.transform.CompileStatic
import io.micronaut.cli.io.support.PathMatchingResourcePatternResolver
import io.micronaut.cli.io.support.Resource

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentSkipListSet

/**
 * A completer that completes class names
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ClassNameCompleter extends StringsCompleter {

    private static Map<String, SortedSet<String>> RESOURCE_SCAN_CACHE = [:]
    private static Collection<ClassNameCompleter> allCompleters = new ConcurrentLinkedQueue<>()
    PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver()

    private Map<File, String> baseDirs


    ClassNameCompleter(Map<File, String> baseDirs) {
        initialize(baseDirs)
    }

    static void refreshAll() {
        Thread.start {
            RESOURCE_SCAN_CACHE.clear()
            Collection<ClassNameCompleter> competers = new ArrayList<>(allCompleters)
            for (ClassNameCompleter completer : competers) {
                completer.refresh()
            }
        }
    }

    private void refresh() {
        if (!baseDirs) return
        initialize(baseDirs)
    }

    private void initialize(Map<File, String> baseDirs) {
        try {
            if (!baseDirs) return
            this.baseDirs = baseDirs
            if (!allCompleters.contains(this))
                allCompleters << this
            SortedSet<String> allStrings = new ConcurrentSkipListSet<>()
            for (Map.Entry<File, String> baseDir in baseDirs.entrySet()) {
                def pattern = "file:${baseDir.key}/${baseDir.value}".toString()
                SortedSet<String> strings = RESOURCE_SCAN_CACHE[pattern]
                if (strings == null) {
                    strings = new TreeSet<>()
                    RESOURCE_SCAN_CACHE[pattern] = strings
                    def resources = resourcePatternResolver.getResources(pattern)
                    for (res in resources) {
                        if (isValidResource(res)) {
                            def path = res.file.canonicalPath
                            def basePath = baseDir.key.canonicalPath
                            path = (path - basePath)[1..-8]
                            path = path.replace(File.separatorChar, '.' as char)
                            strings << path
                        }
                    }
                }
                allStrings.addAll(strings)
            }
            setStrings(allStrings)
        } catch (Throwable e) {
            // ignore
        }
    }

    boolean isValidResource(Resource resource) {
        true
    }
}
