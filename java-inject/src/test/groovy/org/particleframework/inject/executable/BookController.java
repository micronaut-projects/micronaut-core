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
        return String.format("%d - The Stand", id);
    }

    @Executable
    public String showArray(Long[] id) {
        return String.format("%d - The Stand", id[0]);
    }

    @Executable
    public String showPrimitive(long id) {
        return String.format("%d - The Stand", id);
    }

    @Executable
    public String showPrimitiveArray(long[] id) {
        return String.format("%d - The Stand", id[0]);
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