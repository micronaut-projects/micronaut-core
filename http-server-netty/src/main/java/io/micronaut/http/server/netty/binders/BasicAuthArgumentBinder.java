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
package io.micronaut.http.server.netty.binders;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicAuth;
import io.micronaut.http.HttpHeaderValues;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Responsible for binding to a {@link BasicAuth} argument from the authorization
 * header in the request.
 *
 * @author James Kleeh
 * @since 1.3.0
 */
public class BasicAuthArgumentBinder implements TypedRequestArgumentBinder<BasicAuth> {

    @Override
    public boolean supportsSuperTypes() {
        return false;
    }

    @Override
    public Argument<BasicAuth> argumentType() {
        return Argument.of(BasicAuth.class);
    }

    @Override
    public BindingResult<BasicAuth> bind(ArgumentConversionContext<BasicAuth> context, HttpRequest<?> source) {
        final String authorization = source.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(HttpHeaderValues.AUTHORIZATION_PREFIX_BASIC)) {
            String base64Credentials = authorization.substring(6);
            byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(credDecoded, StandardCharsets.UTF_8);
            // credentials = username:password
            final String[] values = credentials.split(":", 2);
            if (values.length == 2) {
                return () -> Optional.of(new BasicAuth(values[0], values[1]));
            }
        }
        return BindingResult.EMPTY;
    }
}
