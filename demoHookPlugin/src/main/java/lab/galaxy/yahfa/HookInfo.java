package lab.galaxy.yahfa;

/**
 * Created by liuruikai756 on 31/03/2017.
 */

public class HookInfo {
    static {
//        System.loadLibrary("helloJni");
    }

    public static String[] hookItemNames = {
            //      "lab.lab.galaxy.demeHookPlugin.Hook_test",
//        "lab.lab.galaxy.demeHookPlugin.Hook_AssetManager_open",
//        "lab.lab.galaxy.demeHookPlugin.Hook_URL_openConnection",
         //   "lab.galaxy.demeHookPlugin.Hook_File_init",
            "lab.galaxy.demeHookPlugin.Hook_HomeActivity_onCreate",
//        "lab.lab.galaxy.demeHookPlugin.Hook_TelephonyManager_getDeviceId"
    };
}