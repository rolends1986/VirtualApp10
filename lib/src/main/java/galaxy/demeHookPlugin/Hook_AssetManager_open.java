package galaxy.demeHookPlugin;

import android.util.Log;

import java.io.InputStream;

/**
 * Created by liuruikai756 on 31/03/2017.
 */

public class Hook_AssetManager_open {
    public static String className = "android.content.res.AssetManager";
    public static String methodName = "open";
    public static String methodSig = "(Ljava/lang/String;)Ljava/io/InputStream;";
    public static InputStream hook(Object thiz, String fileName) {
        Log.e("VirtualHook", "open asset "+fileName);
        return backup(thiz, fileName);
    }

    public static InputStream backup(Object thiz, String msg) {
        Log.e("VirtualHook", "should not be here");
        return null;
    }
}
