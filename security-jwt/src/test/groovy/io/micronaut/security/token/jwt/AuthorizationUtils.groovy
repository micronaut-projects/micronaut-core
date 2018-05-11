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
package io.micronaut.security.token.jwt

import groovy.transform.CompileStatic
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.RxHttpClient
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken

@CompileStatic
trait AuthorizationUtils {

    abstract RxHttpClient getClient()

    String loginWith(RxHttpClient client, String username = "valid", String password = "valid") {
        def creds = new UsernamePasswordCredentials(username, password)
        def resp = client.toBlocking().exchange(HttpRequest.POST('/login', creds), BearerAccessRefreshToken)
        resp.body().accessToken
    }

    HttpResponse get(RxHttpClient client, String path, String token = null, String prefix = 'Bearer') {
        HttpRequest req = HttpRequest.GET(path)
        if (token != null) {
            req = req.header("Authorization", "${prefix} ${token}".toString())
        }
        client.toBlocking().exchange(req, String)
    }

    String loginWith(String username = "valid") {
        loginWith(client, username)
    }

    HttpResponse get(String path, String token = null, String prefix = 'Bearer') {
        get(client, path, token, prefix)
    }
}