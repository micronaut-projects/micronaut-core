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
package io.micronaut.security.token.reader;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Singleton;
import java.util.Optional;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class BearerTokenReader implements TokenReader {
    private static final Logger log = LoggerFactory.getLogger(BearerTokenReader.class);

    String preffix = "Bearer";

    @Override
    public String findToken(HttpRequest<?> request) {
        log.debug("Looking for bearer token in Authorization header");

        HttpHeaders headers = request.getHeaders();
        Optional<String> authorizationHeader = headers.getAuthorization();
        if ( authorizationHeader.isPresent() ) {
            String authorization = authorizationHeader.get();
            log.debug("Authorization header: {}", authorization);
            return extractTokenFromAuthorization(authorization);
        } else {
            return null;
        }
    }

    public String extractTokenFromAuthorization(String authorization) {
        StringBuilder sb = new StringBuilder();
        sb.append(preffix);
        sb.append(" ");
        String str = sb.toString();
        if ( authorization.startsWith(str) ) {
            return authorization.substring(str.length(), authorization.length());
        } else {
            log.debug("{} does not start with {}", authorization, str);
            return null;
        }
    }
}
