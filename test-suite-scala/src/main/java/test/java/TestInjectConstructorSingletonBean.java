package test.java;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TestInjectConstructorSingletonBean {
    public final TestSingletonBean singletonBean;
   // public final TestSingletonScalaBean singletonScalaBean;
    public final TestEngine[] engines;
    public final V8Engine v8Engine;
    public final V6Engine v6Engine;

    @Inject
    public TestInjectConstructorSingletonBean(
      TestSingletonBean singletonBean,
//      TestSingletonScalaBean singletonScalaBean,
      TestEngine[] engines,
      V8Engine v8Engine,
      V6Engine v6Engine
    ) {
        this.singletonBean = singletonBean;
//        this.singletonScalaBean = singletonScalaBean;
        this.engines = engines;
        this.v8Engine = v8Engine;
        this.v6Engine = v6Engine;
    }
}
