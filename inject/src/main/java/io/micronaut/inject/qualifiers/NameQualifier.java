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
package io.micronaut.inject.qualifiers;

import static io.micronaut.core.util.ArgumentUtils.check;

import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameResolver;
import io.micronaut.inject.BeanType;

import javax.inject.Named;
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
@Internal
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
            if (!beanType.isAssignableFrom(candidate.getBeanType())) {
                return false;
            }

            String typeName;
                AnnotationMetadata annotationMetadata = candidate.getAnnotationMetadata();
                // here we resolved the declared Qualifier of the bean
                Optional<String> beanQualifier = annotationMetadata
                        .findDeclaredAnnotation(Named.class)
                        .flatMap(AnnotationValue::stringValue);
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
     * @param <BT>           Bean type
     * @param beanType       The bean type
     * @param candidates     The candidates
     * @param annotationName The annotation name
     * @param qualifiedName The fully qualified name of the annotation
     * @return A stream
     */
    protected <BT extends BeanType<T>> Stream<BT> reduceByAnnotation(Class<T> beanType, Stream<BT> candidates, String annotationName, String qualifiedName) {
        return candidates.filter(candidate -> {
                String candidateName;
                if (candidate.isPrimary() && Primary.class.getSimpleName().equals(annotationName)) {
                    return true;
                }
                if (candidate instanceof NameResolver) {
                    candidateName = ((NameResolver) candidate).resolveName().orElse(candidate.getBeanType().getSimpleName());
                } else {
                    Optional<String> annotation = candidate.getAnnotationMetadata().stringValue(Named.class);
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
                return qualifiedName != null && candidate.getAnnotationMetadata().hasDeclaredAnnotation(qualifiedName);
            }
        );
    }

    /**
     * @param <BT>           Bean type
     * @param beanType       The bean type
     * @param candidates     The candidates
     * @param annotationName The annotation name
     * @return A stream
     */
    protected <BT extends BeanType<T>> Stream<BT> reduceByName(Class<T> beanType, Stream<BT> candidates, String annotationName) {
        return candidates.filter(candidate -> {
                    String candidateName;
                    if (candidate.isPrimary() && Primary.class.getSimpleName().equals(annotationName)) {
                        return true;
                    }
                    if (candidate instanceof NameResolver) {
                        candidateName = ((NameResolver) candidate).resolveName().orElse(candidate.getBeanType().getSimpleName());
                    } else {
                        Optional<String> annotation = candidate.getAnnotationMetadata().stringValue(Named.class);
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
