/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty;

import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.type.Argument;
import org.particleframework.http.HttpParameters;

import java.io.IOException;
import java.util.*;

/**
 * Implementation of {@link HttpParameters} for Netty
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NettyHttpParameters implements HttpParameters {
    private final Map<String, List<String>> parameters;
    private final ConversionService conversionService;

    public NettyHttpParameters(Map<String, List<String>> parameters, ConversionService conversionService) {
        this.parameters = new LinkedHashMap<>(parameters);
        this.conversionService = conversionService;
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        List<String> values = getAll(name);
        if(!values.isEmpty()) {
            String value = values.get(0);
            return conversionService.convert(value, requiredType);
        }
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Argument<T> requiredType) {
        List<String> values = getAll(name);
        if(!values.isEmpty()) {
            String value = values.get(0);
            return conversionService.convert(value, requiredType.getType(), ConversionContext.of(requiredType));
        }
        return Optional.empty();
    }

    @Override
    public Set<String> getNames() {
        return Collections.unmodifiableSet(parameters.keySet());
    }

    @Override
    public List<String> getAll(CharSequence name) {
        List<String> value = parameters.get(name.toString());
        if(value != null) {
            return Collections.unmodifiableList(value);
        }
        else {
            return Collections.emptyList();
        }
    }

    @Override
    public String get(CharSequence name) {
        List<String> all = getAll(name);
        if(all.isEmpty()) {
            return null;
        }
        return all.get(0);
    }

    @Internal
    void setPostRequestDecoder(HttpPostRequestDecoder postRequestDecoder) {
        if(postRequestDecoder != null) {

            List<InterfaceHttpData> bodyHttpDatas = postRequestDecoder.getBodyHttpDatas();
            for (InterfaceHttpData bodyHttpData : bodyHttpDatas) {
                switch (bodyHttpData.getHttpDataType()) {
                    case Attribute:
                        Attribute attribute = (Attribute) bodyHttpData;
                        String name = attribute.getName();
                        try {
                            if(parameters.containsKey(name)) {
                                parameters.get(name).add(attribute.getValue());
                            }
                            else {
                                parameters.put(name, Collections.singletonList(attribute.getValue()));
                            }
                        } catch (IOException ioe) {
                            // ignore
                        }
                        break;
                }
            }
        }
    }
}
