/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.micronaut.discovery.eureka.client.v2;

import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.micronaut.core.annotation.ReflectiveAccess;

/**
 * A simple interface for indicating which <em>datacenter</em> a particular instance belongs.
 *
 * @author Karthik Ranganathan
 */
@JsonRootName("dataCenterInfo")
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = As.PROPERTY, property = "@class")
@JsonTypeIdResolver(DataCenterTypeInfoResolver.class)
public interface DataCenterInfo {

    /**
     * Different data centers related to the instances.
     */
    enum Name {
        Netflix,
        Amazon,
        MyOwn
    }

    /**
     * @return The name
     */
    @ReflectiveAccess
    Name getName();
}
