/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.annotation.AnnotationMetadataSupport;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static io.micronaut.context.BeanDefinitionDescriptorData.MEMBERSHIP_ANNOTATION;
import static io.micronaut.context.BeanDefinitionDescriptorData.MEMBERSHIP_DECLARED_ANNOTATION;
import static io.micronaut.context.BeanDefinitionDescriptorData.MEMBERSHIP_DECLARED_STEREOTYPE;
import static io.micronaut.context.BeanDefinitionDescriptorData.MEMBERSHIP_STEREOTYPE;

/**
 * The annotation metadata of a {@link LazyBeanDefinitionReference}. It answers from the descriptor:
 *
 * <ul>
 *     <li>whether the bean has an annotation or a stereotype, declared or not, by name or by class, where a class
 *     that is repeatable is looked up by its container, as the generated metadata does;</li>
 *     <li>the container of a repeatable annotation, from the registry every descriptor's containers were added to;</li>
 *     <li>the values of an annotation the bean does not have, which are none;</li>
 *     <li>the members a qualifier the bean declares gives, and none of the others, which take defaults the descriptor
 *     does not carry.</li>
 * </ul>
 *
 * <p>Every other method loads the definition and answers from its metadata, through
 * {@link AnnotationMetadataDelegate}. The methods {@link AnnotationMetadataDelegate} leaves to the defaults of
 * {@link AnnotationMetadata} are declared here, so that no default answers for a definition.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 5.3.0
 */
final class DescriptorAnnotationMetadata implements AnnotationMetadataDelegate {

    private static final int DECLARED = MEMBERSHIP_DECLARED_ANNOTATION | MEMBERSHIP_DECLARED_STEREOTYPE;
    private static final int ANY = MEMBERSHIP_ANNOTATION | MEMBERSHIP_STEREOTYPE | DECLARED;

    private final LazyBeanDefinitionReference<?> reference;
    private final BeanDefinitionDescriptorData data;

    DescriptorAnnotationMetadata(LazyBeanDefinitionReference<?> reference) {
        this.reference = reference;
        this.data = reference.data;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return reference.loadedMetadata("its annotation metadata");
    }

    // --- presence

    @Override
    public boolean hasAnnotation(@Nullable String annotation) {
        boolean answered = data.has(annotation, MEMBERSHIP_ANNOTATION);
        if (reference.answers != null) {
            record("hasAnnotation(" + annotation + ")", answered, m -> m.hasAnnotation(annotation));
        }
        return answered;
    }

    @Override
    public boolean hasDeclaredAnnotation(@Nullable String annotation) {
        boolean answered = data.has(annotation, MEMBERSHIP_DECLARED_ANNOTATION);
        if (reference.answers != null) {
            record("hasDeclaredAnnotation(" + annotation + ")", answered, m -> m.hasDeclaredAnnotation(annotation));
        }
        return answered;
    }

    @Override
    public boolean hasStereotype(@Nullable String annotation) {
        boolean answered = data.has(annotation, MEMBERSHIP_STEREOTYPE);
        if (reference.answers != null) {
            record("hasStereotype(" + annotation + ")", answered, m -> m.hasStereotype(annotation));
        }
        return answered;
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable String annotation) {
        boolean answered = data.has(annotation, MEMBERSHIP_DECLARED_STEREOTYPE);
        if (reference.answers != null) {
            record("hasDeclaredStereotype(" + annotation + ")", answered, m -> m.hasDeclaredStereotype(annotation));
        }
        return answered;
    }

    @Override
    public boolean hasAnnotation(@Nullable Class<? extends Annotation> annotation) {
        if (annotation == null) {
            return false;
        }
        boolean answered = data.has(repeatable(annotation.getName()), MEMBERSHIP_ANNOTATION);
        if (reference.answers != null) {
            record("hasAnnotation(" + annotation.getName() + ".class)", answered, m -> m.hasAnnotation(annotation));
        }
        return answered;
    }

    @Override
    public boolean hasDeclaredAnnotation(@Nullable Class<? extends Annotation> annotation) {
        if (annotation == null) {
            return false;
        }
        boolean answered = data.has(repeatable(annotation.getName()), MEMBERSHIP_DECLARED_ANNOTATION);
        if (reference.answers != null) {
            record("hasDeclaredAnnotation(" + annotation.getName() + ".class)", answered, m -> m.hasDeclaredAnnotation(annotation));
        }
        return answered;
    }

    @Override
    public boolean hasStereotype(@Nullable Class<? extends Annotation> annotation) {
        if (annotation == null) {
            return false;
        }
        boolean answered = data.has(repeatable(annotation.getName()), MEMBERSHIP_STEREOTYPE);
        if (reference.answers != null) {
            record("hasStereotype(" + annotation.getName() + ".class)", answered, m -> m.hasStereotype(annotation));
        }
        return answered;
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable Class<? extends Annotation> stereotype) {
        if (stereotype == null) {
            return false;
        }
        boolean answered = data.has(repeatable(stereotype.getName()), MEMBERSHIP_DECLARED_STEREOTYPE);
        if (reference.answers != null) {
            record("hasDeclaredStereotype(" + stereotype.getName() + ".class)", answered, m -> m.hasDeclaredStereotype(stereotype));
        }
        return answered;
    }

    @Override
    public boolean hasStereotypeNonRepeating(Class<? extends Annotation> annotation) {
        return hasStereotype(annotation.getName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean hasStereotype(Class<? extends Annotation> @Nullable ... annotations) {
        if (annotations == null) {
            return false;
        }
        for (Class<? extends Annotation> annotation : annotations) {
            if (hasStereotype(annotation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasStereotype(String @Nullable [] annotations) {
        if (annotations == null) {
            return false;
        }
        for (String annotation : annotations) {
            if (hasStereotype(annotation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean hasDeclaredStereotype(Class<? extends Annotation> @Nullable ... annotations) {
        if (annotations == null) {
            return false;
        }
        for (Class<? extends Annotation> annotation : annotations) {
            if (hasDeclaredStereotype(annotation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable String... annotations) {
        if (annotations == null) {
            return false;
        }
        for (String annotation : annotations) {
            if (hasDeclaredStereotype(annotation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return annotationClass != null && hasAnnotation(annotationClass);
    }

    @Override
    public boolean isDeclaredAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return annotationClass != null && hasDeclaredAnnotation(annotationClass);
    }

    @Override
    public boolean isAnnotationPresent(String annotationName) {
        return annotationName != null && hasAnnotation(annotationName);
    }

    @Override
    public boolean isDeclaredAnnotationPresent(String annotationName) {
        return annotationName != null && hasDeclaredAnnotation(annotationName);
    }

    @Override
    public boolean isEmpty() {
        boolean answered = data.annotationNames.length == 0;
        if (reference.answers != null) {
            record("isEmpty()", answered, AnnotationMetadata::isEmpty);
        }
        return answered;
    }

    // --- repeatable annotations

    @Override
    public Optional<String> findRepeatableAnnotation(String annotation) {
        Optional<String> answered = data.annotationNames.length == 0 ? Optional.empty() : Optional.ofNullable(AnnotationMetadataSupport.getRepeatableAnnotation(annotation));
        if (reference.answers != null) {
            record("findRepeatableAnnotation(" + annotation + ")", answered, m -> m.findRepeatableAnnotation(annotation));
        }
        return answered;
    }

    @Override
    public Optional<String> findRepeatableAnnotation(Class<? extends Annotation> annotation) {
        return findRepeatableAnnotation(annotation.getName());
    }

    @Override
    public boolean isRepeatableAnnotation(String annotation) {
        return findRepeatableAnnotation(annotation).isPresent();
    }

    @Override
    public boolean isRepeatableAnnotation(Class<? extends Annotation> annotation) {
        return findRepeatableAnnotation(annotation.getName()).isPresent();
    }

    // --- values of absent annotations, and of the declared qualifiers

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(String annotation) {
        AnnotationValue<?> qualifier = data.qualifier(annotation);
        if (qualifier != null) {
            Optional<AnnotationValue<T>> answered = Optional.of(withDefaults(annotation, qualifier));
            if (reference.answers != null) {
                record("findAnnotation(" + annotation + ")", answered, m -> m.findAnnotation(annotation));
            }
            return answered;
        }
        if (isAbsent(annotation, ANY)) {
            if (reference.answers != null) {
                record("findAnnotation(" + annotation + ")", Optional.empty(), m -> m.findAnnotation(annotation));
            }
            return Optional.empty();
        }
        return getAnnotationMetadata().findAnnotation(annotation);
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(String annotation) {
        AnnotationValue<?> qualifier = data.qualifier(annotation);
        if (qualifier != null && data.has(annotation, DECLARED)) {
            Optional<AnnotationValue<T>> answered = Optional.of(withDefaults(annotation, qualifier));
            if (reference.answers != null) {
                record("findDeclaredAnnotation(" + annotation + ")", answered, m -> m.findDeclaredAnnotation(annotation));
            }
            return answered;
        }
        if (isAbsent(annotation, DECLARED)) {
            if (reference.answers != null) {
                record("findDeclaredAnnotation(" + annotation + ")", Optional.empty(), m -> m.findDeclaredAnnotation(annotation));
            }
            return Optional.empty();
        }
        return getAnnotationMetadata().findDeclaredAnnotation(annotation);
    }

    @Override
    public <T extends Annotation> @Nullable AnnotationValue<T> getAnnotation(String annotation) {
        return this.<T>findAnnotation(annotation).orElse(null);
    }

    @Override
    public <T extends Annotation> @Nullable AnnotationValue<T> getDeclaredAnnotation(String annotation) {
        return this.<T>findDeclaredAnnotation(annotation).orElse(null);
    }

    @Override
    public Map<CharSequence, Object> getValues(String annotation) {
        AnnotationValue<?> qualifier = data.qualifier(annotation);
        if (qualifier != null) {
            Map<CharSequence, Object> answered = Collections.unmodifiableMap(qualifier.getValues());
            if (reference.answers != null) {
                record("getValues(" + annotation + ")", answered, m -> m.getValues(annotation));
            }
            return answered;
        }
        if (isAbsent(annotation, ANY)) {
            if (reference.answers != null) {
                record("getValues(" + annotation + ")", Collections.emptyMap(), m -> m.getValues(annotation));
            }
            return Collections.emptyMap();
        }
        return getAnnotationMetadata().getValues(annotation);
    }

    @Override
    public Optional<String> stringValue(String annotation) {
        return stringValue(annotation, AnnotationMetadata.VALUE_MEMBER);
    }

    @Override
    public Optional<String> stringValue(String annotation, String member) {
        Optional<Object> raw = declaredValue(annotation, member);
        if (raw == null || raw.isPresent() && firstElement(raw.get()) == null) {
            return getAnnotationMetadata().stringValue(annotation, member);
        }
        Optional<String> answered = raw.map(DescriptorAnnotationMetadata::firstElement).map(Object::toString);
        if (reference.answers != null) {
            record("stringValue(" + annotation + ", " + member + ")", answered, m -> m.stringValue(annotation, member));
        }
        return answered;
    }

    @Override
    public String[] stringValues(String annotation) {
        return stringValues(annotation, AnnotationMetadata.VALUE_MEMBER);
    }

    @Override
    public String[] stringValues(String annotation, String member) {
        Optional<Object> raw = declaredValue(annotation, member);
        if (raw == null) {
            return getAnnotationMetadata().stringValues(annotation, member);
        }
        String[] answered = raw.isPresent() ? strings(raw.get()) : StringUtils.EMPTY_STRING_ARRAY;
        if (answered == null) {
            // a value of a kind the metadata turns into strings in its own way
            return getAnnotationMetadata().stringValues(annotation, member);
        }
        if (reference.answers != null) {
            record("stringValues(" + annotation + ", " + member + ")", answered, m -> m.stringValues(annotation, member));
        }
        return answered;
    }

    @Override
    public Optional<Class> classValue(String annotation) {
        return classValue(annotation, AnnotationMetadata.VALUE_MEMBER);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Optional<Class> classValue(String annotation, String member) {
        Optional<Object> raw = declaredValue(annotation, member);
        if (raw == null) {
            return getAnnotationMetadata().classValue(annotation, member);
        }
        Optional<Class> answered = Optional.empty();
        if (raw.isPresent()) {
            if (!(firstElement(raw.get()) instanceof AnnotationClassValue<?> classValue)) {
                return getAnnotationMetadata().classValue(annotation, member);
            }
            answered = Optional.ofNullable(reference.loadClass(classValue.getName()));
        }
        if (reference.answers != null) {
            record("classValue(" + annotation + ", " + member + ")", answered, m -> m.classValue(annotation, member));
        }
        return answered;
    }

    // --- the defaults of AnnotationMetadata that AnnotationMetadataDelegate does not delegate

    @Override
    public <E extends Enum<E>> EnumSet<E> enumValuesSet(String annotation, String member, Class<E> enumType) {
        return getAnnotationMetadata().enumValuesSet(annotation, member, enumType);
    }

    @Override
    public <E extends Enum<E>> EnumSet<E> enumValuesSet(Class<? extends Annotation> annotation, String member, Class<E> enumType) {
        return getAnnotationMetadata().enumValuesSet(annotation, member, enumType);
    }

    @Override
    public String toString() {
        return "Descriptor metadata of " + reference;
    }

    /**
     * The value of a member as the metadata of the definition holds it, when the descriptor knows it.
     *
     * @return The value, empty when the bean has no such annotation, or {@code null} when the descriptor does not tell
     */
    @SuppressWarnings({"java:S2789", "OptionalAssignedToNull"}) // null means the definition has to answer
    private @Nullable Optional<Object> declaredValue(String annotation, String member) {
        AnnotationValue<?> qualifier = data.qualifier(annotation);
        if (qualifier != null) {
            Object value = qualifier.getValues().get(member);
            if (value != null) {
                return Optional.of(value);
            }
            // a member left at its default has no value in the metadata either, but for the one Micronaut records
            return AnnotationUtil.NON_BINDING_ATTRIBUTE.equals(member) ? Optional.empty() : null;
        }
        return isAbsent(annotation, ANY) ? Optional.empty() : null;
    }

    /**
     * Whether the bean has no such annotation, under its name or under the name of its container.
     */
    private boolean isAbsent(String annotation, int memberships) {
        if (data.has(annotation, memberships)) {
            return false;
        }
        String container = AnnotationMetadataSupport.getRepeatableAnnotation(annotation);
        return container == null || !data.has(container, memberships);
    }

    private static String repeatable(String annotation) {
        String container = AnnotationMetadataSupport.getRepeatableAnnotation(annotation);
        return container != null ? container : annotation;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Annotation> AnnotationValue<T> withDefaults(String annotation, AnnotationValue<?> declared) {
        return new AnnotationValue<>(annotation, declared.getValues(), AnnotationMetadataSupport.getDefaultValuesOrNull(annotation));
    }

    /**
     * The value the metadata answers a single value with: the first element of an array.
     *
     * @return The value, or {@code null} for an empty array or an array of a primitive type, which the definition
     * answers for
     */
    private static @Nullable Object firstElement(Object value) {
        if (value instanceof Object[] array) {
            return array.length > 0 ? array[0] : null;
        }
        if (value.getClass().isArray()) {
            return null;
        }
        return value;
    }

    private static String @Nullable [] strings(Object value) {
        if (value instanceof String string) {
            return new String[]{string};
        }
        if (value instanceof String[] strings) {
            return strings.clone();
        }
        return null;
    }

    private void record(String what, Object answered, Function<AnnotationMetadata, Object> replay) {
        Map<String, LazyBeanDefinitionVerifier.Answer> answers = reference.answers;
        if (answers != null) {
            LazyBeanDefinitionVerifier.record(answers, what, answered, loaded -> replay.apply(loaded.getAnnotationMetadata()));
        }
    }
}
