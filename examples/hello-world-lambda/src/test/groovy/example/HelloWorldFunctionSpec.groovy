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
package example

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class HelloWorldFunctionSpec extends Specification {

    void "run function directly"() {
        expect:
        new HelloWorldFunction()
                .hello(new Person(name: "Fred")).text == "Hello Fred!"
    }

    void "run function as REST service"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        HelloClient client = server.getApplicationContext().getBean(HelloClient)

        when:
        Message message = client.hello("Fred").blockingGet()
        
        then:
        message.text == "Hello Fred!"

        cleanup:
        if(server != null)
            server.stop()
    }

    @IgnoreIf({
        return !new File("${System.getProperty("user.home")}/.aws/credentials").exists()
    })
    void "run execute function as lambda"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'aws.lambda.functions.hello.functionName':'hello-world',
                'aws.lambda.region':'us-east-1'
        )
        HelloClient client = applicationContext.getBean(HelloClient)

        when:
        Message message = client.hello("Fred").blockingGet()

        then:
        message.text == "Hello Fred!"

        cleanup:
        if(applicationContext != null)
            applicationContext.stop()
    }
}
