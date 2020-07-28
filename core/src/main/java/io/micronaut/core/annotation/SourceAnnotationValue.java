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
package io.micronaut.core.annotation;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.ArgumentUtils;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A runtime representation of the an stereotype annotation and its values.
 *
 * <p>This class implements the {@link AnnotationValueResolver} interface and methods such as {@link AnnotationValueResolver#get(CharSequence, Class)} can be used to retrieve the values of annotation members.</p>
 *
 * <p>This this class implements a list of {@link AnnotationValue} for parent stereotypes</p>
 *
 * <p>If a member is not present then the methods of the class will attempt to resolve the default value for a given annotation member. In this sense the behaviour of this class is similar to how
 * a implementation of {@link Annotation} behaves.</p>
 *
 * @param <A> The annotation type
 * @author Michael Pollind
 * @since 1.0
 */
public class SourceAnnotationValue<A extends Annotation> extends AnnotationValue<A> {
    private final List<AnnotationValue<? extends Annotation>> stereotypes;

    /**
     * @param annotationName the annotation name
     * @param values         the values
     */
    public SourceAnnotationValue(String annotationName, Map<CharSequence, Object> values) {
        this(annotationName, values, Collections.emptyList());
    }

    /**
     * @param annotationName the annotation name
     * @param values         the values
     * @param stereotypes    annotation stereotypes
     */
    public SourceAnnotationValue(String annotationName, Map<CharSequence, Object> values, List<AnnotationValue<? extends Annotation>> stereotypes) {
        super(annotationName, values);
        ArgumentUtils.requireNonNull("stereotypes", stereotypes);
        this.stereotypes = stereotypes;
    }

    /**
     * @param annotationName  The annotation name
     * @param values          The values
     * @param retentionPolicy The retention policy
     */
    public SourceAnnotationValue(String annotationName, Map<CharSequence, Object> values, RetentionPolicy retentionPolicy) {
        this(annotationName, values, Collections.emptyList(), retentionPolicy);
    }

    /**
     * @param annotationName  The annotation name
     * @param values          The values
     * @param stereotypes     Annotation stereotypes
     * @param retentionPolicy The retention policy
     */
    public SourceAnnotationValue(String annotationName, Map<CharSequence, Object> values, List<AnnotationValue<? extends Annotation>> stereotypes, RetentionPolicy retentionPolicy) {
        super(annotationName, values, retentionPolicy);
        ArgumentUtils.requireNonNull("stereotypes", stereotypes);
        this.stereotypes = stereotypes;
    }

    /**
     * @param annotationName The annotation name
     * @param values         The values
     * @param defaultValues  The default values
     */
    public SourceAnnotationValue(String annotationName, Map<CharSequence, Object> values, Map<String, Object> defaultValues) {
        this(annotationName, values, defaultValues, Collections.emptyList());
    }

    /**
     * @param annotationName The annotation name
     * @param values         The values
     * @param defaultValues  The default values
     * @param stereotypes    The stereotypes
     */
    public SourceAnnotationValue(String annotationName, Map<CharSequence, Object> values, Map<String, Object> defaultValues, List<AnnotationValue<? extends Annotation>> stereotypes) {
        super(annotationName, values, defaultValues);
        ArgumentUtils.requireNonNull("stereotypes", stereotypes);
        this.stereotypes = stereotypes;
    }

    /**
     * @param annotationName  The annotation name
     * @param values          The values
     * @param defaultValues   The default values
     * @param retentionPolicy The retention policy
     */
    public SourceAnnotationValue(String annotationName, Map<CharSequence, Object> values, Map<String, Object> defaultValues, RetentionPolicy retentionPolicy) {
        this(annotationName, values, defaultValues, Collections.emptyList(), retentionPolicy);
    }

    /**
     * @param annotationName  The annotation name
     * @param values          The values
     * @param defaultValues   The default values
     * @param stereotypes     The stereotypes
     * @param retentionPolicy The retention policy
     */
    public SourceAnnotationValue(String annotationName, Map<CharSequence, Object> values, Map<String, Object> defaultValues, List<AnnotationValue<? extends Annotation>> stereotypes, RetentionPolicy retentionPolicy) {
        super(annotationName, values, defaultValues, retentionPolicy);
        ArgumentUtils.requireNonNull("stereotypes", stereotypes);
        this.stereotypes = stereotypes;
    }

    /**
     * @param annotationName The annotation name
     */
    public SourceAnnotationValue(String annotationName) {
        super(annotationName);
        this.stereotypes = Collections.emptyList();
    }

    /**
     * @param annotationName    The annotation name
     * @param convertibleValues The convertible values
     */
    public SourceAnnotationValue(String annotationName, ConvertibleValues<Object> convertibleValues) {
        super(annotationName, convertibleValues);
        this.stereotypes = Collections.emptyList();
    }

    /**
     * @param target            The target
     * @param defaultValues     The default values
     * @param convertibleValues The convertible values
     * @param valueMapper       The value mapper
     */
    protected SourceAnnotationValue(AnnotationValue<A> target, Map<String, Object> defaultValues, ConvertibleValues<Object> convertibleValues, Function<Object, Object> valueMapper) {
        super(target, defaultValues, convertibleValues, valueMapper);
        this.stereotypes = Collections.emptyList();
    }

    /**
     * The annotation stereotypes associated with annotation.
     *
     * @return annotation stereotypes
     */
    public @NonNull
    final List<AnnotationValue<? extends Annotation>> getStereotypes() {
        return this.stereotypes;
    }
}
