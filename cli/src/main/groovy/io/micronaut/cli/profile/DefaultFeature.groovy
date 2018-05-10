/*
 * Copyright 2017-2018 original authors
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
import groovy.transform.ToString
import io.micronaut.cli.config.NavigableMap
import io.micronaut.cli.io.support.Resource
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.yaml.snakeyaml.Yaml

/**
 * Default implementation of the {@link Feature} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@EqualsAndHashCode(includes = ['name'])
@ToString(includes = ['profile', 'name'])
@CompileStatic
class DefaultFeature implements Feature {
    final Profile profile
    final String name
    final Resource location
    final NavigableMap configuration = new NavigableMap()
    final List<Dependency> dependencies = []
    final List<String> buildPlugins
    final List<String> dependentFeatures = []

    DefaultFeature(Profile profile, String name, Resource location) {
        this.profile = profile
        this.name = name
        this.location = location
        def featureYml = location.createRelative("feature.yml")
        def featureConfig = (Map<String, Object>) new Yaml().loadAs(featureYml.getInputStream(), Map)
        configuration.merge(featureConfig)
        def dependencyMap = configuration.get("dependencies")
        dependentFeatures.addAll((List) configuration.get("dependentFeatures", Collections.emptyList()))

        if (dependencyMap instanceof Map) {
            for (entry in ((Map) dependencyMap)) {
                def scope = entry.key
                def value = entry.value
                if (value instanceof List) {
                    for (dep in ((List) value)) {
                        String coords = dep.toString()
                        if (coords.count(':') == 1) {
                            coords = "$coords:BOM"
                        }
                        dependencies.add new Dependency(new DefaultArtifact(coords), scope.toString())
                    }
                }
            }
        }
        this.buildPlugins = (List<String>) configuration.get("build.plugins", [])
    }

    @Override
    String getDescription() {
        configuration.get("description", '').toString()
    }

    @Override
    Iterable<Feature> getDependentFeatures(io.micronaut.cli.profile.Profile profile) {
        profile.getFeatures().findAll { Feature f -> dependentFeatures.contains(f.name) }
    }
}
