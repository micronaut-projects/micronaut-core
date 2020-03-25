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
package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

import edu.umd.cs.findbugs.annotations.Nullable;

@EachProperty("someconf")
public class SomeConfiguration {

    private final MyConfiguration otherConfig;
    private final String nameQualifier;

    SomeConfiguration(@Nullable @Parameter MyConfiguration otherConfig,
                      @Parameter String name) {
        this.otherConfig = otherConfig;
        this.nameQualifier = name;
    }

    public MyConfiguration getOtherConfig() {
        return otherConfig;
    }

    public String getNameQualifier() {
        return nameQualifier;
    }
}
