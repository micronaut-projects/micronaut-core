package org.particleframework.inject.executable;

import org.particleframework.inject.annotation.Executable;
import org.particleframework.stereotype.Controller;

import javax.inject.Inject;
import java.util.List;

@Controller
public class BookController {
    @Inject
    BookService bookService;

    @Executable
    String show(Long id) {
        return "$id - The Stand";
    }

    @Executable
    String showArray(Long[] id) {
        return "${id[0]} - The Stand";
    }

    @Executable
    String showPrimitive(long id) {
        return "$id - The Stand";
    }

    @Executable
    String showPrimitiveArray(long[] id) {
        return "${id[0]} - The Stand";
    }

    @Executable
    void showVoidReturn(List<String> jobNames) {
        jobNames.add("test");
    }

    @Executable
    int showPrimitiveReturn(int[] values) {
        return values[0];
    }
}