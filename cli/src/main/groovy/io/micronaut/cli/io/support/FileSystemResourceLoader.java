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
package io.micronaut.cli.io.support;

/**
 * {@link ResourceLoader} implementation that resolves plain paths as
 * file system resources rather than as class path resources
 * (the latter is {@link DefaultResourceLoader}'s default strategy).
 *
 * <p><b>NOTE:</b> Plain paths will always be interpreted as relative
 * to the current VM working directory, even if they start with a slash.
 * (This is consistent with the semantics in a Servlet container.)
 * <b>Use an explicit "file:" prefix to enforce an absolute file path.</b>
 * <p>
 * is a full-fledged ApplicationContext implementation that provides
 * the same resource path resolution strategy.
 *
 * @author Juergen Hoeller
 * @see DefaultResourceLoader
 * @since 1.1.3
 */
public class FileSystemResourceLoader extends DefaultResourceLoader {

    /**
     * Resolve resource paths as file system paths.
     * <p>Note: Even if a given path starts with a slash, it will get
     * interpreted as relative to the current VM working directory.
     *
     * @param path the path to the resource
     * @return the corresponding Resource handle
     * @see FileSystemResource
     */
    @Override
    protected Resource getResourceByPath(String path) {
        if (path != null && path.startsWith("/")) {
            path = path.substring(1);
        }
        return new FileSystemResource(path);
    }
}
