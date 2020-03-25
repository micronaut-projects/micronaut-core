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
package io.micronaut.cli.io.support

import io.micronaut.cli.profile.Feature
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileRepository
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.Exclusion
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector

abstract class BuildTokens {
    final String sourceLanguage, testFramework, appname

    BuildTokens(String appname, String sourceLanguage, String testFramework) {
        this.sourceLanguage = sourceLanguage
        this.testFramework = testFramework
        this.appname = appname
    }

    abstract Map getTokens(ProfileRepository profileRepository, Profile profile, List<Feature> features)

    protected List<Dependency> materializeDependencies(Profile profile, List<Feature> features) {
        List<Dependency> profileDependencies = profile.dependencies
        def dependencies = profileDependencies.findAll() { Dependency dep ->
            dep.scope != 'build' && dep.scope != 'excludes'
        }

        for (Feature f in features) {
            List<Dependency> excludes = f.dependencies.findAll() { Dependency dep -> dep.scope == 'excludes' }
            if (excludes) {

                ExclusionDependencySelector selector = new ExclusionDependencySelector(
                        excludes.collect() { Dependency d ->
                            def artifact = d.artifact
                            new Exclusion(artifact.groupId, artifact.artifactId, null, null)
                        }
                )
                dependencies.removeIf({ Dependency d ->
                    !selector.selectDependency(d)
                })
            }
            dependencies.addAll f.dependencies.findAll() { Dependency dep ->
                dep.scope != 'build' && dep.scope != 'excludes'
            }
        }

        dependencies = dependencies.unique()
        dependencies
    }

    abstract Map getTokens(List<String> services)
}
