package test.java;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class TestInjectSingletonBean {
    public final TestSingletonBean singletonBean;
   // public final TestSingletonScalaBean singletonScalaBean;
    public final TestEngine[] engines;
    public final V8Engine v8Engine;
    public final V6Engine v6Engine;
    public final Optional<TestEngine> existingOptionalEngine;
    public final Optional<TestEngine> nonExistingOptionalEngine;

    @Inject
    public TestInjectSingletonBean(
      TestSingletonBean singletonBean,
//      TestSingletonScalaBean singletonScalaBean,
      TestEngine[] engines,
      V8Engine v8Engine,
      V6Engine v6Engine,
      @Named("v8") Optional<TestEngine> existingOptionalEngine,
      @Named("dne") Optional<TestEngine> nonExistingOptionalEngine
    ) {
        this.singletonBean = singletonBean;
//        this.singletonScalaBean = singletonScalaBean;
        this.engines = engines;
        this.v8Engine = v8Engine;
        this.v6Engine = v6Engine;
        this.existingOptionalEngine = existingOptionalEngine;
        this.nonExistingOptionalEngine = nonExistingOptionalEngine;
    }
}
