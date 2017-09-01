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
    public String show(Long id) {
        return "$id - The Stand";
    }

    @Executable
    public String showArray(Long[] id) {
        return "${id[0]} - The Stand";
    }

    @Executable
    public String showPrimitive(long id) {
        return "$id - The Stand";
    }

    @Executable
    public String showPrimitiveArray(long[] id) {
        return "${id[0]} - The Stand";
    }

    @Executable
    public void showVoidReturn(List<String> jobNames) {
        jobNames.add("test");
    }

    @Executable
    public int showPrimitiveReturn(int[] values) {
        return values[0];
    }
}