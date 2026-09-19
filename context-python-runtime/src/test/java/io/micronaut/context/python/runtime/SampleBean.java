/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.python.runtime;

import java.util.List;

/**
 * The shape of a Python wrapper the generator tests generate metadata for.
 */
public class SampleBean {
    private final SampleDependency dependency;
    private final String value;
    private SampleDependency other;
    private List<SampleDependency> all;
    private String name;
    private int age;
    private final long id = 7;
    private boolean initialized;
    private boolean closed;

    public SampleBean(SampleDependency dependency, String value) {
        this.dependency = dependency;
        this.value = value;
    }

    public void setOther(SampleDependency other) {
        this.other = other;
    }

    public void setAll(List<SampleDependency> all) {
        this.all = all;
    }

    public void initialize() {
        initialized = true;
    }

    public boolean close() {
        closed = true;
        return closed;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public long getId() {
        return id;
    }

    public SampleDependency getDependency() {
        return dependency;
    }

    public String getValue() {
        return value;
    }

    public SampleDependency getOther() {
        return other;
    }

    public List<SampleDependency> getAll() {
        return all;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
