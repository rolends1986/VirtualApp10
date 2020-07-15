package galaxy.demeHookPlugin;

import android.util.Log;

/**
 * Created by liuruikai756 on 17/05/2017.
 */

public class Hook_TelephonyManager_getDeviceId {
    public static String className = "android.telephony.TelephonyManager";
    public static String methodName = "getDeviceId";
    public static String methodSig = "()Ljava/lang/String;";
    public static String hook(Object thiz) {

        Log.e("VirtualHook", "TelephonyManager getDeviceId hooked: ");
        String res = backup(thiz);
        Log.e("VirtualHook", "TelephonyManager getDeviceId hooked: ");
        return "1234567890"+res;
    }


    public static String backup(Object thiz) {
        Log.e("VirtualHook", "should not be here");
        return "";
    }

}
