package httpdemo;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.Client;

import java.util.List;

@Client(id="synthData")
public interface SynthClient {

    @Get("/synths")
    List<Synthesizer> getSynthesizers();
}
