package io.micronaut.http.server.stack;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.util.ArrayList;
import java.util.List;

@Controller("/search")
@Requires(property = "spec.name", value = "FullHttpStackBenchmark")
public class SearchController {
    @Post("find")
    public HttpResponse<Result> find(@Body Input input) {
        return find(input.haystack, input.needle);
    }

    private static MutableHttpResponse<Result> find(List<String> haystack, String needle) {
        for (int listIndex = 0; listIndex < haystack.size(); listIndex++) {
            String s = haystack.get(listIndex);
            int stringIndex = s.indexOf(needle);
            if (stringIndex != -1) {
                return HttpResponse.ok(new Result(listIndex, stringIndex));
            }
        }
        return HttpResponse.notFound();
    }

    @Introspected
    record Input(ArrayList<String> haystack, String needle) {
    }

    @Introspected
    record Result(int listIndex, int stringIndex) {
    }
}
