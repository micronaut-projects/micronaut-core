/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.jackson.serialize;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.hateoas.Resource;
import io.micronaut.jackson.modules.BeanIntrospectionModule;

import java.util.List;

/**
 * @author graemerocher
 * @since 1.0
 */
@Internal
@Requires(missingBeans = BeanIntrospectionModule.class)
public class ResourceDeserializerModifier extends BeanDeserializerModifier {

    @Override
    public List<BeanPropertyDefinition> updateProperties(DeserializationConfig config, BeanDescription beanDesc, List<BeanPropertyDefinition> propDefs) {
        if (Resource.class.isAssignableFrom(beanDesc.getBeanClass())) {
            for (int i = 0; i < propDefs.size(); i++) {
                BeanPropertyDefinition definition = propDefs.get(i);
                if (definition.getName().equals("embedded")) {
                    propDefs.set(i, definition.withSimpleName("_embedded"));
                }
                if (definition.getName().equals("links")) {
                    propDefs.set(i, definition.withSimpleName("_links"));
                }
            }

            return propDefs;
        } else {
            return super.updateProperties(config, beanDesc, propDefs);
        }
    }
}
