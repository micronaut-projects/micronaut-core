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
package io.micronaut.validation.validator;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.annotation.AnnotatedElementValidator;
import io.micronaut.inject.qualifiers.TypeArgumentQualifier;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.DefaultConstraintValidators;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link AnnotatedElementValidator}. Used for discovery via
 * service loader and not for direct public consumption. Considered internal.
 *
 * @author graemerocher
 * @since 1.2
 */
@Internal
public class DefaultAnnotatedElementValidator extends DefaultValidator implements AnnotatedElementValidator {


    /**
     * Default constructor.
     */
    public DefaultAnnotatedElementValidator() {
        super(new DefaultValidatorConfiguration()
                    .setConstraintValidatorRegistry(new LocalConstraintValidators()));
    }

    /**
     * Local constraint validator lookup using service loader.
     */
    private static class LocalConstraintValidators extends DefaultConstraintValidators {

        private Map<ValidatorKey, ConstraintValidator> validatorMap;

        @Override
        protected <A extends Annotation, T> Optional<ConstraintValidator> findLocalConstraintValidator(@NonNull Class<A> constraintType, @NonNull Class<T> targetType) {
            return findConstraintValidatorFromServiceLoader(constraintType, targetType);
        }

        private <A extends Annotation, T> Optional<ConstraintValidator> findConstraintValidatorFromServiceLoader(Class<A> constraintType, Class<T> targetType) {
            if (validatorMap == null) {
                validatorMap = initializeValidatorMap();
            }
            return validatorMap.entrySet().stream()
                    .filter(entry -> {
                                final ValidatorKey key = entry.getKey();
                                final Class[] left = {constraintType, targetType};
                                return TypeArgumentQualifier.areTypesCompatible(
                                        left,
                                        Arrays.asList(key.getConstraintType(), key.getTargetType())
                                );
                            })
                    .findFirst().map(Map.Entry::getValue);
        }

        private Map<ValidatorKey, ConstraintValidator> initializeValidatorMap() {
            validatorMap = new HashMap<>();
            final SoftServiceLoader<ConstraintValidator> constraintValidators = SoftServiceLoader.load(ConstraintValidator.class);
            for (ServiceDefinition<ConstraintValidator> constraintValidator : constraintValidators) {
                if (constraintValidator.isPresent()) {
                    try {
                        final ConstraintValidator validator = constraintValidator.load();
                        final Class[] typeArgs = GenericTypeUtils.resolveInterfaceTypeArguments(validator.getClass(), ConstraintValidator.class);
                        if (ArrayUtils.isNotEmpty(typeArgs) && typeArgs.length == 2) {
                            validatorMap.put(
                                    new ValidatorKey(typeArgs[0], typeArgs[1]),
                                    validator
                            );
                        }
                    } catch (Exception e) {
                        // as this will occur in the compiler, we print a warning and not log it
                        System.err.println("WARNING: Could not validator [" + constraintValidator.getName() + "]: " + e.getMessage());
                    }
                }
            }

            return validatorMap;
        }
    }
}
