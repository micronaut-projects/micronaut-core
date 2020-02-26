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
package io.micronaut.cli.profile.repository

import groovy.transform.CompileStatic
import io.micronaut.cli.profile.Profile
import org.eclipse.aether.artifact.DefaultArtifact

/**
 * A JAR file repository that resolves profiles from a static array of JAR file URLs
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class StaticJarProfileRepository extends AbstractJarProfileRepository {


    final URL[] urls

    StaticJarProfileRepository(ClassLoader parent, URL... urls) {
        this.urls = urls
        for (url in urls) {
            registerProfile(url, parent)
        }
    }

    Profile getProfile(String profileName) {
        def profile = super.getProfile(profileName)
        if (profile == null && profileName.contains(':')) {
            def art = new DefaultArtifact(profileName)
            profile = super.getProfile(art.artifactId)
        }
        return profile
    }
}
