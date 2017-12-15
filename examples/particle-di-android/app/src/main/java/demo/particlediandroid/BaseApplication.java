package demo.particlediandroid;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

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
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle bundle) {
                ctx.inject(activity);
            }

            @Override
            public void onActivityStarted(Activity activity) {

            }

            @Override
            public void onActivityResumed(Activity activity) {

            }

            @Override
            public void onActivityPaused(Activity activity) {

            }

            @Override
            public void onActivityStopped(Activity activity) {

            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

            }

            @Override
            public void onActivityDestroyed(Activity activity) {
//                ctx.destroy(activity); TODO: add destroy hook
            }
        });
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ctx.stop();
    }


}
