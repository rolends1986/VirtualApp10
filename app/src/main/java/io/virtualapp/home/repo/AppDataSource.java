package io.virtualapp.home.repo;
import android.content.Context;
import com.lody.virtual.remote.InstallResult;
import org.jdeferred.Promise;
import java.io.File;
import java.util.List;
import io.virtualapp.home.models.AppData;
import io.virtualapp.home.models.AppInfo;
import io.virtualapp.home.models.AppInfoLite;
public interface AppDataSource {
    Promise<List<AppData>, Throwable, Void> getVirtualApps();
    Promise<List<AppInfo>, Throwable, Void> getInstalledApps(Context context);
    Promise<List<AppInfo>, Throwable, Void> getStorageApps(Context context, File rootDir);

    /**
     * 获取apk的信息
     * @param context
     * @param apkFile apk文件路径
     * @return
     */
    AppInfo getAppInfo(Context context, File apkFile);
    InstallResult addVirtualApp(AppInfoLite info);
    boolean removeVirtualApp(String packageName, int userId);
}
