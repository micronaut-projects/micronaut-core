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
 * Loads relative to a static base resource.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class StaticResourceLoader implements ResourceLoader {

    private Resource baseResource;

    /**
     * @param baseResource The base resource
     */
    public StaticResourceLoader(Resource baseResource) {
        this.baseResource = baseResource;
    }

    @Override
    public Resource getResource(String location) {
        return baseResource.createRelative(location);
    }

    @Override
    public ClassLoader getClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }
}

