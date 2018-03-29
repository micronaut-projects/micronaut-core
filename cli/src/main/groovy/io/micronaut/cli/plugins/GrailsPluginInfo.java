/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.cli.plugins;

import java.util.Map;

import io.micronaut.cli.io.support.Resource;

/**
 * Base interface that just contains information about a particular plugin.
 * @author Graeme Rocher
 * @since 1.3
 */
public interface GrailsPluginInfo {

    /**
     * Defines the convention that appears within plugin class names
     */
    String TRAILING_NAME = "GrailsPlugin";

    /**
     * The name of the plugin
     */
    String NAME = "name";

    /**
     * Defines the name of the property that specifies the plugin version
     */
    String VERSION = "version";

    /**
     * @return The name of the plug-in
     */
    String getName();

    /**
     * @return The version of the plug-in
     */
    String getVersion();

    /**
     * @return The full name of the plugin including version
     */
    String getFullName();

    /**
     * Returns the location of the Resource that represents the plugin descriptor (the *GrailsPlugin.groovy file)
     * @return The resource
     */
    Resource getDescriptor();

    /**
     * @return The directory where the plugin exists or null if it cannot be read
     */
    Resource getPluginDir();

    /**
     * Gets the properties of the plugin as a map
     * @return A map of the properties
     */
    @SuppressWarnings("rawtypes")
    Map getProperties();
}
