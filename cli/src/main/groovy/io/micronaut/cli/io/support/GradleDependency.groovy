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

import org.eclipse.aether.graph.Dependency

class GradleDependency {

    static final Map<String, String> SCOPE_MAP = [
        compile: 'implementation',
        runtime: 'runtimeOnly',
        testRuntime: 'testRuntimeOnly',
        testCompile: 'testImplementation',
        provided: 'developmentOnly'
    ]

    private String scope
    private String dependency

    GradleDependency(String scope, String dependency) {
        this.scope = scope
        this.dependency = dependency
    }

    GradleDependency(Dependency dependency) {
        this(SCOPE_MAP.get(dependency.scope) ?: dependency.scope, dependency)
    }

    GradleDependency(String scope, Dependency dependency) {
        this.scope = scope
        def artifact = dependency.artifact
        def v = artifact.version.replace('BOM', '')
        StringBuilder artifactString = new StringBuilder()
        if (dependency.exclusions != null && !dependency.exclusions.empty) {
            artifactString.append('(')
        } else {
            artifactString.append(' ')
        }
        artifactString.append('"')
        artifactString.append(artifact.groupId)
        artifactString.append(':').append(artifact.artifactId)
        if (v) {
            artifactString.append(':').append(v)
        }
        artifactString.append('"')

        def ln = System.getProperty("line.separator")

        if (dependency.exclusions != null && !dependency.exclusions.empty) {
            artifactString.append(") {").append(ln)
            for (e in dependency.exclusions) {
                artifactString.append("    ")
                        .append("exclude")

                artifactString.append(" group: ").append('"').append(e.groupId).append('",')
                artifactString.append(" module: ").append('"').append(e.artifactId).append('"')

                artifactString.append(ln)
            }
            artifactString.append("}")
        }
        this.dependency = artifactString.toString()
    }

    String toString(int spaces) {
        (scope + dependency).replaceAll('(?m)^', ' ' * spaces)
    }

    String getScope() {
        scope
    }
}
