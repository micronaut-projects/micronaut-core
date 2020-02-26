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
package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties("map")
public class MapProperties {

    Map<String, Object> field;

    private Map<String, Object> setter;

    public Map<String, Object> getSetter() {
        return setter;
    }

    public void setSetter(Map<String, Object> setter) {
        this.setter = setter;
    }
}
