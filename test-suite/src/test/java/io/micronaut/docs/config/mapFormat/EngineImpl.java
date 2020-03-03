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
package io.micronaut.docs.config.mapFormat;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

// tag::class[]
@Singleton
public class EngineImpl implements Engine {
    @Override
    public Map getSensors() {
        return config.getSensors();
    }

    public String start() {
        return "Engine Starting V" + getConfig().getCylinders() + " [sensors=" + getSensors().size() + "]";
    }

    public EngineConfig getConfig() {
        return config;
    }

    public void setConfig(EngineConfig config) {
        this.config = config;
    }

    @Inject
    private EngineConfig config;
}
// end::class[]