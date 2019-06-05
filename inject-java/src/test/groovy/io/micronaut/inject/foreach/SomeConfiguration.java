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
package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

import javax.annotation.Nullable;
import java.util.List;

@EachProperty("someconf")
public class SomeConfiguration {

    private final MyConfiguration otherConfig;
    private final List<NonBeanClass> otherBeans;
    private final String nameQualifier;

    SomeConfiguration(@Nullable @Parameter MyConfiguration otherConfig,
                      @Parameter List<NonBeanClass> otherBeans,
                      @Parameter String name) {
        this.otherConfig = otherConfig;
        this.otherBeans = otherBeans;
        this.nameQualifier = name;
    }

    public MyConfiguration getOtherConfig() {
        return otherConfig;
    }

    public List<NonBeanClass> getOtherBeans() {
        return otherBeans;
    }

    public String getNameQualifier() {
        return nameQualifier;
    }
}
