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
package io.micronaut.jackson.serialize;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.util.NameTransformer;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.hateoas.Resource;
import io.micronaut.jackson.modules.BeanIntrospectionModule;

import javax.inject.Singleton;
import java.util.Iterator;
import java.util.List;

/**
 * Modifies serialization for {@link Resource}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@Singleton
@Requires(missingBeans = BeanIntrospectionModule.class)
class ResourceSerializerModifier extends BeanSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
        if (Resource.class.isAssignableFrom(beanDesc.getBeanClass())) {
            Iterator<BeanPropertyWriter> i = beanProperties.iterator();
            BeanPropertyWriter links = null;
            BeanPropertyWriter embedded = null;
            while (i.hasNext()) {
                BeanPropertyWriter writer = i.next();
                String name = writer.getName();
                if (name.equals("links")) {
                    i.remove();
                    links = writer;
                }
                if (name.equals("embedded")) {
                    i.remove();
                    embedded = writer;
                }
            }
            if (embedded != null) {
                embedded = embedded.rename(new NameTransformer() {
                    @Override
                    public String transform(String name) {
                        return Resource.EMBEDDED;
                    }

                    @Override
                    public String reverse(String transformed) {
                        return transformed;
                    }
                });
                beanProperties.add(0, embedded);
            }
            if (links != null) {
                links = links.rename(new NameTransformer() {
                    @Override
                    public String transform(String name) {
                        return Resource.LINKS;
                    }

                    @Override
                    public String reverse(String transformed) {
                        return transformed;
                    }
                });
                beanProperties.add(0, links);
            }
            return beanProperties;
        } else {
            return super.changeProperties(config, beanDesc, beanProperties);
        }
    }
}
