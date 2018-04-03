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
package io.micronaut.gradle.profile

import io.micronaut.gradle.publish.MicronautCentralPublishGradlePlugin
import org.gradle.api.Project

/**
 * A plugin for publishing profiles
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class MicronautProfilePublishGradlePlugin extends MicronautCentralPublishGradlePlugin {


    @Override
    protected String getDefaultReleaseRepo() {
        "https://repo.micronaut.io/artifactory/profiles-releases"
    }

    @Override
    protected String getDefaultSnapshotRepo() {
        "https://repo.micronaut.io/artifactory/profiles-snapshots"
    }

    @Override
    protected Map<String, String> getDefaultExtraArtifact(Project project) {
        [source: "${project.buildDir}/classes/profile/META-INF/profile/profile.yml".toString(),
         classifier: defaultClassifier,
         extension : 'yml']
    }

    @Override
    protected String getDefaultClassifier() {
        'profile'
    }

    @Override
    protected String getDefaultRepo() {
        'profiles'
    }
}
