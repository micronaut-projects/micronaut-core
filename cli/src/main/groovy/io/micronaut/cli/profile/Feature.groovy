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
package io.micronaut.cli.profile

import io.micronaut.cli.config.NavigableMap
import io.micronaut.cli.io.support.Resource
import org.eclipse.aether.graph.Dependency

/**
 * An interface that describes a feature of a profile. Different profiles may share many common features even if the profile itself is different.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
interface Feature {

    /**
     * @return The profile this feature belongs to
     */
    Profile getProfile()

    /**
     * @return The name of the feature
     */
    String getName()

    /**
     * @return The description of the profile
     */
    String getDescription()

    /**
     * @return The location of the feature
     */
    Resource getLocation()

    /**
     * @return The dependency definitions for this feature
     */
    List<Dependency> getDependencies()

    /**
     * @return The build plugin names
     */
    List<String> getBuildPlugins()


    /**
     * @return The JVM args
     */
    List<String> getJvmArgs()

    /**
     * @return The configuration for the feature
     */
    NavigableMap getConfiguration()

    /**
     * @return The dependent feature names
     */
    Iterable<Feature> getDependentFeatures(Profile profile)

    /**
     * @return The features that are evicted by this feature
     */
    Iterable<String> getEvictedFeatureNames()

    /**
     * @return The default feature names
     */
    Iterable<Feature> getDefaultFeatures(Profile profile)

    /**
     * @param Whether the feature was requested on the command line
     */
    void setRequested(Boolean requested)

    /**
     * @return Whether the feature was requested on the command line
     */
    Boolean getRequested()

    /**
     * @return The minimum required Java version
     */
    Integer getMinJavaVersion()

    /**
     * @return The maximum supported Java version
     */
    Integer getMaxJavaVersion()

    /**
     * @return Whether the feature is supported for a specific java version
     */
    boolean isSupported(Integer javaVersion)

}
