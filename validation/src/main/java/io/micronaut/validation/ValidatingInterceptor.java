/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.validation;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.validation.validator.ExecutableMethodValidator;
import io.micronaut.validation.validator.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.ValidatorFactory;
import javax.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link MethodInterceptor} that validates method invocations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class ValidatingInterceptor implements MethodInterceptor {

    /**
     * The position of the interceptor. See {@link io.micronaut.core.order.Ordered}
     */
    public static final int POSITION = InterceptPhase.VALIDATE.getPosition();

    private static final Logger LOG = LoggerFactory.getLogger(ValidatingInterceptor.class);

    private final @Nullable ExecutableValidator executableValidator;
    private final ExecutableMethodValidator micronautValidator;

    /**
     * Creates ValidatingInterceptor from the validatorFactory.
     *
     * @param validatorFactory Factory returning initialized {@code Validator} instances
     * @deprecated Use {@link #ValidatingInterceptor(Validator, ValidatorFactory)} instead
     */
    @Deprecated
    public ValidatingInterceptor(Optional<ValidatorFactory> validatorFactory) {
        this(Validator.getInstance(), validatorFactory.orElse(null));
    }

    /**
     * Creates ValidatingInterceptor from the validatorFactory.
     *
     * @param micronautValidator The micronaut validator use if no factory is available
     * @param validatorFactory Factory returning initialized {@code Validator} instances
     */
    @Inject
    public ValidatingInterceptor(
            Validator micronautValidator,
            @Nullable ValidatorFactory validatorFactory) {

        this.micronautValidator = micronautValidator.forExecutables();
        this.executableValidator = validatorFactory != null ? validatorFactory.getValidator().forExecutables() : null;
    }

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Override
    public Object intercept(MethodInvocationContext context) {
        final Object target = context.getTarget();
        if (executableValidator == null) {
            final ExecutableMethod executableMethod = context.getExecutableMethod();
            final Set<ConstraintViolation<Object>> constraintViolations = this.micronautValidator.validateParameters(
                    target,
                    executableMethod,
                    context.getParameterValues());
            if (constraintViolations.isEmpty()) {
                final Object result = context.proceed();
                this.micronautValidator.validateReturnValue(target, executableMethod, result);
                return result;
            } else {
                throw new ConstraintViolationException(constraintViolations);
            }
        } else {
            Method targetMethod = context.getTargetMethod();
            if (targetMethod.getParameterTypes().length == 0) {
                return context.proceed();
            } else {
                Set<ConstraintViolation<Object>> constraintViolations = executableValidator
                    .validateParameters(
                            target,
                        targetMethod,
                        context.getParameterValues()
                    );
                if (constraintViolations.isEmpty()) {
                    return context.proceed();
                } else {
                    throw new ConstraintViolationException(constraintViolations);
                }
            }

        }
    }
}
