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
package org.particleframework.inject.qualifiers;

import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.core.naming.NameResolver;
import org.particleframework.core.util.StringUtils;
import org.particleframework.inject.BeanDefinition;

import javax.inject.Named;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A {@link org.particleframework.context.Qualifier} that uses {@link AnnotationMetadata}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class AnnotationMetadataQualifier<T> extends NameQualifier<T> {

    private final AnnotationMetadata annotationMetadata;

    AnnotationMetadataQualifier(AnnotationMetadata metadata, String name) {
        super(name);
        this.annotationMetadata = metadata;
    }

    @Override
    public Stream<BeanDefinition<T>> reduce(Class<T> beanType, Stream<BeanDefinition<T>> candidates) {
        String name;
        if (Named.class.getName().equals(getName())) {
            String v = annotationMetadata.getValue(Named.class, String.class).orElse(null);
            if(StringUtils.isNotEmpty(v)) {
                name = Character.toUpperCase(v.charAt(0)) + v.substring(1);
            }
            else {
                name = getName();
            }

        } else {
            name = getName();
        }

        return candidates.filter(candidate -> {
                    String candidateName;
                    if(candidate instanceof NameResolver) {
                        candidateName = ((NameResolver) candidate).resolveName().orElse(candidate.getType().getSimpleName());
                    }
                    else {
                        Optional<String> annotation = candidate.getValue(Named.class, String.class);
                        candidateName = annotation.orElse( candidate.getType().getSimpleName() );
                    }

                    if(candidateName.equalsIgnoreCase(name)) {
                        return true;
                    }
                    else {

                        String qualified = name + beanType.getSimpleName();
                        if(qualified.equals(candidateName)) {
                            return true;
                        }
                    }
                    return false;
                }
        );
    }

}
