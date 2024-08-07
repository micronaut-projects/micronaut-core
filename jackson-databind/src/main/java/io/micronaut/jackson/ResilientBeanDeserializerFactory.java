/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.cfg.DeserializerFactoryConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerFactory;
import com.fasterxml.jackson.databind.deser.DeserializerFactory;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.impl.CreatorCollector;

/**
 * This class hooks {@link BeanDeserializerFactory} to fix an exception in {@link #_constructDefaultValueInstantiator}
 * that happens when jackson attempts to access record member info in a native-image without reflective access for the
 * record class.
 *
 * @author yawkat
 * @since 3.3.0
 */
final class ResilientBeanDeserializerFactory extends BeanDeserializerFactory {
    public ResilientBeanDeserializerFactory(DeserializerFactoryConfig config) {
        super(config);
    }

    @Override
    public DeserializerFactory withConfig(DeserializerFactoryConfig config) {
        return new ResilientBeanDeserializerFactory(config);
    }

    @Override
    protected ValueInstantiator _constructDefaultValueInstantiator(DeserializationContext ctxt, BeanDescription beanDesc) throws JsonMappingException {
        try {
            return super._constructDefaultValueInstantiator(ctxt, beanDesc);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().startsWith("Failed to access RecordComponents of type ")) {
                final DeserializationConfig config = ctxt.getConfig();
                return new CreatorCollectionState(ctxt, beanDesc, config.getDefaultVisibilityChecker(beanDesc.getBeanClass(), beanDesc.getClassInfo()),
                    new CreatorCollector(beanDesc, config), _findCreatorsFromProperties(ctxt, beanDesc)).creators.constructValueInstantiator(ctxt);
            }
            throw e;
        }
    }
}
