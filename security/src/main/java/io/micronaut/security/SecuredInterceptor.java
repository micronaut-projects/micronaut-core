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

package io.micronaut.security;

import javax.inject.Singleton;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.http.annotation.Controller;
import io.micronaut.security.exceptions.MissingRoleException;
import io.micronaut.security.exceptions.NotAuthenticatedException;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.utils.SecurityService;

import java.util.Arrays;
import java.util.Optional;

/**
 * Interceptor implementation for the {@link io.micronaut.security.Secured} annotation.
 *
 * If {@link io.micronaut.security.Secured} annotation is present in a class annotated with {@link io.micronaut.http.annotation.Controller}, the interceptor does nothing because it is handled by {@link io.micronaut.security.rules.SecuredAnnotationRule}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class SecuredInterceptor implements MethodInterceptor<Object, Object> {

    /**
     * The position on the interceptor in the chain.
     */
    public static final int POSITION = InterceptPhase.VALIDATE.getPosition();

    protected final SecurityService securityService;

    /**
     *
     * @param securityService Bean with security utility methods.
     */
    public SecuredInterceptor(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public int getOrder() {
        return POSITION;
    }

    /**
     *
     * @param context The context
     * @return Proceeds
     * @throws MissingRoleException when the user does not have any of the required roles
     * @throws NotAuthenticatedException when the user is not authenticated and @Secured values require authentication
     */
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.hasDeclaredAnnotation(Secured.class)) {
            if (context.findAnnotation(Controller.class).isPresent()) {
                return context.proceed();
            }
            String[] roles = context.getValue(Secured.class, String[].class).orElse(null);
            ProceedResult result = proceedResult(roles, securityService::isAuthenticated, securityService::hasRole);
            switch (result) {
                case FORBIDDEN:
                    StringBuilder sb = new StringBuilder();
                    sb.append("Authenticated user must have at least one role ");
                    if (roles != null) {
                        Optional<String> str = Arrays.stream(roles).reduce((a, b) -> a + ", " + b);
                        str.ifPresent(sb::append);
                    }
                    throw new MissingRoleException(sb.toString());
                case UNAUTHORIZED:
                    throw new NotAuthenticatedException("Authentication not found");
                case PROCEED:
                default:
                    return context.proceed();
            }
        }
        return context.proceed();
    }

    /**
     *
     * @param roles List of roles present in {@link io.micronaut.security.Secured} annotation.
     * @param authenticationChecker Implementation of {@link io.micronaut.security.SecuredInterceptor.AuthenticationChecker}
     * @param roleChecker Implementation of {@link io.micronaut.security.SecuredInterceptor.RoleChecker}
     * @return Returns an enum indicating whether the flow should proceed or an exception should be raised.
     */
    public static ProceedResult proceedResult(String[] roles,
                                              AuthenticationChecker authenticationChecker,
                                              RoleChecker roleChecker) {
        if (roles != null && roles.length == 1) {
            if (roles[0].equals(SecurityRule.IS_ANONYMOUS)) {
                return ProceedResult.PROCEED;
            }
        }

        if (!authenticationChecker.isAuthenticated()) {
            return ProceedResult.UNAUTHORIZED;
        }

        if (roles != null) {
            if (Arrays.stream(roles).anyMatch(role -> role.equals(SecurityRule.IS_AUTHENTICATED) || roleChecker.hasRole(role))) {
                return ProceedResult.PROCEED;
            }

            return ProceedResult.FORBIDDEN;
        }
        return ProceedResult.PROCEED;
    }

    /**
     * Different results of processing {@link io.micronaut.security.Secured} annotation.
     */
    protected enum ProceedResult {
        PROCEED,
        UNAUTHORIZED,
        FORBIDDEN
    }

    /**
     * Defines an interface to check whether a user has a role or not.
     */
    protected  interface RoleChecker {
        /**
         *
         * @param role User role
         * @return true if the user has the role
         */
        boolean hasRole(String role);
    }

    /**
     *  Defines an interface to check whether a user is authenticated or no.
     */
    protected  interface AuthenticationChecker {
        /**
         *
         * @return if the user is authenticated.
         */
        boolean isAuthenticated();
    }
}
