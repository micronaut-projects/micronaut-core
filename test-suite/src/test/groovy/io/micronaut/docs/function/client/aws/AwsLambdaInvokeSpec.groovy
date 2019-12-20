package io.micronaut.docs.function.client.aws

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.function.client.FunctionClient
import io.micronaut.function.client.FunctionDefinition
import io.micronaut.function.client.FunctionInvoker
import io.micronaut.function.client.FunctionInvokerChooser
import io.micronaut.function.client.aws.AWSInvokeRequestDefinition
import io.micronaut.function.client.aws.AWSLambdaConfiguration
import io.micronaut.http.annotation.Body
import io.reactivex.Single
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Specification

import javax.inject.Named

@IgnoreIf({
    return !new File("${System.getProperty("user.home")}/.aws/credentials").exists()
})
class AwsLambdaInvokeSpec extends Specification {

    void "test setup function definitions"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'aws.lambda.functions.test.functionName':'micronaut-function',
                'aws.lambda.functions.test.qualifier':'something'
        )
        
        Collection<FunctionDefinition> definitions = applicationContext.getBeansOfType(FunctionDefinition)
        
        expect:
        definitions.size() == 1
        definitions.first() instanceof AWSInvokeRequestDefinition
        definitions.first().invokeRequest.functionName == 'micronaut-function'
        definitions.first().invokeRequest.qualifier == 'something'

    }

    void "test setup lambda config"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'aws.lambda.functions.test.functionName':'micronaut-function',
                'aws.lambda.functions.test.qualifier':'something',
                'aws.lambda.region':'us-east-1'
        )
        AWSLambdaConfiguration configuration = applicationContext.getBean(AWSLambdaConfiguration)

        expect:
        configuration.builder.region == 'us-east-1'
    }

    @Ignore
    void "test invoke function"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'aws.lambda.functions.test.functionName':'micronaut-function',
                'aws.lambda.region':'us-east-1'
        )

        when:
        FunctionDefinition definition = applicationContext.getBean(FunctionDefinition)
        FunctionInvokerChooser chooser = applicationContext.getBean(FunctionInvokerChooser)
        Optional<FunctionInvoker> invoker = chooser.choose(definition)

        then:
        invoker.isPresent()

        when:
        FunctionInvoker invokerInstance = invoker.get()

        Book book = invokerInstance.invoke(definition, new Book(title: "The Stand"), Argument.of(Book))

        then:
        book != null
        book.title == "THE STAND"
    }

    @Ignore
    void "test invoke client with @FunctionClient"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'aws.lambda.functions.test.functionName':'micronaut-function',
                'aws.lambda.region':'us-east-1'
        )

        when:
        MyClient myClient = applicationContext.getBean(MyClient)
        Book book = myClient.micronautFunction( new Book(title: "The Stand") )

        then:
        book != null
        book.title == "THE STAND"

        when:
        book = myClient.someOtherName( "The Stand" )

        then:
        book != null
        book.title == "THE STAND"

        when:
        book = myClient.reactiveInvoke( "The Stand" ).blockingGet()

        then:
        book != null
        book.title == "THE STAND"
    }

    static class Book {
        String title
    }

    //tag::functionClient[]
    @FunctionClient
    static interface MyClient {

        Book micronautFunction(@Body Book book)

        @Named('micronaut-function')
        Book someOtherName(String title)

        @Named('micronaut-function')
        Single<Book> reactiveInvoke(String title)
    }
    //end::functionClient[]
}
