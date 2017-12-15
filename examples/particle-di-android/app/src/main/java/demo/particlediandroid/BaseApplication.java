package demo.particlediandroid;

import android.app.Application;

import org.particleframework.context.ApplicationContext;

public class BaseApplication extends Application {

    public BaseApplication() {
        super();
    }

    ApplicationContext ctx;

    @Override
    public void onCreate() {
        super.onCreate();
         ctx = ApplicationContext.build(MainActivity.class).start();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ctx.stop();
    }

}
