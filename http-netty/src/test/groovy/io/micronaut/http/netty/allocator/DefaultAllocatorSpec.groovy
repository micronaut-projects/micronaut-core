package io.micronaut.http.netty.allocator

import io.micronaut.context.ApplicationContext
import io.netty.buffer.PooledByteBufAllocator
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Unroll

@Stepwise
class DefaultAllocatorSpec extends Specification {

    @Unroll
    void "test default allocator config #name"() {
        given:
        def context = ApplicationContext.run(
                ("netty.default.allocator." + name): value
        )

        expect:
        System.getProperty("io.netty.allocator.$name") == value.toString()

        cleanup:
        context.close()

        where:
        name                           | value
        'maxOrder'                     | 1
        'pageSize'                     | 5
        'chunkSize'                    | 2048
        'smallCacheSize'               | 2048
        'normalCacheSize'              | 2048
        'maxCachedBufferCapacity'      | 2048
        'useCacheForAllThreads'        | false
        'numHeapArenas'                | 5
        'numDirectArenas'              | 5
        'cacheTrimInterval'            | 1000
        'maxCachedByteBuffersPerChunk' | 20
    }

    void "test default allocator configured"() {
        given:
        def context = ApplicationContext.run(
                ("netty.default.allocator.smallCacheSize"): 4096
        )

        expect:
        PooledByteBufAllocator.DEFAULT.metric().smallCacheSize() == 4096

        cleanup:
        context.close()
    }
}
