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

class OneOfFeatureGroup {

    String name
    List<Map> oneOfFeaturesData
    List<OneOfFeature> oneOfFeatures

    private boolean initialized = false

    void initialize(List<Feature> features) {
        if (!initialized) {
            synchronized (this) {
                oneOfFeatures = []

                oneOfFeaturesData.each { Map map ->
                    Feature f = features.find { it.name == map.name }
                    if (f) {
                        oneOfFeatures.add(new OneOfFeature(feature: f, priority: (Integer) map.priority))
                    }
                }
                oneOfFeatures.sort { a, b -> a.priority <=> b.priority}
            }

            initialized = true
        }
    }
}
