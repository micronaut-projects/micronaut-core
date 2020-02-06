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
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.validation.validator.ExecutableMethodValidator;
import io.micronaut.validation.validator.ReactiveValidator;
import io.micronaut.validation.validator.Validator;
import org.reactivestreams.Publisher;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Constraint;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.ValidatorFactory;
import javax.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.CompletionStage;

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

    private final @Nullable ExecutableValidator executableValidator;
    private final @Nullable ExecutableMethodValidator micronautValidator;

    /**
     * Creates ValidatingInterceptor from the validatorFactory.
     *
     * @param micronautValidator The micronaut validator use if no factory is available
     * @param validatorFactory   Factory returning initialized {@code Validator} instances
     */
    @Inject
    public ValidatingInterceptor(@Nullable Validator micronautValidator,
                                 @Nullable ValidatorFactory validatorFactory) {

        if (validatorFactory != null) {
            javax.validation.Validator validator = validatorFactory.getValidator();
            if (validator instanceof Validator) {
                this.micronautValidator = (ExecutableMethodValidator) validator;
                this.executableValidator = null;
            } else {
                this.micronautValidator = null;
                this.executableValidator = validator.forExecutables();
            }
        } else if (micronautValidator != null) {
            this.micronautValidator = micronautValidator.forExecutables();
            this.executableValidator = null;
        } else {
            this.micronautValidator = null;
            this.executableValidator = null;
        }
    }

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Override
    public Object intercept(MethodInvocationContext context) {
        final boolean isValidatorBeanNull = executableValidator == null;
        if (isValidatorBeanNull && micronautValidator == null) {
            return context.proceed();
        }
        final Object target = context.getTarget();
        if (isValidatorBeanNull) {
            final ExecutableMethod executableMethod = context.getExecutableMethod();
            @SuppressWarnings("unchecked")
            Set<ConstraintViolation<Object>> constraintViolations = this.micronautValidator.validateParameters(
                    target,
                    executableMethod,
                    context.getParameterValues());
            final boolean supportsReactive = micronautValidator instanceof ReactiveValidator;
            if (constraintViolations.isEmpty()) {
                final Object result = context.proceed();
                if (context.hasStereotype(Valid.class) || context.hasStereotype(Constraint.class)) {
                    final boolean hasResult = result != null;
                    if (supportsReactive & hasResult && Publishers.isConvertibleToPublisher(result)) {
                        ReactiveValidator reactiveValidator = (ReactiveValidator) micronautValidator;
                        final Publisher newPublisher = reactiveValidator.validatePublisher(
                                Publishers.convertPublisher(result, Publisher.class)
                        );
                        return Publishers.convertPublisher(newPublisher, executableMethod.getReturnType().getType());
                    } else if (supportsReactive & result instanceof CompletionStage) {
                        return ((ReactiveValidator) micronautValidator).validateCompletionStage(((CompletionStage) result));
                    } else {
                        constraintViolations = this.micronautValidator.validateReturnValue(target, executableMethod, result);
                        if (!constraintViolations.isEmpty()) {
                            throw new ConstraintViolationException(constraintViolations);
                        }
                    }
                }
                return result;
            } else {
                throw new ConstraintViolationException(constraintViolations);
            }
        } else {
            Method targetMethod = context.getTargetMethod();
            if (targetMethod.getParameterTypes().length == 0) {
                final Object result = context.proceed();
                return validateReturnValue(executableValidator, context, target, targetMethod, result);
            } else {
                Set<ConstraintViolation<Object>> constraintViolations = executableValidator
                        .validateParameters(
                                target,
                                targetMethod,
                                context.getParameterValues()
                        );
                if (constraintViolations.isEmpty()) {
                    final Object result = context.proceed();
                    return validateReturnValue(executableValidator, context, target, targetMethod, result);
                } else {
                    throw new ConstraintViolationException(constraintViolations);
                }
            }

        }
    }

    private Object validateReturnValue(@NonNull ExecutableValidator validator, MethodInvocationContext context, Object target, Method targetMethod, Object result) {
        Set<ConstraintViolation<Object>> constraintViolations;
        if (context.hasStereotype(Valid.class)) {
            constraintViolations = validator.validateReturnValue(
                    target,
                    targetMethod,
                    result
            );

            if (!constraintViolations.isEmpty()) {
                throw new ConstraintViolationException(constraintViolations);
            }
        }
        return result;
    }
}
