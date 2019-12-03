/*
 * Copyright 2017-2019 original authors
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

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.InheritConstructors
import groovy.transform.ToString
import io.micronaut.cli.config.NavigableMap
import io.micronaut.cli.io.support.Resource
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.Exclusion
import org.yaml.snakeyaml.Yaml

/**
 * Default implementation of the {@link Feature} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@InheritConstructors
@EqualsAndHashCode(includes = ['name'])
@ToString(includes = ['profile', 'name'])
@CompileStatic
class DefaultFeature implements Feature {
    final Profile profile
    final String name
    final String mainClassName
    final Resource location
    final NavigableMap configuration = new NavigableMap()
    final List<Dependency> dependencies = []
    final List<String> buildPlugins
    final List<String> jvmArgs
    final List<String> dependentFeatures = []
    final List<String> defaultFeatures = []
    final List<String> evictedFeatures = []
    private Boolean requested = false
    final Integer minJava
    final Integer maxJava

    DefaultFeature(Profile profile, String name, Resource location) {
        this.profile = profile
        this.name = name
        this.location = location
        def featureYml = location.createRelative("feature.yml")
        def featureConfig = (Map<String, Object>) new Yaml().loadAs(featureYml.getInputStream(), Map)
        configuration.merge(featureConfig)
        def dependencyMap = configuration.get("dependencies")
        Map featureMap = (Map) configuration.get("features", Collections.emptyMap())
        if (featureMap.containsKey("dependent")) {
            dependentFeatures.addAll((List) featureMap.get("dependent", Collections.emptyList()))
        }
        if (featureMap.containsKey("excludes")) {
            evictedFeatures.addAll((List) featureMap.get("excludes", Collections.emptyList()))
        }
        if (featureMap.containsKey("default")) {
            defaultFeatures.addAll((List) featureMap.get("default", Collections.emptyList()))
        }

        if (dependencyMap instanceof List) {

            for (entry in ((List) dependencyMap)) {
                if (entry instanceof Map) {
                    def scope = (String)entry.scope
                    def os = entry.os
                    if (os && !isSupportedOs(os.toString())) {
                        continue
                    }
                    String coords = (String)entry.coords

                    if (coords.count(':') == 1) {
                        coords = "$coords:BOM"
                    }
                    Dependency dependency = new Dependency(new DefaultArtifact(coords), scope.toString())
                    if (entry.containsKey('excludes')) {
                        List<Exclusion> dependencyExclusions = new ArrayList<>()
                        List excludes = (List)entry.excludes

                        for (ex in excludes) {
                            if (ex instanceof Map) {
                                dependencyExclusions.add(new Exclusion((String)ex.group, (String)ex.module, (String)ex.classifier, (String)ex.extension))
                            }
                        }
                        dependency = dependency.setExclusions(dependencyExclusions)
                    }
                    dependencies.add(dependency)
                }
            }
        }
        this.buildPlugins = (List<String>) configuration.get("build.plugins", [])
        this.jvmArgs = (List<String>) configuration.get("jvmArgs", [])

        this.minJava = (Integer) configuration.get("java.min") ?: null
        this.maxJava = (Integer) configuration.get("java.max") ?: null

        this.mainClassName = (String) configuration.get("mainClassName") ?: null
    }

    @Override
    String getMainClassName() {
        mainClassName
    }

    @Override
    String getDescription() {
        configuration.get("description", '').toString()
    }

    @Override
    Iterable<Feature> getDependentFeatures(io.micronaut.cli.profile.Profile profile) {
        profile.getFeatures().findAll { Feature f -> dependentFeatures.contains(f.name) }
    }

    @Override
    Iterable<String> getEvictedFeatureNames() {
        return evictedFeatures
    }

    @Override
    Iterable<Feature> getDefaultFeatures(io.micronaut.cli.profile.Profile profile) {
        profile.getFeatures().findAll { Feature f -> defaultFeatures.contains(f.name) }
    }

    @Override
    Integer getMinJavaVersion() {
        minJava
    }

    @Override
    Integer getMaxJavaVersion() {
        maxJava
    }

    @Override
    void setRequested(Boolean r) {
        requested = r
    }

    @Override
    Boolean getRequested() {
        requested
    }

    @Override
    boolean isSupported(Integer javaVersion) {
        if (minJavaVersion != null) {
            if (maxJavaVersion != null) {
                return javaVersion >= minJavaVersion && javaVersion <= maxJavaVersion
            } else {
                return javaVersion >= minJavaVersion
            }
        }
        if (maxJavaVersion != null) {
            return javaVersion <= maxJavaVersion
        }

        true
    }


    static boolean isSupportedOs(String os) {
        os = os.toLowerCase(Locale.ENGLISH).trim()
        String osName = System.getProperty("os.name")?.toLowerCase(Locale.ENGLISH) ?: "unix"
        switch (os) {
            case "windows":
                return osName.contains("windows")
            case "osx":
                return osName.contains("mac os x") || osName.contains("darwin") || osName.contains("osx")
            case "unix":
                return osName.contains("mac os x") || osName.contains("darwin") || osName.contains("osx") ||
                        osName.contains("sunos") || osName.contains("solaris") || osName.contains("linux") ||
                        osName.contains("freebsd")
            default:
                return false
        }
    }
}
