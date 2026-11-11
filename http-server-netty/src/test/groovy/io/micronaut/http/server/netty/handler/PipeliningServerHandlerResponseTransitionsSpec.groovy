package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.ReferenceCountUtil
import reactor.core.publisher.Flux
import spock.lang.Specification

/**
 * Drives each response state of {@link PipeliningServerHandler.OutboundAccessImpl} through its
 * transitions, including the ones only a failure or a discarded response reaches.
 */
class PipeliningServerHandlerResponseTransitionsSpec extends Specification {

    List<PipeliningServerHandler.OutboundAccessImpl> accesses = []
    List<Object> written = []
    List<Throwable> failed = []
    Set<String> failAccept = []
    Map<String, HttpResponseStatus> answerInAccept = [:]
    Set<String> finishInAccept = []

    PipeliningServerHandler handler = new PipeliningServerHandler(new RequestHandler() {
        @Override
        void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
            outboundAccess.attachment(request.uri())
            accesses << (PipeliningServerHandler.OutboundAccessImpl) outboundAccess
            if (request.headers().contains(HttpHeaderNames.EXPECT)) {
                // demanding the body requests the 100 Continue
                Flux.from(body.toByteArrayPublisher()).subscribe()
            } else {
                body.close()
            }
            def status = answerInAccept[request.uri()]
            if (status != null) {
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, status), NettyByteBodyFactory.empty())
                if (finishInAccept.contains(request.uri())) {
                    finishing((PipeliningServerHandler.OutboundAccessImpl) outboundAccess)
                }
            }
            if (failAccept.contains(request.uri())) {
                throw new RuntimeException("accept failed")
            }
        }

        @Override
        void handleUnboundError(Throwable cause) {
        }

        @Override
        void responseWritten(Object attachment) {
            written << attachment
        }

        @Override
        void responseFailed(Object attachment, Throwable cause) {
            failed << cause
        }
    })

    EmbeddedChannel ch = new EmbeddedChannel(handler)

    def cleanup() {
        ch.finishAndReleaseAll()
    }

    private void request(String uri) {
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri))
        ch.runPendingTasks()
    }

    private void continueRequest(String uri) {
        def request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri)
        request.headers().add(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE)
        request.headers().add(HttpHeaderNames.CONTENT_LENGTH, 3)
        ch.writeInbound(request)
        ch.runPendingTasks()
    }

    private static void respond(PipeliningServerHandler.OutboundAccessImpl access, HttpResponseStatus status) {
        access.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, status), NettyByteBodyFactory.empty())
    }

    private List<HttpResponse> responses() {
        List<HttpResponse> responses = []
        Object msg
        while ((msg = ch.readOutbound()) != null) {
            if (msg instanceof HttpResponse) {
                responses << (HttpResponse) msg
            }
            ReferenceCountUtil.release(msg)
        }
        return responses
    }

    private static String stateName(PipeliningServerHandler.OutboundAccessImpl access) {
        return access.@state.getClass().simpleName
    }

    /**
     * Move a request whose response was written to the finishing state of that response: the
     * response handed its final write over, but is not reported written yet. On a real
     * connection this only lasts while a streamed response issues its final write.
     */
    private static void finishing(PipeliningServerHandler.OutboundAccessImpl access) {
        def response = access.handler
        assert response != null
        def finishing = Class.forName(PipeliningServerHandler.name + '$Finishing')
            .getDeclaredConstructor(PipeliningServerHandler.OutboundHandler)
        finishing.accessible = true
        access.@state = finishing.newInstance(response)
    }

    def 'a response queued behind an earlier one is found by every lookup until it is written'() {
        given:
        request('/first')
        request('/second')
        def first = accesses[0]
        def second = accesses[1]

        expect:
        stateName(second) == 'Unanswered'
        second.handler == null
        second.pendingResponse() == null
        second.queuedHandler() == null
        second.startWriting() == null

        when:
        respond(second, HttpResponseStatus.ACCEPTED)
        def queued = second.handler

        then:
        stateName(second) == 'Queued'
        queued != null
        second.pendingResponse().is(queued)
        second.queuedHandler().is(queued)

        when: 'a 100 Continue asked for once a response was submitted is not written'
        second.writeContinue()

        then:
        stateName(second) == 'Queued'

        when:
        respond(first, HttpResponseStatus.NO_CONTENT)
        ch.runPendingTasks()

        then:
        stateName(second) == 'Written'
        second.handler.is(queued)
        second.pendingResponse() == null
        second.queuedHandler() == null
        responses()*.status() == [HttpResponseStatus.NO_CONTENT, HttpResponseStatus.ACCEPTED]
        written == ['/first', '/second']

        when: 'a written request is not queued anymore, and the handler of another response does not move it'
        second.startWriting()

        then:
        thrown(IllegalStateException)

        when:
        second.toFinishing(first.handler)

        then:
        thrown(IllegalStateException)

        when: 'a final write the response still had queued when it was discarded leaves it written'
        second.toFinishing(queued)

        then:
        stateName(second) == 'Written'

        expect:
        !second.toWritten(first.handler)
        !second.toWritten(queued)
        written == ['/first', '/second']
    }

    def 'a response that handed its final write over is finishing until it is reported written'() {
        given:
        request('/only')
        def access = accesses[0]
        respond(access, HttpResponseStatus.OK)
        def response = access.handler
        finishing(access)

        expect:
        stateName(access) == 'Finishing'
        access.handler.is(response)
        access.pendingResponse() == null
        access.queuedHandler() == null

        when: 'handing over the final write again leaves it finishing'
        access.toFinishing(response)

        then:
        stateName(access) == 'Finishing'

        when:
        def report = access.toWritten(response)

        then:
        report
        stateName(access) == 'Written'
    }

    def 'an off-loop write that fails while the response is finishing discards it and closes the connection'() {
        given:
        request('/only')
        def access = accesses[0]
        respond(access, HttpResponseStatus.OK)
        finishing(access)

        when:
        def failure = new RuntimeException('write failed')
        access.giveUpOffLoopWrite(failure)
        ch.runPendingTasks()

        then:
        failed == [failure]
        stateName(access) == 'Written'
        !ch.isOpen()
    }

    def 'an off-loop write that fails while the response is queued closes the connection'() {
        given:
        request('/first')
        request('/second')
        respond(accesses[1], HttpResponseStatus.ACCEPTED)

        expect:
        stateName(accesses[1]) == 'Queued'

        when:
        def failure = new RuntimeException('write failed')
        accesses[1].giveUpOffLoopWrite(failure)
        ch.runPendingTasks()

        then:
        failed == [failure]
        !ch.isOpen()
    }

    def 'an off-loop write that fails before any response was submitted answers the request and closes the connection'() {
        given:
        request('/only')
        def access = accesses[0]

        expect:
        stateName(access) == 'Unanswered'

        when:
        def failure = new RuntimeException('write failed')
        access.giveUpOffLoopWrite(failure)
        ch.runPendingTasks()

        then:
        failed == [failure]
        responses()*.status() == [HttpResponseStatus.INTERNAL_SERVER_ERROR]
        !ch.isOpen()
    }

    def 'a request whose accept failed after its response was queued closes the connection once the response is written'() {
        given:
        request('/first')
        answerInAccept['/second'] = HttpResponseStatus.ACCEPTED
        failAccept << '/second'

        when:
        request('/second')

        then:
        stateName(accesses[1]) == 'Queued'
        ch.isOpen()

        when:
        respond(accesses[0], HttpResponseStatus.NO_CONTENT)
        ch.runPendingTasks()

        then:
        def responses = responses()
        responses*.status() == [HttpResponseStatus.NO_CONTENT, HttpResponseStatus.ACCEPTED]
        responses[1].headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)
        written == ['/first', '/second']
        !ch.isOpen()
    }

    def 'a request whose accept failed after its response was written completely closes the connection'() {
        given:
        answerInAccept['/only'] = HttpResponseStatus.ACCEPTED
        failAccept << '/only'

        when:
        request('/only')

        then:
        responses()*.status() == [HttpResponseStatus.ACCEPTED]
        written == ['/only']
        !ch.isOpen()
    }

    def 'a request whose accept failed while its response is finishing closes the connection'() {
        given:
        answerInAccept['/only'] = HttpResponseStatus.ACCEPTED
        finishInAccept << '/only'
        failAccept << '/only'

        when:
        request('/only')

        then:
        responses()*.status() == [HttpResponseStatus.ACCEPTED]
        !ch.isOpen()
    }

    def 'a 100 Continue queued behind an earlier response is the handler its request waits with'() {
        given:
        request('/first')
        continueRequest('/second')
        def second = accesses[1]

        expect:
        stateName(second) == 'ContinueRequested'
        second.handler == null
        second.pendingResponse() == null
        second.queuedHandler() != null

        when:
        respond(second, HttpResponseStatus.ACCEPTED)
        def continueHandler = second.queuedHandler()

        then:
        stateName(second) == 'Queued'
        continueHandler instanceof PipeliningServerHandler.ContinueOutboundHandler
        second.pendingResponse().is(second.handler)

        when: 'a close marked through the 100 Continue reaches the response that follows it'
        continueHandler.markCloseAfterWrite()
        respond(accesses[0], HttpResponseStatus.NO_CONTENT)
        ch.runPendingTasks()

        then:
        def responses = responses()
        responses*.status() == [HttpResponseStatus.NO_CONTENT, HttpResponseStatus.CONTINUE, HttpResponseStatus.ACCEPTED]
        responses[2].headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)
        written == ['/first', '/second']
    }

    def 'a 100 Continue asked for after the handler was removed is not written'() {
        given:
        request('/only')
        def access = accesses[0]
        ch.pipeline().remove(handler)

        when:
        access.writeContinue()
        ch.runPendingTasks()

        then:
        stateName(access) == 'Unanswered'
        responses().isEmpty()
        // a response discarded without being submitted, because the handler was removed, is reported
        access.toWritten(null)
    }
}
