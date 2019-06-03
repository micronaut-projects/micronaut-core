package io.micronaut.http.server.netty.xml

import com.fasterxml.aalto.AsyncByteArrayFeeder
import com.fasterxml.aalto.AsyncXMLInputFactory
import com.fasterxml.aalto.AsyncXMLStreamReader
import com.fasterxml.aalto.stax.InputFactoryImpl
import spock.lang.Ignore
import spock.lang.Specification

@Ignore
class XmlContentProcessorSpec extends Specification {

    void "test reading xml"() {
        InputFactoryImpl inputF = new InputFactoryImpl() // sub-class of XMLStreamReader2
        byte[] input_part1 = "<product><id>3</id></product>".getBytes("UTF-8") // would come from File, over the net etc
        AsyncXMLStreamReader<AsyncByteArrayFeeder> parser = inputF.createAsyncFor(input_part1)
        parser.getInputFeeder().endOfInput()
        parser.close()

        expect:
        1 == 1
    }
}
