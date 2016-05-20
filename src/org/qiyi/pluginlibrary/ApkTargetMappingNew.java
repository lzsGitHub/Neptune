package org.qiyi.pluginlibrary;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.qiyi.plugin.manager.ProxyEnvironmentNew;
import org.qiyi.pluginlibrary.ErrorType.ErrorType;
import org.qiyi.pluginlibrary.install.PluginInstaller;
import org.qiyi.pluginlibrary.plugin.TargetMapping;
import org.qiyi.pluginlibrary.utils.ResolveInfoUtil;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/**
 * @author huangbo 插件apk资源初始化
 */
public class ApkTargetMappingNew implements TargetMapping, Parcelable {

    private String versionName;
    private int versionCode;
    private String packageName;
    private String applicationClassName;
    private String defaultActivityName;
    private PermissionInfo[] permissions;
    /** Apk's package info */
    private PackageInfo packageInfo;
    private Bundle metaData;

    private String dataDir;
    private String nativeLibraryDir;

    private boolean isDataNeedPrefix = false;
    /** Save all activity's resolve info */
    private Map<String, ActivityIntentInfo> mActivitiyIntentInfos = new HashMap<String, ActivityIntentInfo>(0);

    /** Save all service's resolve info */
    private Map<String, ServiceIntentInfo> mServiceIntentInfos = new HashMap<String, ServiceIntentInfo>(0);

    /** Save all receiver's resolve info */
    private Map<String, ReceiverIntentInfo> mReceiverIntentInfos = new HashMap<String, ReceiverIntentInfo>(0);

    public ApkTargetMappingNew(Context context, File apkFile) {
        init(context, apkFile);
    }

    protected ApkTargetMappingNew(Parcel in) {
        versionName = in.readString();
        versionCode = in.readInt();
        packageName = in.readString();
        applicationClassName = in.readString();
        defaultActivityName = in.readString();
        permissions = in.createTypedArray(PermissionInfo.CREATOR);
        packageInfo = in.readParcelable(PackageInfo.class.getClassLoader());
        metaData = in.readBundle();
        dataDir = in.readString();
        nativeLibraryDir = in.readString();
        isDataNeedPrefix = in.readByte() != 0;

        final Bundle activityStates = in.readBundle(ActivityIntentInfo.class.getClassLoader());

        for (String key : activityStates.keySet()) {
            final ActivityIntentInfo state = activityStates.getParcelable(key);
            mActivitiyIntentInfos.put(key, state);
        }

        final Bundle serviceStates = in.readBundle(ServiceIntentInfo.class.getClassLoader());
        for (String key : serviceStates.keySet()) {
            final ServiceIntentInfo state = serviceStates.getParcelable(key);
            mServiceIntentInfos.put(key, state);
        }

        final Bundle receiverStates = in.readBundle(ReceiverIntentInfo.class.getClassLoader());
        for (String key : receiverStates.keySet()) {
            final ReceiverIntentInfo state = receiverStates.getParcelable(key);
            mReceiverIntentInfos.put(key, state);
        }

    }

    public static final Creator<ApkTargetMappingNew> CREATOR = new Creator<ApkTargetMappingNew>() {
        @Override
        public ApkTargetMappingNew createFromParcel(Parcel in) {
            return new ApkTargetMappingNew(in);
        }

        @Override
        public ApkTargetMappingNew[] newArray(int size) {
            return new ApkTargetMappingNew[size];
        }
    };

    private void init(Context context, File apkFile) {
        try {
            packageInfo = context.getPackageManager().getPackageArchiveInfo(apkFile.getAbsolutePath(),
                    PackageManager.GET_ACTIVITIES | PackageManager.GET_PERMISSIONS | PackageManager.GET_META_DATA
                            | PackageManager.GET_SERVICES | PackageManager.GET_CONFIGURATIONS);
            if (packageInfo == null) {
                throw new Exception("getPackageArchiveInfo is null for file: " + apkFile.getAbsolutePath());
            }
            packageName = packageInfo.packageName;
            applicationClassName = packageInfo.applicationInfo.className;
            permissions = packageInfo.permissions;
            versionCode = packageInfo.versionCode;
            versionName = packageInfo.versionName;

            // 2.2 上获取不到application的meta-data，所以取默认activity里的meta作为开关
            if (packageInfo.activities != null && packageInfo.activities.length > 0) {
                defaultActivityName = packageInfo.activities[0].name;
                metaData = packageInfo.activities[0].metaData;
            }
            if (metaData == null) {
                metaData = packageInfo.applicationInfo.metaData;
            }
            packageInfo.applicationInfo.publicSourceDir = apkFile.getAbsolutePath();
            dataDir = new File(PluginInstaller.getPluginappRootPath(context), packageName).getAbsolutePath();
            nativeLibraryDir = new File(dataDir, PluginInstaller.NATIVE_LIB_PATH).getAbsolutePath();
            if (metaData != null) {
                isDataNeedPrefix = metaData.getBoolean(ProxyEnvironmentNew.META_KEY_DATA_WITH_PREFIX);
            }
            ResolveInfoUtil.parseResolveInfo(apkFile.getAbsolutePath(), this);
        } catch (RuntimeException e) {
            ProxyEnvironmentNew.deliverPlug(context, false, packageName, ErrorType.ERROR_CLIENT_LOAD_INIT_APK_FAILE);
            e.printStackTrace();
            return;
        } catch (Exception e) {
            ProxyEnvironmentNew.deliverPlug(context, false, packageName, ErrorType.ERROR_CLIENT_LOAD_INIT_APK_FAILE);
            e.printStackTrace();
        }
    }

    public String getDataDir() {
        return dataDir;
    }

    public String getnativeLibraryDir() {
        return nativeLibraryDir;
    }

    public boolean isDataNeedPrefix() {
        return isDataNeedPrefix;
    }

    public ActivityInfo findActivityByClassName(String activityClsName) {
        if (packageInfo == null || packageInfo.activities == null) {
            return null;
        }
        for (ActivityInfo act : packageInfo.activities) {
            if (act != null && act.name.equals(activityClsName)) {
                return act;
            }
        }
        return null;
    }

    /**
     * Resolve activity by intent
     *
     * @param intent
     * @return
     */
    public ActivityInfo resolveActivity(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (mActivitiyIntentInfos != null) {
            ComponentName compname = intent.getComponent();
            String className = null;
            if (compname != null) {
                className = compname.getClassName();
            }
            if (!TextUtils.isEmpty(className)) {
                ActivityIntentInfo act = mActivitiyIntentInfos.get(className);
                if (act != null) {
                    return act.mInfo;
                }
            } else {
                for (ActivityIntentInfo info : mActivitiyIntentInfos.values()) {
                    if (info != null && info.mFilter != null) {
                        for (IntentFilter filter : info.mFilter) {
                            if (filter.match(intent.getAction(), null, intent.getScheme(), intent.getData(), intent.getCategories(),
                                    "TAG") > 0) {
                                return info.mInfo;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public ActivityInfo findReceiverByClassName(String className) {
        if (packageInfo == null || packageInfo.receivers == null) {
            return null;
        }
        for (ActivityInfo receiver : packageInfo.receivers) {
            if (receiver.name.equals(className)) {
                return receiver;
            }
        }
        return null;
    }

    public ServiceInfo findServiceByClassName(String className) {
        if (packageInfo == null || packageInfo.services == null) {
            return null;
        }
        for (ServiceInfo service : packageInfo.services) {
            if (service.name.equals(className)) {
                return service;
            }
        }
        return null;

    }

    public ServiceInfo resolveService(Intent intent) {
        if (intent == null) {
            return null;
        }
        ComponentName compname = intent.getComponent();
        if (compname != null && mServiceIntentInfos != null) {
            String className = compname.getClassName();
            if (!TextUtils.isEmpty(className)) {
                ServiceIntentInfo service = mServiceIntentInfos.get(className);
                if (service != null) {
                    return service.mInfo;
                }
            } else {
                for (ServiceIntentInfo info : mServiceIntentInfos.values()) {
                    if (info != null && info.mFilter != null) {
                        for (IntentFilter filter : info.mFilter) {
                            if (filter.match(intent.getAction(), null, intent.getScheme(), intent.getData(), intent.getCategories(),
                                    "TAG") > 0) {
                                return info.mInfo;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    @Override
    public String getApplicationClassName() {
        return applicationClassName;
    }

    @Override
    public String getDefaultActivityName() {
        return defaultActivityName;
    }

    public PermissionInfo[] getPermissions() {
        return permissions;
    }

    @Override
    public String getVersionName() {
        return versionName;
    }

    @Override
    public int getVersionCode() {
        return versionCode;
    }

    @Override
    public PackageInfo getPackageInfo() {
        return packageInfo;
    }

    @Override
    public int getThemeResource(String activity) {
        ActivityInfo info = getActivityInfo(activity);
        if (info == null) {
            // 支持不同系统的默认Theme
            if (Build.VERSION.SDK_INT >= 14) {
                return android.R.style.Theme_DeviceDefault;
            } else {
                return android.R.style.Theme;
            }
        }

        /**
         * 指定默认theme为android.R.style.Theme
         * 有些OPPO手机上，把theme设置成0，其实会把Theme设置成holo主题
         * ，带ActionBar，导致插件黑屏，目前插件SDK不支持ActionBar
         */
        if (info == null || info.getThemeResource() == 0) {
            // 支持不同系统的默认Theme
            if (Build.VERSION.SDK_INT >= 14) {
                return android.R.style.Theme_DeviceDefault;
            } else {
                return android.R.style.Theme;
            }
        }
        return info.getThemeResource();
    }

    @Override
    public ActivityInfo getActivityInfo(String activity) {
        if (!TextUtils.isEmpty(activity) && mActivitiyIntentInfos != null) {
            ActivityIntentInfo info = mActivitiyIntentInfos.get(activity);
            if (info != null) {
                return info.mInfo;
            }
        }
        return null;
    }

    @Override
    public ServiceInfo getServiceInfo(String service) {
        if (!TextUtils.isEmpty(service) && mServiceIntentInfos != null) {
            ServiceIntentInfo info = mServiceIntentInfos.get(service);
            if (info != null) {
                return info.mInfo;
            }
        }
        return null;
    }

    /**
     * @return the metaData
     */
    public Bundle getMetaData() {
        return metaData;
    }

    public void addActivity(ActivityIntentInfo activity) {
        if (mActivitiyIntentInfos == null) {
            mActivitiyIntentInfos = new HashMap<String, ActivityIntentInfo>(0);
        }
        mActivitiyIntentInfos.put(activity.mInfo.name, activity);
    }

    public void addReceiver(ReceiverIntentInfo receiver) {
        if (mReceiverIntentInfos == null) {
            mReceiverIntentInfos = new HashMap<String, ReceiverIntentInfo>(0);
        }
        // 此时的activityInfo 表示 receiverInfo
        mReceiverIntentInfos.put(receiver.mInfo.name, receiver);
    }

    public void addService(ServiceIntentInfo service) {
        if (mServiceIntentInfos == null) {
            mServiceIntentInfos = new HashMap<String, ServiceIntentInfo>(0);
        }
        mServiceIntentInfos.put(service.mInfo.name, service);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(versionName);
        parcel.writeInt(versionCode);
        parcel.writeString(packageName);
        parcel.writeString(applicationClassName);
        parcel.writeString(defaultActivityName);
        parcel.writeTypedArray(permissions, i);
        parcel.writeParcelable(packageInfo, i);
        parcel.writeBundle(metaData);
        parcel.writeString(dataDir);
        parcel.writeString(nativeLibraryDir);
        parcel.writeByte((byte) (isDataNeedPrefix ? 1 : 0));

        final Bundle activityStates = new Bundle();
        for (String uri : mActivitiyIntentInfos.keySet()) {
            final ActivityIntentInfo aii = mActivitiyIntentInfos.get(uri);
            activityStates.putParcelable(uri.toString(), aii);
        }
        parcel.writeBundle(activityStates);

        final Bundle serviceStates = new Bundle();
        for (String uri : mServiceIntentInfos.keySet()) {
            final ServiceIntentInfo aii = mServiceIntentInfos.get(uri);
            serviceStates.putParcelable(uri.toString(), aii);
        }
        parcel.writeBundle(serviceStates);

        final Bundle receiverStates = new Bundle();
        for (String uri : mReceiverIntentInfos.keySet()) {
            final ReceiverIntentInfo aii = mReceiverIntentInfos.get(uri);
            receiverStates.putParcelable(uri.toString(), aii);
        }
        parcel.writeBundle(receiverStates);

    }

    public final static class ActivityIntentInfo extends IntentInfo implements Parcelable {
        public final ActivityInfo mInfo;

        public ActivityIntentInfo(final ActivityInfo info) {
            mInfo = info;
        }

        protected ActivityIntentInfo(Parcel in) {
            super(in);
            mInfo = ActivityInfo.CREATOR.createFromParcel(in);
        }

        public static final Creator<ActivityIntentInfo> CREATOR = new Creator<ActivityIntentInfo>() {
            @Override
            public ActivityIntentInfo createFromParcel(Parcel in) {
                return new ActivityIntentInfo(in);
            }

            @Override
            public ActivityIntentInfo[] newArray(int size) {
                return new ActivityIntentInfo[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            super.writeToParcel(parcel, i);
            if (mInfo != null) {
                mInfo.writeToParcel(parcel, i);
            }
        }
    }

    public final static class ServiceIntentInfo extends IntentInfo implements Parcelable {
        public final ServiceInfo mInfo;

        public ServiceIntentInfo(final ServiceInfo info) {
            mInfo = info;
        }

        protected ServiceIntentInfo(Parcel in) {
            super(in);
            mInfo = ServiceInfo.CREATOR.createFromParcel(in);

        }

        public static final Creator<ServiceIntentInfo> CREATOR = new Creator<ServiceIntentInfo>() {
            @Override
            public ServiceIntentInfo createFromParcel(Parcel in) {
                return new ServiceIntentInfo(in);
            }

            @Override
            public ServiceIntentInfo[] newArray(int size) {
                return new ServiceIntentInfo[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            super.writeToParcel(parcel, i);
            if (mInfo != null) {
                mInfo.writeToParcel(parcel, i);
            }
        }
    }

    public final static class ReceiverIntentInfo extends IntentInfo implements Parcelable {
        public final ActivityInfo mInfo;

        public ReceiverIntentInfo(final ActivityInfo info) {
            mInfo = info;
        }

        protected ReceiverIntentInfo(Parcel in) {
            super(in);
            mInfo = ActivityInfo.CREATOR.createFromParcel(in);
        }

        public static final Creator<ReceiverIntentInfo> CREATOR = new Creator<ReceiverIntentInfo>() {
            @Override
            public ReceiverIntentInfo createFromParcel(Parcel in) {
                return new ReceiverIntentInfo(in);
            }

            @Override
            public ReceiverIntentInfo[] newArray(int size) {
                return new ReceiverIntentInfo[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            super.writeToParcel(parcel, i);
            if (mInfo != null) {
                mInfo.writeToParcel(parcel, i);
            }
        }
    }

    public static class IntentInfo implements Parcelable {
        public List<IntentFilter> mFilter;

        protected IntentInfo() {
        }

        protected IntentInfo(Parcel in) {
            mFilter = in.createTypedArrayList(IntentFilter.CREATOR);
        }

        public static final Creator<IntentInfo> CREATOR = new Creator<IntentInfo>() {
            @Override
            public IntentInfo createFromParcel(Parcel in) {
                return new IntentInfo(in);
            }

            @Override
            public IntentInfo[] newArray(int size) {
                return new IntentInfo[size];
            }
        };

        public void setFilter(List<IntentFilter> filters) {
            mFilter = filters;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            if (mFilter != null) {
                dest.writeTypedList(mFilter);
            }
        }
    }
}