/*
 * Copyright 2017 original authors
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
package org.particleframework.web.router.resource;

import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.context.exceptions.ConfigurationException;
import org.particleframework.core.io.ResourceLoader;
import org.particleframework.core.io.file.FileSystemResourceLoader;
import org.particleframework.core.io.scan.ClassPathResourceLoader;
import org.particleframework.core.util.Toggleable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores configuration for the loading of static resources
 *
 * @author James Kleeh
 * @since 1.0
 */
@ConfigurationProperties(StaticResourceConfiguration.PREFIX)
public class StaticResourceConfiguration implements Toggleable {

    public static final String PREFIX = "router.static.resources";

    protected boolean enabled = false;
    protected List<String> paths = Collections.emptyList();
    protected String mapping = "/**";

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public List<ResourceLoader> getResourceLoaders() {
        List<ResourceLoader> loaders = new ArrayList<>(paths.size());
        if (enabled) {
            for(String path: paths) {
                if (path.startsWith("classpath:")) {
                    loaders.add(new ClassPathResourceLoader(this.getClass().getClassLoader(), path.substring(10)));
                } else if (path.startsWith("file:")) {
                    loaders.add(new FileSystemResourceLoader(new File(path.substring(5))));
                } else {
                    throw new ConfigurationException("Unrecognizable resource path: " + path);
                }
            }
        }
        return loaders;
    }
}
