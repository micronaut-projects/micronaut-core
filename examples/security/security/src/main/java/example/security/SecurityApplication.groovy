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
package example.security

import example.security.providers.DbService
import example.security.services.RegisterService
import groovy.transform.CompileStatic
import io.micronaut.runtime.Micronaut
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.controllers.UsernamePassword

import javax.inject.Singleton
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.runtime.server.event.ServerStartupEvent

/**
 * @author Sergio del Amo
 * @since 1.0
 */
@CompileStatic
@Singleton
class SecurityApplication implements ApplicationEventListener<ServerStartupEvent> {

    protected final RegisterService registerService
    protected final DbService dbAuthenticationProvider

    SecurityApplication(RegisterService registerService, DbService dbAuthenticationProvider) {
        this.registerService = registerService
        this.dbAuthenticationProvider = dbAuthenticationProvider
    }

    @Override
    void onApplicationEvent(ServerStartupEvent event) {
        registerService.register('admin','admin',['ROLE_GRAILS'])
        UsernamePassword creds = new UsernamePassword('admin', 'admin')
        AuthenticationResponse rsp = dbAuthenticationProvider.authenticate(creds)
        assert rsp instanceof UserDetails
    }

    static void main(String[] args) {
        Micronaut.run(SecurityApplication.class)
    }
}
