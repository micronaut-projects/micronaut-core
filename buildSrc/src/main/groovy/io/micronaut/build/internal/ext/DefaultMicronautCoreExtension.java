/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.internal.ext;

import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;

public abstract class DefaultMicronautCoreExtension implements MicronautCoreExtension {
    private final DependencyHandler dependencyHandler;
    private final VersionCatalog libs;

    @Inject
    public DefaultMicronautCoreExtension(DependencyHandler dependencyHandler, VersionCatalogsExtension versionCatalogs) {
        this.dependencyHandler = dependencyHandler;
        this.libs = versionCatalogs.find("libs").get();
    }

    private static void excludeMicronautLibs(ExternalModuleDependency dep) {
        dep.exclude(module("micronaut-runtime"));
        dep.exclude(module("micronaut-inject"));
        dep.exclude(module("micronaut-bom"));
        dep.exclude(module("micronaut-core-bom"));
        dep.exclude(module("micronaut-platform"));
    }

    @Override
    public void usesMicronautTest() {
        addTestImplementationDependency("core");
    }

    @Override
    public void usesMicronautTestSpock() {
        addTestImplementationDependency("spock");
    }

    @Override
    public void usesMicronautTestJunit() {
        addTestImplementationDependency("junit5");
    }

    @Override
    public void usesMicronautTestKotest() {
        addTestImplementationDependency("kotest5");
    }

    private void addTestImplementationDependency(String lib) {
        dependencyHandler.addProvider("testImplementation", libs.findLibrary(
                "micronaut.test." + lib
        ).get(), DefaultMicronautCoreExtension::excludeMicronautLibs);
    }

    private static Map<String, String> module(String module) {
        return Collections.singletonMap("module", module);
    }
}
