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

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationProperties;
import javax.validation.constraints.Min;
import java.util.Map;
import io.micronaut.core.convert.format.MapFormat;
// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine")
public class EngineConfig {
    public int getCylinders() {
        return cylinders;
    }

    public void setCylinders(int cylinders) {
        this.cylinders = cylinders;
    }

    public Map<Integer, String> getSensors() {
        return sensors;
    }

    public void setSensors(Map<Integer, String> sensors) {
        this.sensors = sensors;
    }

    @Min(1L)
    private int cylinders;
    @MapFormat(transformation = MapFormat.MapTransformation.FLAT) //<1>
    private Map<Integer, String> sensors;
}
// end::class[]
