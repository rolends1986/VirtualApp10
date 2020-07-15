package com.lody.virtual.client;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.StrictMode;
import android.util.Log;

import com.kook.tools.util.AndroidVersionConstant;
import com.lody.virtual.client.core.CrashHandler;
import com.lody.virtual.client.core.InvocationStubManager;
import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.env.SpecialComponentList;
import com.lody.virtual.client.env.VirtualRuntime;
import com.lody.virtual.client.fixer.ContextFixer;
import com.lody.virtual.client.hook.delegate.AppInstrumentation;
import com.lody.virtual.client.hook.providers.ProviderHook;
import com.lody.virtual.client.hook.proxies.am.HCallbackStub;
import com.lody.virtual.client.hook.secondary.ProxyServiceFactory;
import com.lody.virtual.client.ipc.VActivityManager;
import com.lody.virtual.client.ipc.VDeviceManager;
import com.lody.virtual.client.ipc.VPackageManager;
import com.lody.virtual.client.ipc.VirtualStorageManager;
import com.lody.virtual.client.stub.VASettings;
import com.lody.virtual.helper.compat.BuildCompat;
import com.lody.virtual.helper.compat.StorageManagerCompat;
import com.lody.virtual.helper.utils.VLog;
import com.lody.virtual.os.VEnvironment;
import com.lody.virtual.os.VUserHandle;
import com.lody.virtual.remote.InstalledAppInfo;
import com.lody.virtual.remote.PendingResultData;
import com.lody.virtual.remote.VDeviceInfo;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import dalvik.system.DexClassLoader;
import galaxy.demeHookPlugin.Hook_File_init;
import galaxy.yahfa.HookInfo;
import lab.galaxy.yahfa.HookMain;
import mirror.android.app.ActivityThread;
import mirror.android.app.ContextImpl;
import mirror.android.app.ContextImplKitkat;
import mirror.android.app.IActivityManager;
import mirror.android.app.LoadedApk;
import mirror.android.app.LoadedApkICS;
import mirror.android.app.LoadedApkKitkat;
import mirror.android.app.LoadedApkNougat;
import mirror.android.content.ContentProviderHolderOreo;
import mirror.android.content.res.CompatibilityInfo;
import mirror.android.providers.Settings;
import mirror.android.renderscript.RenderScriptCacheDir;
import mirror.android.view.CompatibilityInfoHolder;
import mirror.android.view.DisplayAdjustments;
import mirror.android.view.HardwareRenderer;
import mirror.android.view.RenderScript;
import mirror.android.view.ThreadedRenderer;
import mirror.com.android.internal.content.ReferrerIntent;
import mirror.dalvik.system.VMRuntime;
import mirror.java.lang.ThreadGroupN;


import com.lody.virtual.helper.utils.VLog;

import static com.lody.virtual.os.VUserHandle.getUserId;

public final class VClientImpl extends IVClient.Stub {
    private static final int NEW_INTENT = 11;
    private static final int RECEIVER = 12;
    private static final String TAG = VClientImpl.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static final VClientImpl gClient = new VClientImpl();
    private final H mH = new H();
    private ConditionVariable mTempLock;
    private Instrumentation mInstrumentation = AppInstrumentation.getDefault();
    private IBinder token;
    private int vuid;
    private VDeviceInfo deviceInfo;
    private AppBindData mBoundApplication;
    private Application mInitialApplication;
    private CrashHandler crashHandler;

    public static VClientImpl get() {
        return gClient;
    }

    public boolean isBound() {
        return mBoundApplication != null;
    }

    public VDeviceInfo getDeviceInfo() {
        if (deviceInfo == null) {
            synchronized (this) {
                if (deviceInfo == null) {
                    deviceInfo = VDeviceManager.get().getDeviceInfo(getUserId(vuid));
                }
            }
        }
        return deviceInfo;
    }

    public Application getCurrentApplication() {
        return mInitialApplication;
    }

    public String getCurrentPackage() {
        return mBoundApplication != null ?
                mBoundApplication.appInfo.packageName : VPackageManager.get().getNameForUid(getVUid());
    }

    public ApplicationInfo getCurrentApplicationInfo() {
        return mBoundApplication != null ? mBoundApplication.appInfo : null;
    }

    public CrashHandler getCrashHandler() {
        return crashHandler;
    }

    public void setCrashHandler(CrashHandler crashHandler) {
        this.crashHandler = crashHandler;
    }

    public int getVUid() {
        return vuid;
    }

    public int getBaseVUid() {
        return VUserHandle.getAppId(vuid);
    }

    public ClassLoader getClassLoader(ApplicationInfo appInfo) {
        Context context = createPackageContext(appInfo.packageName);
        return context.getClassLoader();
    }

    private void sendMessage(int what, Object obj) {
        Message msg = Message.obtain();
        msg.what = what;
        msg.obj = obj;
        mH.sendMessage(msg);
    }

    @Override
    public IBinder getAppThread() {
        return ActivityThread.getApplicationThread.call(VirtualCore.mainThread());
    }

    @Override
    public IBinder getToken() {
        return token;
    }

    public void initProcess(IBinder token, int vuid) {
        this.token = token;
        this.vuid = vuid;
    }

    private void handleNewIntent(NewIntentData data) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            intent = ReferrerIntent.ctor.newInstance(data.intent, data.creator);
        } else {
            intent = data.intent;
        }
        if (ActivityThread.performNewIntents != null) {
            if (Build.VERSION.SDK_INT >= AndroidVersionConstant.Oreo_8) {
                ActivityThread.performNewIntents.call(
                        VirtualCore.mainThread(),
                        data.token,
                        Collections.singletonList(intent),
                        true);
            } else {
                ActivityThread.performNewIntents.call(
                        VirtualCore.mainThread(),
                        data.token,
                        Collections.singletonList(intent)
                );
            }
        }
    }

    public void bindApplication(final String packageName, final String processName) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            VLog.i(" 主线程中 bindApplication " + packageName + "    processName:" + processName);
            bindApplicationNoCheck(packageName, processName, new ConditionVariable());
        } else {
            VLog.i(" 非主线程中 bindApplication " + packageName + "    processName:" + processName);
            final ConditionVariable lock = new ConditionVariable();
            VirtualRuntime.getUIHandler().post(new Runnable() {
                @Override
                public void run() {
                    bindApplicationNoCheck(packageName, processName, lock);
                    lock.open();
                }
            });
            lock.block();
        }
    }


    private void bindApplicationNoCheck(String packageName, String processName, ConditionVariable lock) {
        VDeviceInfo deviceInfo = getDeviceInfo();
        if (processName == null) {
            processName = packageName;
        }
        mTempLock = lock;
        try {
            setupUncaughtHandler();
        } catch (Throwable e) {
        }
        try {
            fixInstalledProviders();
        } catch (Throwable e) {
            e.printStackTrace();
            VLog.printThrowable(e);
        }
        mirror.android.os.Build.SERIAL.set(deviceInfo.serial);
        mirror.android.os.Build.DEVICE.set(Build.DEVICE.replace(" ", "_"));
        ActivityThread.mInitialApplication.set(
                VirtualCore.mainThread(),
                null
        );
        AppBindData data = new AppBindData();
        InstalledAppInfo info = VirtualCore.get().getInstalledAppInfo(packageName, 0);
        if (info == null) {
            new Exception("App not exist!").printStackTrace();
            Process.killProcess(0);
            System.exit(0);
        }
        data.appInfo = VPackageManager.get().getApplicationInfo(packageName, 0, getUserId(vuid));
        data.processName = processName;
        data.providers = VPackageManager.get().queryContentProviders(processName, getVUid(), PackageManager.GET_META_DATA);
        VLog.i(TAG, "Binding application " + data.appInfo.packageName + " (" + data.processName + ")");
        mBoundApplication = data;
        VirtualRuntime.setupRuntime(data.processName, data.appInfo);
        int targetSdkVersion = data.appInfo.targetSdkVersion;
        if (targetSdkVersion < Build.VERSION_CODES.GINGERBREAD) {
            StrictMode.ThreadPolicy newPolicy = new StrictMode.ThreadPolicy.Builder(StrictMode.getThreadPolicy()).permitNetwork().build();
            StrictMode.setThreadPolicy(newPolicy);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && targetSdkVersion < Build.VERSION_CODES.LOLLIPOP) {
            mirror.android.os.Message.updateCheckRecycle.call(targetSdkVersion);
        }
        if (VASettings.ENABLE_IO_REDIRECT) {
            startIOUniformer();
        }
        NativeEngine.launchEngine();
        Object mainThread = VirtualCore.mainThread();
        NativeEngine.startDexOverride();
        Context context = createPackageContext(data.appInfo.packageName);
        System.setProperty("java.io.tmpdir", context.getCacheDir().getAbsolutePath());
        File codeCacheDir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            codeCacheDir = context.getCodeCacheDir();
        } else {
            codeCacheDir = context.getCacheDir();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            if (HardwareRenderer.setupDiskCache != null) {
                HardwareRenderer.setupDiskCache.call(codeCacheDir);
            }
        } else {
            if (Build.VERSION.SDK_INT < AndroidVersionConstant.Q_10 && ThreadedRenderer.setupDiskCache != null) {
                ThreadedRenderer.setupDiskCache.call(codeCacheDir);
            } else {
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (RenderScriptCacheDir.setupDiskCache != null) {
                RenderScriptCacheDir.setupDiskCache.call(codeCacheDir);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (RenderScript.setupDiskCache != null) {
                RenderScript.setupDiskCache.call(codeCacheDir);
            }
        }
        Object boundApp = fixBoundApp(mBoundApplication);
        mBoundApplication.info = ContextImpl.mPackageInfo.get(context);
        mirror.android.app.ActivityThread.AppBindData.info.set(boundApp, data.info);
        VMRuntime.setTargetSdkVersion.call(VMRuntime.getRuntime.call(), data.appInfo.targetSdkVersion);


        Configuration configuration = context.getResources().getConfiguration();
        Object compatInfo = CompatibilityInfo.ctor.newInstance(data.appInfo, configuration.screenLayout, configuration.smallestScreenWidthDp, false);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            DisplayAdjustments.setCompatibilityInfo.call(LoadedApkNougat.mDisplayAdjustments.get(mBoundApplication.info), compatInfo);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                DisplayAdjustments.setCompatibilityInfo.call(ContextImplKitkat.mDisplayAdjustments.get(context), compatInfo);
            }
            DisplayAdjustments.setCompatibilityInfo.call(LoadedApkKitkat.mDisplayAdjustments.get(mBoundApplication.info), compatInfo);
        } else {
            CompatibilityInfoHolder.set.call(LoadedApkICS.mCompatibilityInfo.get(mBoundApplication.info), compatInfo);
        }
        boolean conflict = SpecialComponentList.isConflictingInstrumentation(packageName);
        if (!conflict) {
            InvocationStubManager.getInstance().checkEnv(AppInstrumentation.class);
        }

        //注入HOOK框架

        mInitialApplication = LoadedApk.makeApplication.call(data.info, false, null);
        VLog.i(" VClientImpl mInitialApplication is null? " + (mInitialApplication == null));
        if (mInitialApplication == null) {
            throw new NullPointerException(" mInitialApplication 初始化失败 需要详查原因");
        }
        VLog.i("准备将创建号的Application 放入ActivityThread.mInitialApplication");
        ActivityThread.localLOGV.set(mainThread, true);
        mirror.android.app.ActivityThread.mInitialApplication.set(mainThread, mInitialApplication);
        VLog.i("准备 ContextFixer.fixContext");
        ContextFixer.fixContext(mInitialApplication);
        if (Build.VERSION.SDK_INT >= 24 && "com.tencent.mm:recovery".equals(processName)) {
            fixWeChatRecovery(mInitialApplication);
        }
        VLog.i("准备 注册 Providers");
        if (data.providers != null) {
            installContentProviders(mInitialApplication, data.providers);
        }
        if (lock != null) {
            lock.open();
            mTempLock = null;
        }
        VLog.i("beforeApplicationCreate");
        VirtualCore.get().getComponentDelegate().beforeApplicationCreate(mInitialApplication);
        try {
            VLog.i("调用 Application.onCreate");
            mInstrumentation.callApplicationOnCreate(mInitialApplication);
            InvocationStubManager.getInstance().checkEnv(HCallbackStub.class);
            if (conflict) {
                InvocationStubManager.getInstance().checkEnv(AppInstrumentation.class);
            }
            Application createdApp = ActivityThread.mInitialApplication.get(mainThread);
            if (createdApp != null) {
                mInitialApplication = createdApp;
            }
        } catch (Exception e) {
            VLog.printException(e);
            if (!mInstrumentation.onException(mInitialApplication, e)) {
                VLog.i("Unable to create application exception is null ?  " + (e == null));
                throw new RuntimeException(
                        "Unable to create application " + (mInitialApplication == null ? " \"application is null \"" : mInitialApplication.getClass().getName())
                                + ": " + e.toString(), e);
            }
        }
        VActivityManager.get().appDoneExecuting();
//        VirtualCore.get().getComponentDelegate().afterApplicationCreate(mInitialApplication);
//        if (processName.equals(packageName)) {
//            VLog.i("调用 getVirtualController onCreateController");
//            VirtualCore.get().getVirtualController().onCreateController(mInitialApplication);
//        }


        /**--------------------- @2019.10.08 Hai-Yang Li  -------------------*/
        ClassLoader targetClassLoader = mInitialApplication.getClassLoader();

        // judge the apk is debuggable or not.
        if ((mInitialApplication.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            Log.i(TAG, "designed app debuggable is : false");
        } else {
            Log.i(TAG, "designed app debuggable is : true");
        }

        try {
            Log.i("VirtualHook", "Applying hook start : " + VPackageManager.get().getInstalledHookPlugins().length);
            for (String pluginName : VPackageManager.get().getInstalledHookPlugins()) {
                Log.i("VirtualHook", "Applying hook " + pluginName);
//                VLog.w("VirtualHook", "Applying hook "+pluginName);
                applyHookPlugin(VEnvironment.getPackageResourcePath(pluginName).getAbsolutePath(), VEnvironment.getPackageLibPath(pluginName).getAbsolutePath(), targetClassLoader);
            }
            Log.i("VirtualHook", "Applying hook end");
        } catch (Exception e) {
            Log.e("VirtualHook", "Applying hook exception",e);
            e.printStackTrace();
        }


        if (processName.equals(packageName)) {
            VLog.i("调用 getVirtualController onCreateController");
            VirtualCore.get().getVirtualController().onCreateController(mInitialApplication);
        }
    }


    /**--------------------- @2019.10.08 Hai-Yang Li  -------------------*/
    /**
     * From VirtualHook
     * The source code link : https://github.com/PAGalaxyLab/VirtualHook
     *
     * @param apkPath
     * @param libPath
     * @param appClassLoader
     */
    private void applyHookPlugin(String apkPath, String libPath, ClassLoader appClassLoader) {
        DexClassLoader dexClassLoader = new DexClassLoader(apkPath,
                VEnvironment.getDalvikCacheDirectory().getAbsolutePath(),
                libPath,
                appClassLoader);

        doHookDefault( appClassLoader);


        VirtualCore.get().getComponentDelegate().afterApplicationCreate(mInitialApplication);

    }


    public static void doHookDefault(ClassLoader originClassLoader) {
        try {


//            String[] hookItemNames = HookInfo.hookItemNames;
//
//            int count = hookItemNames.length;
//
//            for (int i = 0; i < count; ++i) {
//                String hookItemName = hookItemNames[i];
//                doHookItemDefault(hookItemName, originClassLoader);
//            }
            Log.w("VirtualHook", Hook_File_init.class.getName());

            doHookItemDefault(Hook_File_init.class.getName(), originClassLoader);
        } catch (Exception e) {
            Log.e("VirtualHook", "doHookDefault hook exception",e);
            e.printStackTrace();
        }

    }

    private static void doHookItemDefault(String hookItemName, ClassLoader originClassLoader) {
        try {
            Log.i("VirtualHook", "Start hooking with item " + hookItemName);
            Class<?> hookItem = Class.forName(hookItemName);
            String className = (String) hookItem.getField("className").get((Object) null);
            String methodName = (String) hookItem.getField("methodName").get((Object) null);
            String methodSig = (String) hookItem.getField("methodSig").get((Object) null);
            if (className == null || className.equals("")) {
                Log.w("VirtualHook", "No target class. Skipping...");
                return;
            }

            Class<?> clazz = Class.forName(className, true, originClassLoader);
            if (Modifier.isAbstract(clazz.getModifiers())) {
                Log.w("VirtualHook", "Hook may fail for abstract class: " + className);
            }

            Method hook = null;
            Method backup = null;
            Method[] var10 = hookItem.getDeclaredMethods();
            int var11 = var10.length;

            for (int var12 = 0; var12 < var11; ++var12) {
                Method method = var10[var12];
                if (method.getName().equals("hook") && Modifier.isStatic(method.getModifiers())) {
                    hook = method;
                } else if (method.getName().equals("backup") && Modifier.isStatic(method.getModifiers())) {
                    backup = method;
                }
            }

            if (hook == null) {
                Log.e("VirtualHook", "Cannot find hook for " + methodName);
                return;
            }

            HookMain.findAndBackupAndHook(clazz, methodName, methodSig, hook, backup);
        } catch (Exception e) {
            Log.e("VirtualHook", "doHookItemDefault hook exception",e);
            e.printStackTrace();
        }

    }


    private void fixWeChatRecovery(Application app) {
        try {
            Field field = app.getClassLoader().loadClass("com.tencent.recovery.Recovery").getField("context");
            field.setAccessible(true);
            if (field.get(null) != null) {
                return;
            }
            field.set(null, app.getBaseContext());
        } catch (Throwable e) {
            e.printStackTrace();
            VLog.printThrowable(e);
        }
    }

    private void setupUncaughtHandler() {
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) {
            root = root.getParent();
        }
        ThreadGroup newRoot = new RootThreadGroup(root);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            final List<ThreadGroup> groups = mirror.java.lang.ThreadGroup.groups.get(root);
            synchronized (groups) {
                List<ThreadGroup> newGroups = new ArrayList<>(groups);
                newGroups.remove(newRoot);
                mirror.java.lang.ThreadGroup.groups.set(newRoot, newGroups);
                groups.clear();
                groups.add(newRoot);
                mirror.java.lang.ThreadGroup.groups.set(root, groups);
                for (ThreadGroup group : newGroups) {
                    if (group == newRoot) continue;
                    mirror.java.lang.ThreadGroup.parent.set(group, newRoot);
                }
            }
        } else {
            final ThreadGroup[] groups = ThreadGroupN.groups.get(root);
            synchronized (groups) {
                ThreadGroup[] newGroups = groups.clone();
                ThreadGroupN.groups.set(newRoot, newGroups);
                ThreadGroupN.groups.set(root, new ThreadGroup[]{newRoot});
                VLog.d("在android 8  以后 ThreadGroupN 这里没有搞懂是做什么的，前期先帮忙注释掉 后续在开发");
            }
        }
    }

    @SuppressLint("SdCardPath")
    private void startIOUniformer() {
        ApplicationInfo info = mBoundApplication.appInfo;
        int userId = VUserHandle.myUserId();
        String wifiMacAddressFile = deviceInfo.getWifiFile(userId).getPath();
        NativeEngine.redirectDirectory("/sys/class/net/wlan0/address", wifiMacAddressFile);
        NativeEngine.redirectDirectory("/sys/class/net/eth0/address", wifiMacAddressFile);
        NativeEngine.redirectDirectory("/sys/class/net/wifi/address", wifiMacAddressFile);
        NativeEngine.redirectDirectory("/data/data/" + info.packageName, info.dataDir);
        NativeEngine.redirectDirectory("/data/user/0/" + info.packageName, info.dataDir);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NativeEngine.redirectDirectory("/data/user_de/0/" + info.packageName, info.dataDir);
        }
        String libPath = VEnvironment.getAppLibDirectory(info.packageName).getAbsolutePath();
        String userLibPath = new File(VEnvironment.getUserSystemDirectory(userId), info.packageName + "/lib").getAbsolutePath();
        NativeEngine.redirectDirectory(userLibPath, libPath);
        NativeEngine.redirectDirectory("/data/data/" + info.packageName + "/lib/", libPath);
        NativeEngine.redirectDirectory("/data/user/0/" + info.packageName + "/lib/", libPath);
        VirtualStorageManager vsManager = VirtualStorageManager.get();
        String vsPath = vsManager.getVirtualStorage(info.packageName, userId);
        boolean enable = vsManager.isVirtualStorageEnable(info.packageName, userId);
        if (enable && vsPath != null) {
            File vsDirectory = new File(vsPath);
            if (vsDirectory.exists() || vsDirectory.mkdirs()) {
                HashSet<String> mountPoints = getMountPoints();
                for (String mountPoint : mountPoints) {
                    NativeEngine.redirectDirectory(mountPoint, vsPath);
                }
            }
        }
        NativeEngine.enableIORedirect();
    }

    @SuppressLint("SdCardPath")
    private HashSet<String> getMountPoints() {
        HashSet<String> mountPoints = new HashSet<>(3);
        mountPoints.add("/mnt/sdcard/");
        mountPoints.add("/sdcard/");
        String[] points = StorageManagerCompat.getAllPoints(VirtualCore.get().getContext());
        if (points != null) {
            Collections.addAll(mountPoints, points);
        }
        return mountPoints;
    }

    private Context createPackageContext(String packageName) {
        try {
            Context hostContext = VirtualCore.get().getContext();
            return hostContext.createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            VirtualRuntime.crash(new RemoteException());
        }
        throw new RuntimeException();
    }

    private Object fixBoundApp(AppBindData data) {
        Object thread = VirtualCore.mainThread();
        Object boundApp = mirror.android.app.ActivityThread.mBoundApplication.get(thread);
        mirror.android.app.ActivityThread.AppBindData.appInfo.set(boundApp, data.appInfo);
        mirror.android.app.ActivityThread.AppBindData.processName.set(boundApp, data.processName);
        mirror.android.app.ActivityThread.AppBindData.instrumentationName.set(
                boundApp,
                new ComponentName(data.appInfo.packageName, Instrumentation.class.getName())
        );
        ActivityThread.AppBindData.providers.set(boundApp, data.providers);
        return boundApp;
    }

    private void installContentProviders(Context app, List<ProviderInfo> providers) {
        long origId = Binder.clearCallingIdentity();
        Object mainThread = VirtualCore.mainThread();
        try {
            for (ProviderInfo cpi : providers) {
                try {
                    ActivityThread.installProvider(mainThread, app, cpi, null);
                } catch (Throwable e) {
                    e.printStackTrace();
                    VLog.printThrowable(e);
                }
            }
        } catch (Exception e) {
            VLog.printException(e);
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    @Override
    public IBinder acquireProviderClient(ProviderInfo info) {
        if (mTempLock != null) {
            mTempLock.block();
        }
        if (!isBound()) {
            VClientImpl.get().bindApplication(info.packageName, info.processName);
        }
        IInterface provider = null;
        String[] authorities = info.authority.split(";");
        String authority = authorities.length == 0 ? info.authority : authorities[0];
        ContentResolver resolver = VirtualCore.get().getContext().getContentResolver();
        ContentProviderClient client = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                client = resolver.acquireUnstableContentProviderClient(authority);
            } else {
                client = resolver.acquireContentProviderClient(authority);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            VLog.printThrowable(e);
        }
        if (client != null) {
            provider = mirror.android.content.ContentProviderClient.mContentProvider.get(client);
            client.release();
        }
        return provider != null ? provider.asBinder() : null;
    }

    private void fixInstalledProviders() {
        clearSettingProvider();
        Map clientMap = ActivityThread.mProviderMap.get(VirtualCore.mainThread());
        VLog.i("当前 ActivityThread.mProviderMap 里面是否存了 " + clientMap.size() + " 个 ProviderClientRecord");
        for (Object clientRecord : clientMap.values()) {
            if (BuildCompat.isOreo()) {
                IInterface provider = ActivityThread.ProviderClientRecordJB.mProvider.get(clientRecord);
                Object holder = ActivityThread.ProviderClientRecordJB.mHolder.get(clientRecord);
                if (holder == null) {
                    continue;
                }
                ProviderInfo info = ContentProviderHolderOreo.info.get(holder);
                VLog.i("当前 ProviderClientRecord " + clientRecord + "    provider:" + provider + "    holder:" + holder + "    info.authority:" + info.authority);
                if (!info.authority.startsWith(VASettings.STUB_CP_AUTHORITY)) {
                    provider = ProviderHook.createProxy(true, info.authority, provider);
                    ActivityThread.ProviderClientRecordJB.mProvider.set(clientRecord, provider);
                    ContentProviderHolderOreo.provider.set(holder, provider);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                IInterface provider = ActivityThread.ProviderClientRecordJB.mProvider.get(clientRecord);
                Object holder = ActivityThread.ProviderClientRecordJB.mHolder.get(clientRecord);
                if (holder == null) {
                    continue;
                }
                ProviderInfo info = IActivityManager.ContentProviderHolder.info.get(holder);
                if (!info.authority.startsWith(VASettings.STUB_CP_AUTHORITY)) {
                    provider = ProviderHook.createProxy(true, info.authority, provider);
                    ActivityThread.ProviderClientRecordJB.mProvider.set(clientRecord, provider);
                    IActivityManager.ContentProviderHolder.provider.set(holder, provider);
                }
            } else {
                String authority = ActivityThread.ProviderClientRecord.mName.get(clientRecord);
                IInterface provider = ActivityThread.ProviderClientRecord.mProvider.get(clientRecord);
                if (provider != null && !authority.startsWith(VASettings.STUB_CP_AUTHORITY)) {
                    provider = ProviderHook.createProxy(true, authority, provider);
                    ActivityThread.ProviderClientRecord.mProvider.set(clientRecord, provider);
                }
            }
        }
    }

    private void clearSettingProvider() {
        Object cache;
        cache = Settings.System.sNameValueCache.get();
        if (cache != null) {
            clearContentProvider(cache);
        }
        cache = Settings.Secure.sNameValueCache.get();
        if (cache != null) {
            clearContentProvider(cache);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && Settings.Global.TYPE != null) {
            cache = Settings.Global.sNameValueCache.get();
            if (cache != null) {
                clearContentProvider(cache);
            }
        }
    }

    private static void clearContentProvider(Object cache) {
        if (BuildCompat.isOreo()) {
            Object holder = Settings.NameValueCacheOreo.mProviderHolder.get(cache);
            if (holder != null) {
                Settings.ContentProviderHolder.mContentProvider.set(holder, null);
            }
        } else {
            Settings.NameValueCache.mContentProvider.set(cache, null);
        }
    }

    @Override
    public void finishActivity(IBinder token) {
        VActivityManager.get().finishActivity(token);
    }

    @Override
    public void scheduleNewIntent(String creator, IBinder token, Intent intent) {
        NewIntentData data = new NewIntentData();
        data.creator = creator;
        data.token = token;
        data.intent = intent;
        sendMessage(NEW_INTENT, data);
    }

    @Override
    public void scheduleReceiver(String processName, ComponentName component, Intent intent, PendingResultData resultData) {
        ReceiverData receiverData = new ReceiverData();
        receiverData.resultData = resultData;
        receiverData.intent = intent;
        receiverData.component = component;
        receiverData.processName = processName;
        sendMessage(RECEIVER, receiverData);
    }

    private void handleReceiver(ReceiverData data) {
        BroadcastReceiver.PendingResult result = data.resultData.build();
        try {
            if (!isBound()) {
                bindApplication(data.component.getPackageName(), data.processName);
            }
            Context context = mInitialApplication.getBaseContext();
            Context receiverContext = ContextImpl.getReceiverRestrictedContext.call(context);
            String className = data.component.getClassName();
            Log.e("gy", "handler Receiver: " + className);
            BroadcastReceiver receiver = (BroadcastReceiver) context.getClassLoader().loadClass(className).newInstance();
            mirror.android.content.BroadcastReceiver.setPendingResult.call(receiver, result);
            data.intent.setExtrasClassLoader(context.getClassLoader());
            if (data.intent.getComponent() == null) {
                data.intent.setComponent(data.component);
            }
            receiver.onReceive(receiverContext, data.intent);
            if (mirror.android.content.BroadcastReceiver.getPendingResult.call(receiver) != null) {
                result.finish();
            }
        } catch (Exception e) {
            e.printStackTrace();
            VLog.printException(e);
            throw new RuntimeException(
                    "Unable to start receiver " + data.component
                            + ": " + e.toString(), e);
        }
        VActivityManager.get().broadcastFinish(data.resultData);
    }

    @Override
    public IBinder createProxyService(ComponentName component, IBinder binder) {
        return ProxyServiceFactory.getProxyService(getCurrentApplication(), component, binder);
    }

    @Override
    public String getDebugInfo() {
        return "process : " + VirtualRuntime.getProcessName() + "\n" +
                "initialPkg : " + VirtualRuntime.getInitialPackageName() + "\n" +
                "vuid : " + vuid;
    }

    private static class RootThreadGroup extends ThreadGroup {
        RootThreadGroup(ThreadGroup parent) {
            super(parent, "VA-Root");
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            CrashHandler handler = VClientImpl.gClient.crashHandler;
            if (handler != null) {
                handler.handleUncaughtException(t, e);
            } else {
                VLog.e("uncaught", e);
                System.exit(0);
            }
        }
    }

    private final class NewIntentData {
        String creator;
        IBinder token;
        Intent intent;
    }

    private final class AppBindData {
        String processName;
        ApplicationInfo appInfo;
        List<ProviderInfo> providers;
        Object info;
    }

    private final class ReceiverData {
        PendingResultData resultData;
        Intent intent;
        ComponentName component;
        String processName;
    }

    private class H extends Handler {
        private H() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NEW_INTENT: {
                    handleNewIntent((NewIntentData) msg.obj);
                }
                break;
                case RECEIVER: {
                    handleReceiver((ReceiverData) msg.obj);
                }
            }
        }
    }
}
