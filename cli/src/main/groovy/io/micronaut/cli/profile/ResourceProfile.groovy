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

import groovy.transform.CompileStatic
import io.micronaut.cli.io.support.Resource

/**
 * A profile that operates against abstract {@link Resource} references
 *
 *
 * @since 1.0
 * @author Lari Hotari
 * @author Graeme Rocher
 */
@CompileStatic
class ResourceProfile extends AbstractProfile implements Profile {

    ResourceProfile(ProfileRepository repository, String name, Resource profileDir) {
        super(profileDir)
        super.name = name
        this.profileRepository = repository
        initialize()
    }

    @Override
    String getName() {
        super.name
    }

    @Override
    void setMainClassName(String mainClassName) {
        super.mainClassName = mainClassName
    }

    public static Profile create(ProfileRepository repository, String name, Resource profileDir) {
        Profile profile = new ResourceProfile(repository, name, profileDir)
        return profile
    }

    boolean equals(o) {
        if (this.is(o)) {
            return true
        }
        if (getClass() != o.class) {
            return false
        }

        ResourceProfile that = (ResourceProfile) o

        if (name != that.name) {
            return false
        }

        return true
    }

    int hashCode() {
        return (name != null ? name.hashCode() : 0)
    }
}
