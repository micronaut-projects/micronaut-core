/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.inject.qualifiers;

import static io.micronaut.core.util.ArgumentUtils.check;

import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.naming.NameResolver;
import io.micronaut.inject.BeanType;

import javax.inject.Named;
import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Qualifies using a name.
 *
 * @param <T> The type
 * @author Graeme Rocher
 * @since 1.0
 */
class NameQualifier<T> implements Qualifier<T>, io.micronaut.core.naming.Named {

    private final String name;

    /**
     * @param name The qualifier name
     */
    NameQualifier(String name) {
        this.name = Objects.requireNonNull(name, "Argument [name] cannot be null");
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        check("beanType", beanType).notNull();
        check("candidates", candidates).notNull();
        return candidates.filter(candidate -> {
                String typeName;
                AnnotationMetadata annotationMetadata = candidate.getAnnotationMetadata();
                // here we resolved the declared Qualifier of the bean
                Optional<Class<? extends Annotation>> qualifierType = annotationMetadata.getDeclaredAnnotationTypeByStereotype(javax.inject.Qualifier.class);
                Optional<String> beanQualifier = qualifierType.isPresent() && qualifierType.get() == Named.class ? annotationMetadata.getValue(Named.class, String.class) : Optional.empty();
                typeName = beanQualifier.orElseGet(() -> {
                    if (candidate instanceof NameResolver) {
                        Optional<String> resolvedName = ((NameResolver) candidate).resolveName();
                        return resolvedName.orElse(candidate.getBeanType().getSimpleName());
                    }
                    return candidate.getBeanType().getSimpleName();
                });
                return typeName.equalsIgnoreCase(name) || typeName.equalsIgnoreCase(name + beanType.getSimpleName());
            }
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !NameQualifier.class.isAssignableFrom(o.getClass())) {
            return false;
        }

        NameQualifier<?> that = (NameQualifier<?>) o;

        return name.equals(that.name);
    }

    @Override
    public String toString() {
        return "@Named('" + name + "')";
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * @param beanType       The bean type
     * @param candidates     The candidates
     * @param annotationName The annotation name
     * @param <BT>           Bean type
     * @return A stream
     */
    protected <BT extends BeanType<T>> Stream<BT> reduceByAnnotation(Class<T> beanType, Stream<BT> candidates, String annotationName) {
        return candidates.filter(candidate -> {
                String candidateName;
                if (candidate.isPrimary() && Primary.class.getSimpleName().equals(annotationName)) {
                    return true;
                }
                if (candidate instanceof NameResolver) {
                    candidateName = ((NameResolver) candidate).resolveName().orElse(candidate.getBeanType().getSimpleName());
                } else {
                    Optional<String> annotation = candidate.getAnnotationMetadata().getValue(Named.class, String.class);
                    candidateName = annotation.orElse(candidate.getBeanType().getSimpleName());
                }

                if (candidateName.equalsIgnoreCase(annotationName)) {
                    return true;
                } else {

                    String qualified = annotationName + beanType.getSimpleName();
                    if (qualified.equals(candidateName)) {
                        return true;
                    }
                }
                return false;
            }
        );
    }
}
