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
package io.micronaut.discovery.eureka.client.v2;

import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.ClassNameIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.micronaut.core.annotation.Introspected;

import java.io.IOException;

/**
 * Forked from original Netflix code.
 *
 * @author Tomasz Bak
 */
@Introspected
class DataCenterTypeInfoResolver extends ClassNameIdResolver {

    /**
     * This phantom class name is kept for backwards compatibility. Internally it is mapped to
     * {@link MyDataCenterInfo} during the deserialization process.
     */
    public static final String MY_DATA_CENTER_INFO_TYPE_MARKER = "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo";

    /**
     * Default constructor.
     */
    public DataCenterTypeInfoResolver() {
        super(TypeFactory.defaultInstance().constructType(DataCenterInfo.class), TypeFactory.defaultInstance());
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) throws IOException {
        if (MY_DATA_CENTER_INFO_TYPE_MARKER.equals(id)) {
            return context.getTypeFactory().constructType(MyDataCenterInfo.class);
        }
        return super.typeFromId(context, id);
    }

    @Override
    public String idFromValue(Object value) {
        if (value.getClass().getSimpleName().equals(AmazonInfo.class.getSimpleName())) {
            return AmazonInfo.class.getName();
        }
        return MY_DATA_CENTER_INFO_TYPE_MARKER;
    }
}
