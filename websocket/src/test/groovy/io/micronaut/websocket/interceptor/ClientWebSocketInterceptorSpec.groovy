package io.micronaut.websocket.interceptor

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.core.type.ReturnType
import io.micronaut.websocket.WebSocketSession
import spock.lang.Specification
import spock.lang.Unroll

class ClientWebSocketInterceptorSpec extends Specification {

    @Unroll
    void "close declared by #declaringType.simpleName closes the session"() {
        given:
        def session = Mock(WebSocketSession)
        def interceptor = interceptorWithSession(session)
        def context = Mock(MethodInvocationContext) {
            getDeclaringType() >> declaringType
        }

        when:
        def result = interceptor.intercept(context)

        then:
        result == null
        1 * session.close()
        0 * context.proceed()

        where:
        declaringType << [Closeable, AutoCloseable]
    }

    void "abstract no-arg void close method closes the session"() {
        given:
        def session = Mock(WebSocketSession)
        def interceptor = interceptorWithSession(session)
        def context = closeContext(true, 'close', Argument.ZERO_ARGUMENTS, Void.TYPE)

        when:
        def result = interceptor.intercept(context)

        then:
        result == null
        1 * session.close()
        0 * context.proceed()
    }

    void "abstract close method without a session is a no-op"() {
        given:
        def interceptor = new ClientWebSocketInterceptor(ConversionService.SHARED)
        def context = closeContext(true, 'close', Argument.ZERO_ARGUMENTS, Void.TYPE)

        when:
        def result = interceptor.intercept(context)

        then:
        result == null
        0 * context.proceed()
    }

    @Unroll
    void "method #methodName (abstract=#isAbstract, args=#args.length, returns=#returnType.name) is not treated as close"() {
        given:
        def session = Mock(WebSocketSession)
        def interceptor = interceptorWithSession(session)
        def context = closeContext(isAbstract, methodName, args, returnType)

        when:
        def result = interceptor.intercept(context)

        then:
        result == 'proceeded'
        0 * session.close()
        1 * context.proceed() >> 'proceeded'

        where:
        isAbstract | methodName | args                                     | returnType
        false      | 'close'    | Argument.ZERO_ARGUMENTS                  | Void.TYPE
        true       | 'shutdown' | Argument.ZERO_ARGUMENTS                  | Void.TYPE
        true       | 'close'    | [Argument.of(String, 'reason')] as Argument[] | Void.TYPE
        true       | 'close'    | Argument.ZERO_ARGUMENTS                  | String
    }

    private ClientWebSocketInterceptor interceptorWithSession(WebSocketSession session) {
        def interceptor = new ClientWebSocketInterceptor(ConversionService.SHARED)
        def aware = Mock(MethodInvocationContext) {
            getDeclaringType() >> WebSocketSessionAware
            getParameterValues() >> ([session] as Object[])
        }
        assert interceptor.intercept(aware) == null
        return interceptor
    }

    private MethodInvocationContext closeContext(boolean abstractMethod, String name, Argument[] arguments, Class type) {
        def rt = Stub(ReturnType)
        rt.getType() >> type
        def context = Mock(MethodInvocationContext)
        context.getDeclaringType() >> ClientWebSocketInterceptorSpec
        context.isAbstract() >> abstractMethod
        context.getMethodName() >> name
        context.getArguments() >> arguments
        context.getReturnType() >> rt
        return context
    }
}
