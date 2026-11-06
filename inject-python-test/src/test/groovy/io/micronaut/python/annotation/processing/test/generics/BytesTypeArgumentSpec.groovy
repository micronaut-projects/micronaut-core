package io.micronaut.python.annotation.processing.test.generics

import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.python.compiler.PyronautCompiler
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.nio.charset.StandardCharsets

class BytesTypeArgumentSpec extends AbstractPythonTypeElementSpec {

    void "bytes as a generic type argument is mapped to byte[] in the generated signatures"() {
        given:
        def pythonCode = '''
from typing import Annotated, Optional
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Nullable
from micronaut.python.annotation.processing.test.generics import BinaryCodec, BinaryMessage, ByteArrayCodec
from reactor.core.publisher import Flux, Mono

@Singleton
class BinaryService:
    @Executable
    def codec(self) -> BinaryCodec[bytes, bytes]:
        return ByteArrayCodec.INSTANCE

    @Executable
    def message(self) -> BinaryMessage[bytes, int]:
        return BinaryMessage(b"key", 1)

    @Executable
    def payload(self) -> Mono[bytes]:
        return Mono.just(b"abc")

    @Executable
    def payloads(self) -> Flux[bytes]:
        return Flux.just(b"abc")

    @Executable
    def chunks(self) -> list[bytes]:
        return [b"a", b"b"]

    @Executable
    def buffers(self) -> list[bytearray]:
        return [bytearray(b"a")]

    @Executable
    def named(self) -> dict[str, bytes]:
        return {"a": b"a"}

    @Executable
    def optional(self) -> Optional[bytes]:
        return None

    @Executable
    def nullable(self) -> bytes | None:
        return None

    @Executable
    def annotated(self) -> list[Annotated[bytes, Nullable]]:
        return []

    @Executable
    def nested(self) -> dict[str, list[bytes]]:
        return {}

    @Executable
    async def raw(self) -> bytes:
        return b"abc"
'''
        def tempDir = File.createTempDir("python-bytes-generics", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()
        def generated = new File(tempDir, "python/BinaryService.java").text

        then:
        generated.contains("BinaryCodec<byte[], byte[]> codec()")
        generated.contains("BinaryMessage<byte[], Integer> message()")
        generated.contains("Mono<byte[]> payload()")
        generated.contains("Flux<byte[]> payloads()")
        generated.contains("List<byte[]> chunks()")
        generated.contains("List<byte[]> buffers()")
        generated.contains("Map<String, byte[]> named()")
        generated.contains("Optional<byte[]> optional()")
        generated.contains("byte[] nullable()")
        generated.contains("List<byte[]> annotated()")
        generated.contains("Map<String, List<byte[]>> nested()")
        generated.contains("CompletionStage<byte[]> raw()")
        !generated.contains("Byte")

        cleanup:
        tempDir.deleteDir()
    }

    void "bytes type arguments are byte[] in the bean definition and coerced at runtime"() {
        given:
        def context = buildContext('''
from typing import Annotated
from jakarta.inject import Singleton
from micronaut.context.annotation import Bean, Executable, Factory
from micronaut.python.annotation.processing.test.generics import BinaryCodec, BinaryMessage, ByteArrayCodec
from reactor.core.publisher import Flux, Mono

@Factory
class CodecFactory:
    @Singleton
    @Bean
    def codec(self) -> BinaryCodec[bytes, bytes]:
        return ByteArrayCodec.INSTANCE

@Singleton
class BinaryService:
    def __init__(self, codec: BinaryCodec[bytes, bytes]):
        self.codec = codec

    @Executable
    def decode(self, key: str) -> bytes:
        return self.codec.decodeKey(key)

    @Executable
    def message(self) -> BinaryMessage[bytes, int]:
        return BinaryMessage(b"key", 1)

    @Executable
    def payload(self) -> Mono[bytes]:
        return Mono.just(b"abc")

    @Executable
    def payloads(self) -> Flux[bytes]:
        return Flux.just(b"abc")

    @Executable
    def chunks(self) -> list[bytes]:
        return [b"a", b"b"]

    @Executable
    def named(self) -> dict[str, bytes]:
        return {"a": b"a"}
''', true)

        when:
        def codecDefinition = context.getBeanDefinition(BinaryCodec)
        def serviceDefinition = getBeanDefinition(context, 'python.BinaryService')
        def service = getBean(context, 'python.BinaryService')

        then:
        codecDefinition.getTypeArguments(BinaryCodec)*.type == [byte[], byte[]]
        context.getBean(Argument.of(BinaryCodec, byte[], byte[])).is(ByteArrayCodec.INSTANCE)
        serviceDefinition.getRequiredMethod("payload").returnType.asArgument().typeParameters*.type == [byte[]]
        serviceDefinition.getRequiredMethod("payloads").returnType.asArgument().typeParameters*.type == [byte[]]
        serviceDefinition.getRequiredMethod("chunks").returnType.asArgument().typeParameters*.type == [byte[]]
        serviceDefinition.getRequiredMethod("named").returnType.asArgument().typeParameters*.type == [String, byte[]]
        serviceDefinition.getRequiredMethod("message").returnType.asArgument().typeParameters*.type == [byte[], Integer]

        when:
        def decoded = serviceDefinition.getRequiredMethod("decode", String).invoke(service, "key")
        def message = serviceDefinition.getRequiredMethod("message").invoke(service)
        def payload = serviceDefinition.getRequiredMethod("payload").invoke(service)
        def payloads = serviceDefinition.getRequiredMethod("payloads").invoke(service)
        def chunks = serviceDefinition.getRequiredMethod("chunks").invoke(service)
        def named = serviceDefinition.getRequiredMethod("named").invoke(service)

        then:
        decoded == "key".getBytes(StandardCharsets.UTF_8)
        message instanceof BinaryMessage
        message.key() == "key".getBytes(StandardCharsets.UTF_8)
        message.value() == 1
        payload instanceof Mono
        Mono.from(payload).block() == "abc".getBytes(StandardCharsets.UTF_8)
        payloads instanceof Flux
        Flux.from(payloads).collectList().block() == ["abc".getBytes(StandardCharsets.UTF_8)]
        chunks == ["a".getBytes(StandardCharsets.UTF_8), "b".getBytes(StandardCharsets.UTF_8)]
        named == [a: "a".getBytes(StandardCharsets.UTF_8)]

        cleanup:
        context?.close()
    }

    void "controller returning Mono of bytes serves the raw bytes"() {
        given:
        def context = buildContext('''
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Get
from reactor.core.publisher import Mono

@Controller("/binary")
class BinaryController:
    @Get(produces=MediaType.APPLICATION_OCTET_STREAM)
    def data(self) -> Mono[bytes]:
        return Mono.just(b"abc")
''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/binary"), byte[]) == "abc".getBytes(StandardCharsets.UTF_8)

        cleanup:
        client.close()
        context?.close()
    }
}
