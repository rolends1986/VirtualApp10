package lab.galaxy.demeHookPlugin;

import android.app.Activity;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;

import java.io.File;

public class Hook_HomeActivity_onCreate {

    public static String className = "com.xunmeng.pinduoduo.ui.activity.HomeActivity";
    public static String methodName = "onCreate";
    public static String methodSig = "(Landroid/os/Bundle;)V";

    public static void hook(Object thiz, Bundle savedInstanceState) {
        Log.e("VirtualHook", "Activity onCreate " + thiz.toString());
        backup(thiz, savedInstanceState);
    }

    public static void backup(Object thiz, Bundle savedInstanceState) {
        Log.e("VirtualHook", "Activity should not be here");
        throw new UnsupportedOperationException("Stub!");
    }
}
