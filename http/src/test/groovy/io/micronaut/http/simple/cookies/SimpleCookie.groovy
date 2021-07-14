/*
 * Copyright 2021 original authors
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
package io.micronaut.http.simple.cookies

import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.SameSite
import spock.lang.Specification

class SimpleCookieSpec extends Specification {

    def "SimpleCookie.toString() returns a set-cookie compliant string"() {
        when:
        String name = "NAME"
        String value = "VALUE"
        String domain = "DOMAIN"
        String path = "PATH"
        boolean httpOnly = true
        boolean secure = true
        long maxAge = 1
        SameSite sameSite = SameSite.Lax

        Cookie cookie = new SimpleCookie(name, value)
                .domain(domain)
                .path(path)
                .httpOnly(httpOnly)
                .secure(secure)
                .maxAge(maxAge)
                .sameSite(sameSite)

        List<String> components = cookie.toString().split(';').collect { it.trim() }

        then:
        components[0] == "$name=$value"
        domain == null || components.find { it == "Domain=$domain" }
        domain != null || !components.find { it == "Domain=$domain" }
        path == null || components.find { it == "Path=$path" }
        path != null || !components.find { it == "Path=$path" }
        !httpOnly || components.find { it == "HttpOnly" }
        httpOnly || !components.find { it == "HttpOnly" }
        !secure || components.find { it == "Secure" }
        secure || !components.find { it == "Secure" }
        maxAge < 0 || components.find { it == "Max-Age=$maxAge" }
        maxAge >= 0 || !components.find { it == "Max-Age=$maxAge" }
        sameSite == null || components.find { it == "SameSite=$sameSite" }
        sameSite != null || !components.find { it == "SameSite=$sameSite" }
    }

}
