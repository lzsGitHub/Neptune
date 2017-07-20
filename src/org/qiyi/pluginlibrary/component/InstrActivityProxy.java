package org.qiyi.pluginlibrary.component;

import android.app.Activity;
import android.app.Fragment;
import android.app.assist.AssistContent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import org.qiyi.pluginlibrary.ErrorType.ErrorType;
import org.qiyi.pluginlibrary.PServiceSupervisor;
import org.qiyi.pluginlibrary.PluginActivityControl;
import org.qiyi.pluginlibrary.PluginServiceWrapper;
import org.qiyi.pluginlibrary.constant.IIntentConstant;
import org.qiyi.pluginlibrary.context.CMContextWrapperNew;
import org.qiyi.pluginlibrary.listenter.IResourchStaticsticsControllerManager;
import org.qiyi.pluginlibrary.plugin.InterfaceToGetHost;
import org.qiyi.pluginlibrary.runtime.PluginLoadedApk;
import org.qiyi.pluginlibrary.runtime.PluginManager;
import org.qiyi.pluginlibrary.utils.ComponetFinder;
import org.qiyi.pluginlibrary.utils.ContextUtils;
import org.qiyi.pluginlibrary.utils.IntentUtils;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginlibrary.utils.ReflectionUtils;
import org.qiyi.pluginlibrary.utils.ResourcesToolForPlugin;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;

/**
 * 插件Activity代理
 */
public class InstrActivityProxy extends Activity implements InterfaceToGetHost {
    private static final String TAG = InstrActivityProxy.class.getSimpleName();

    private PluginLoadedApk mLoadedApk;
    private PluginActivityControl mPluginContrl;
    private CMContextWrapperNew mPluginContextWrapper;
    private String mPluginPackage = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onCreate....");
        String pluginActivityName = null;
        String pluginPkgName = null;
        String[] pkgAndCls = parsePkgAndClsFromIntent();
        if (pkgAndCls != null) {
            pluginPkgName = pkgAndCls[0];
            pluginActivityName = pkgAndCls[1];
        } else {
            PluginManager.deliver(this, false, pluginPkgName,
                    ErrorType.ERROR_CLIENT_GET_PKG_AND_CLS_FAIL);
            PluginDebugLog.log(TAG, "Pkg or activity is null in LActivityProxy, just return!");
            this.finish();
            return;
        }

        if (!tryToInitPluginLoadApk(pluginPkgName)) {
            this.finish();
            PluginDebugLog.log(TAG, "mPluginEnv is null in LActivityProxy, just return!");
            return;
        }
        if (!PluginManager.isPluginLoadedAndInit(pluginPkgName)) {
            Intent i = new Intent();
            i.setComponent(new ComponentName(pluginPkgName,
                    IIntentConstant.EXTRA_VALUE_LOADTARGET_STUB));
            PluginManager.readyToStartSpecifyPlugin(this,null,i,true);
        }
        ContextUtils.notifyHostPluginStarted(this, getIntent());
        Activity mPluginActivity = loadPluginActivity(mLoadedApk, pluginActivityName);
        if (null == mPluginActivity) {
            PluginManager.deliver(this, false, pluginPkgName,
                    ErrorType.ERROR_CLIENT_FILL_PLUGIN_ACTIVITY_FAIL);
            PluginDebugLog.log(TAG, "Cann't get pluginActivityName class finish!");
            this.finish();
            return;
        }
        try {
            mPluginContrl = new PluginActivityControl(InstrActivityProxy.this, mPluginActivity,
                    mLoadedApk.getPluginApplication(), mLoadedApk.getPluginInstrument());
        } catch (Exception e1) {
            PluginManager.deliver(this, false, pluginPkgName,
                    ErrorType.ERROR_CLIENT_CREATE_PLUGIN_ACTIVITY_CONTROL_FAIL);
            e1.printStackTrace();
            this.finish();
            return;
        }
        if (null != mPluginContrl) {
            mPluginContextWrapper = new CMContextWrapperNew(InstrActivityProxy.this.getBaseContext(), pluginPkgName);
            ActivityInfo actInfo = mLoadedApk.getActivityInfoByClassName(pluginActivityName);
            if (actInfo != null) {
                changeActivityInfo(this, pluginPkgName, actInfo);
            }
            mPluginContrl.dispatchProxyToPlugin(mLoadedApk.getPluginInstrument(), mPluginContextWrapper, pluginPkgName);
            int resTheme = mLoadedApk.getActivityThemeResourceByClassName(pluginActivityName);
            setTheme(resTheme);
            // Set plugin's default theme.
            mPluginActivity.setTheme(resTheme);
//            try {
//                if (getParent() == null) {
//                    mLoadedApk.getActivityStackSupervisor().pushActivityToStack(this);
//                }
//                mPluginContrl.callOnCreate(savedInstanceState);
//                mPluginContrl.getPluginRef().set("mDecor", this.getWindow().getDecorView());
//                PluginManager.sendPluginLoadedBroadcast(InstrActivityProxy.this.getBaseContext());
//            } catch (Exception e) {
//                PluginManager.deliver(this, false, pluginPkgName,
//                        ErrorType.ERROR_CLIENT_CALL_ON_CREATE_FAIL);
//                e.printStackTrace();
//                this.finish();
//                return;
//            }

            if (getParent() == null) {
                mLoadedApk.getActivityStackSupervisor().pushActivityToStack(this);
            }
            mPluginContrl.callOnCreate(savedInstanceState);
            mPluginContrl.getPluginRef().set("mDecor", this.getWindow().getDecorView());
            PluginManager.sendPluginLoadedBroadcast(InstrActivityProxy.this.getBaseContext());
        }
    }


    /**
     * 装载被代理的Activity
     * @param mLoadedApk
     *          插件的实例对象
     * @param activityName
     *          需要被代理的Activity 类名
     * @return
     *          成功则返回插件中被代理的Activity对象
     */
    private Activity loadPluginActivity(PluginLoadedApk mLoadedApk ,String activityName){
        try {
            Activity mActivity = (Activity) mLoadedApk.getPluginClassLoader()
                    .loadClass(activityName).newInstance();
            return mActivity;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 从Intent里面解析被代理的Activity的包名和组件名
     * @return
     *      成功则返回一个长度为2的String[],String[0]表示包名，String[1]表示类名
     *      失败则返回null
     */
    private String[] parsePkgAndClsFromIntent(){
        Intent mIntent = getIntent();
        if(mIntent == null){
            return null;
        }

        //从action里面拿到pkg,并全局保存，然后还原action
        if(TextUtils.isEmpty(mPluginPackage)){
            mPluginPackage = IntentUtils.getPluginPackage(mIntent);
        }
        IntentUtils.resetAction(mIntent);

        if(!TextUtils.isEmpty(mPluginPackage)){
            if(mLoadedApk == null){
                mLoadedApk = PluginManager.getPluginLoadedApkByPkgName(mPluginPackage);
            }
            if(mLoadedApk!=null){
                //解决插件中跳转自定义Bean对象失败的问题
                mIntent.setExtrasClassLoader(mLoadedApk.getPluginClassLoader());
            }
        }
        final Bundle pluginMessage = mIntent.getExtras();
        String[] result = new String[2];
        if (null != pluginMessage) {
            result[0] = pluginMessage.getString(IIntentConstant.EXTRA_TARGET_PACKAGNAME_KEY);
            result[1] = pluginMessage.getString(IIntentConstant.EXTRA_TARGET_CLASS_KEY);
            if (!TextUtils.isEmpty(result[0]) && !TextUtils.isEmpty(result[1])) {
                PluginDebugLog.runtimeFormatLog(TAG,"pluginPkg:%s,pluginCls:%s",result[0],result[1]);
                return result;
            }
        }
        return null;
    }


    /**
     * 尝试初始化PluginLoadedApk
     * @param mPluginPackage
     * @return
     */
    private boolean tryToInitPluginLoadApk(String mPluginPackage) {
        if (!TextUtils.isEmpty(mPluginPackage) && null == mLoadedApk) {
            mLoadedApk = PluginManager.getPluginLoadedApkByPkgName(mPluginPackage);
        }
        if (null != mLoadedApk) {
            return true;
        }
        return false;
    }

    private boolean mNeedUpdateConfiguration = true;


    public PluginActivityControl getController() {
        return mPluginContrl;
    }

    @Override
    public Resources getResources() {
        if (mLoadedApk == null) {
            return super.getResources();
        }
        Resources mPluginResource = mLoadedApk.getPluginResource();
        return mPluginResource == null ? super.getResources()
                : mPluginResource;
    }

    @Override
    public void setTheme(int resid) {
        if (ContextUtils.isAndroidN() || ContextUtils.isAndroidO()) {
            String[] temp = parsePkgAndClsFromIntent();
            if (mNeedUpdateConfiguration && (temp != null || mLoadedApk != null)) {
                tryToInitPluginLoadApk(temp[0]);
                if (mLoadedApk != null) {
                    ActivityInfo actInfo = mLoadedApk.getActivityInfoByClassName(temp[1]);
                    if (actInfo != null) {
                        int resTheme = actInfo.getThemeResource();
                        if (mNeedUpdateConfiguration) {
                            changeActivityInfo(InstrActivityProxy.this, temp[0], actInfo);
                            super.setTheme(resTheme);
                            mNeedUpdateConfiguration = false;
                            return;
                        }
                    }
                }
            }
            super.setTheme(resid);
        } else {
            getTheme().applyStyle(resid, true);
        }
    }

    /**
     * Override Oppo method in Context Resolve cann't start plugin on oppo
     * devices, true or false both OK, false as the temporary result
     *
     * @return
     */
    public boolean isOppoStyle() {
        return false;
    }

    @Override
    public Resources.Theme getTheme() {
        if (mLoadedApk == null) {
            String[] temp = parsePkgAndClsFromIntent();
            if (null != temp) {
                tryToInitPluginLoadApk(temp[0]);
            }
        }
        return super.getTheme();
    }


    @Override
    public AssetManager getAssets() {
        if (mLoadedApk == null) {
            return super.getAssets();
        }
        AssetManager mPluginAssetManager = mLoadedApk.getPluginAssetManager();
        return mPluginAssetManager == null ? super.getAssets()
                : mPluginAssetManager;
    }

    @Override
    public File getFilesDir() {
        return mPluginContextWrapper.getFilesDir();
    }

    @Override
    public File getCacheDir() {
        return mPluginContextWrapper.getCacheDir();
    }

    @Override
    public File getFileStreamPath(String name) {
        return mPluginContextWrapper.getFileStreamPath(name);
    }

    @Override
    public File getDir(String name, int mode) {
        return mPluginContextWrapper.getDir(name, mode);

    }

    @Override
    public FileInputStream openFileInput(String name) throws FileNotFoundException {
        return mPluginContextWrapper.openFileInput(name);
    }

    @Override
    public FileOutputStream openFileOutput(String name, int mode) throws FileNotFoundException {
        return mPluginContextWrapper.openFileOutput(name, mode);
    }

    @Override
    public File getDatabasePath(String name) {
        return mPluginContextWrapper.getDatabasePath(name);
    }

    @Override
    public boolean deleteFile(String name) {
        return mPluginContextWrapper.deleteFile(name);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, CursorFactory factory) {

        return mPluginContextWrapper.openOrCreateDatabase(name, mode, factory);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, CursorFactory factory,
            DatabaseErrorHandler errorHandler) {
        return mPluginContextWrapper.openOrCreateDatabase(name, mode, factory, errorHandler);
    }

    @Override
    public boolean deleteDatabase(String name) {

        return mPluginContextWrapper.deleteDatabase(name);
    }

    @Override
    public String[] databaseList() {
        return mPluginContextWrapper.databaseList();
    }

    @Override
    public ClassLoader getClassLoader() {
        if (mLoadedApk == null) {
            return super.getClassLoader();
        }
        return mLoadedApk.getPluginClassLoader();
    }

    @Override
    public Context getApplicationContext() {
        if (null != mLoadedApk && null != mLoadedApk.getPluginApplication()) {
            return mLoadedApk.getPluginApplication();
        }
        return super.getApplicationContext();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onResume....");
        if (getController() != null) {
            try {
                getController().callOnResume();
                IResourchStaticsticsControllerManager.onResume(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onStart....");
        if (getController() != null) {
            try {
                getController().callOnStart();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (getController() != null) {
            try {
                getController().callOnPostCreate(savedInstanceState);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onDestroy....");
        if (null == this.getParent() && mLoadedApk != null) {
            mLoadedApk.getActivityStackSupervisor().popActivityFromStack(this);
        }
        if (getController() != null) {

            try {
                getController().callOnDestroy();
                // LCallbackManager.callAllOnDestroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onPause....");
        if (getController() != null) {

            try {
                getController().callOnPause();
                IResourchStaticsticsControllerManager.onPause(this);
                // LCallbackManager.callAllOnPause();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onBackPressed() {
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onBackPressed....");
        if (getController() != null) {
            try {
                getController().callOnBackPressed();
                // LCallbackManager.callAllOnBackPressed();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onStop....");
        if (getController() != null) {
            try {
                getController().callOnStop();
                // LCallbackManager.callAllOnStop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onRestart....");
        if (getController() != null) {
            try {
                getController().callOnRestart();
                // LCallbackManager.callAllOnRestart();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (getController() != null) {
            // LCallbackManager.callAllOnKeyDown(keyCode, event);
            return getController().callOnKeyDown(keyCode, event);

        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public ComponentName startService(Intent mIntent) {
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy startService....");
        if (mLoadedApk != null) {
            ComponetFinder.findSuitableServiceByIntent(mLoadedApk,mIntent);
        }
        return super.startService(mIntent);
    }

    @Override
    public boolean stopService(Intent name) {
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy stopService....");
        if (mLoadedApk != null) {
            //TODO 如果是隐式调用，那么这里会崩溃
            String actServiceClsName = name.getComponent().getClassName();
            PluginServiceWrapper plugin = PServiceSupervisor.getServiceByIdentifer(
                    PluginServiceWrapper.getIndeitfy(mLoadedApk.getPluginPackageName(), actServiceClsName));
            if (plugin != null) {
                plugin.updateStartStatus(PluginServiceWrapper.PLUGIN_SERVICE_STOPED);
                plugin.tryToDestroyService(name);
                return true;
            }
        }
        return super.stopService(name);
    }

    @Override
    public boolean bindService(Intent mIntent, ServiceConnection conn, int flags) {
        if (mLoadedApk != null) {
            //ServiceJumpUtil.remapStartServiceIntent(mPluginEnv, service);
            ComponetFinder.findSuitableServiceByIntent(mLoadedApk,mIntent);
        }
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy bindService...."+mIntent);
        return super.bindService(mIntent, conn, flags);
    }

//    public void startActivityForResult(Intent intent, int requestCode) {
//        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy startActivityForResult one....");
//        if (mLoadedApk != null) {
//            super.startActivityForResult(
//                    ActivityJumpUtil.handleStartActivityIntent(mLoadedApk.getPluginPackageName(), intent, requestCode, null, this),
//                    requestCode);
//        } else {
//            super.startActivityForResult(intent, requestCode);
//        }
//    }
//
//    @SuppressLint("NewApi")
//    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
//        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy startActivityForResult two....");
//        if (mLoadedApk != null) {
//            super.startActivityForResult(
//                    ActivityJumpUtil.handleStartActivityIntent(mPluginEnv.getTargetPackageName(), intent, requestCode, options, this),
//                    requestCode, options);
//        } else {
//            super.startActivityForResult(intent, requestCode, options);
//        }
//    }

    // public void startActivityFromFragment(Fragment fragment, Intent intent,
    // int requestCode) {
    // // TODO Auto-generated method stub
    // super.startActivityFromFragment(fragment, intent, requestCode);
    // }
    //
    // @Override
    // public void startActivityFromFragment(Fragment fragment, Intent intent,
    // int requestCode,
    // Bundle options) {
    // // TODO Auto-generated method stub
    // super.startActivityFromFragment(fragment, intent, requestCode, options);
    // }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return mPluginContextWrapper.getSharedPreferences(name, mode);
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        if (getController() != null) {
            getController().callDump(prefix, fd, writer, args);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
         mNeedUpdateConfiguration = true;
        if (getController() != null) {
            getController().callOnConfigurationChanged(newConfig);
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (getController() != null) {
            getController().callOnPostResume();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (getController() != null) {
            getController().callOnDetachedFromWindow();
        }
        super.onDetachedFromWindow();
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onCreateView1:"+name);
        if (getController() != null) {
            return getController().callOnCreateView(name, context, attrs);
        }
        return super.onCreateView(name, context, attrs);
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onCreateView2:"+name);
        if (getController() != null) {
            return getController().callOnCreateView(parent, name, context, attrs);
        }
        return super.onCreateView(parent, name, context, attrs);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onNewIntent");
        if (getController() != null) {
            getController().callOnNewIntent(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onActivityResult");
        if (getController() != null) {
            getController().getPluginRef().call("onActivityResult", PluginActivityControl.sMethods, requestCode, resultCode, data);
        }
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onAttachFragment");
        if (getController() != null && getController().getPlugin() != null) {
            getController().getPlugin().onAttachFragment(fragment);
        }
    }

    @Override
    public View onCreatePanelView(int featureId) {

        if (getController() != null && getController().getPlugin() != null) {
            return getController().getPlugin().onCreatePanelView(featureId);
        } else {
            return super.onCreatePanelView(featureId);
        }
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        if (getController() != null && getController().getPlugin() != null) {
            getController().getPlugin().onOptionsMenuClosed(menu);
        }
    }

    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        super.onPanelClosed(featureId, menu);
        if (getController() != null && getController().getPlugin() != null) {
            getController().getPlugin().onPanelClosed(featureId, menu);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (getController() != null && getController().getPlugin() != null) {
            return getController().getPlugin().onKeyUp(keyCode, event);
        } else {
            return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    public void onAttachedToWindow() {

        super.onAttachedToWindow();
        if (getController() != null && getController().getPlugin() != null) {
            getController().getPlugin().onAttachedToWindow();
        }
    }

    @Override
    public CharSequence onCreateDescription() {
        if (getController() != null && getController().getPlugin() != null) {
            return getController().getPlugin().onCreateDescription();
        } else {
            return super.onCreateDescription();
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (getController() != null && getController().getPlugin() != null) {
            return getController().getPlugin().onGenericMotionEvent(event);
        } else {
            return super.onGenericMotionEvent(event);
        }
    }

    @Override
    public void onContentChanged() {

        super.onContentChanged();
        if (getController() != null && getController().getPlugin() != null) {
            getController().getPlugin().onContentChanged();
        }
    }

    @Override
    public boolean onCreateThumbnail(Bitmap outBitmap, Canvas canvas) {
        if (getController() != null && getController().getPlugin() != null) {
            return getController().getPlugin().onCreateThumbnail(outBitmap, canvas);
        } else {
            return super.onCreateThumbnail(outBitmap, canvas);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onRestoreInstanceState");
        if (getController() != null) {
            getController().callOnRestoreInstanceState(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        PluginDebugLog.runtimeLog(TAG,"InstrActivityProxy onSaveInstanceState");
        if (getController() != null) {
            getController().callOnSaveInstanceState(outState);
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (getController() != null) {
            ReflectionUtils pluginRef = getController().getPluginRef();
            if (pluginRef != null) {
                // 6.0.1用mHasCurrentPermissionsRequest保证分发permission
                // result完成，根据现有
                // 的逻辑，第一次权限请求onRequestPermissionsResult一直是true，会影响之后的申请权限的
                // dialog弹出
                try {
                    pluginRef.set("mHasCurrentPermissionsRequest", false);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                pluginRef.call("onRequestPermissionsResult", PluginActivityControl.sMethods, requestCode, permissions, grantResults);
            }
        }
    }

    public void onStateNotSaved() {
        super.onStateNotSaved();
        if (getController() != null) {
            getController().getPluginRef().call("onStateNotSaved", PluginActivityControl.sMethods);
        }
    }

    public boolean onSearchRequested(SearchEvent searchEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (getController() != null) {
                return getController().getPlugin().onSearchRequested(searchEvent);
            }
            return super.onSearchRequested(searchEvent);
        } else {
            return false;
        }
    }

    public boolean onSearchRequested() {
        if (getController() != null) {
            getController().getPlugin().onSearchRequested();
        }
        return super.onSearchRequested();
    }

    public void onProvideAssistContent(AssistContent outContent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            super.onProvideAssistContent(outContent);
            if (getController() != null) {
                getController().getPlugin().onProvideAssistContent(outContent);
            }
        }
    }

    /**
     * Get the context which start this plugin
     *
     * @return
     */
    @Override
    public Context getOriginalContext() {
        if (null != mLoadedApk) {
            return mLoadedApk.getHostContext();
        }
        return null;
    }

    /**
     * Get host resource
     *
     * @return host resource tool
     */
    @Override
    public ResourcesToolForPlugin getHostResourceTool() {
        if (null != mLoadedApk) {
            return mLoadedApk.getHostResourceTool();
        }
        return null;
    }

    @Override
    public void exitApp() {
        if (null != mLoadedApk) {
            mLoadedApk.quitApp(true);
        }
    }

    @Override
    public String getPluginPackageName() {
        if (null != mLoadedApk) {
            return mLoadedApk.getPluginPackageName();
        }
        return this.getPackageName();
    }

    public String dump() {
        String[] pkgCls = parsePkgAndClsFromIntent();
        if (null != pkgCls && pkgCls.length == 2) {
            return "Package&Cls is: " + this + " " + (pkgCls != null ? pkgCls[0] + " " + pkgCls[1] : "") + " flg=0x"
                    + Integer.toHexString(getIntent().getFlags());
        } else {
            return "Package&Cls is: " + this + " flg=0x" + Integer.toHexString(getIntent().getFlags());
        }
    }

    public void dump(PrintWriter printWriter){
        String[] pkgCls = parsePkgAndClsFromIntent();
        if (null != pkgCls && pkgCls.length == 2) {
            printWriter.print("Package&Cls is: " + this + " " + (pkgCls != null ? pkgCls[0] + " " + pkgCls[1] : "") + " flg=0x"
                    + Integer.toHexString(getIntent().getFlags())); ;
        } else {
            printWriter.print("Package&Cls is: " + this + " flg=0x" + Integer.toHexString(getIntent().getFlags()));
        }
    }


    @Override
    public ApplicationInfo getApplicationInfo() {
        if (mPluginContextWrapper != null) {
            return mPluginContextWrapper.getApplicationInfo();
        }
        return super.getApplicationInfo();
    }

    @Override
    public String getPackageCodePath() {
        if (mPluginContextWrapper != null) {
            return mPluginContextWrapper.getPackageCodePath();
        }
        return super.getPackageCodePath();
    }

    private  void changeActivityInfo(Activity activity, String pkgName, ActivityInfo mActivityInfo) {
        ActivityInfo origActInfo = null;
        try {
            Field field_mActivityInfo = Activity.class.getDeclaredField("mActivityInfo");
            field_mActivityInfo.setAccessible(true);
            origActInfo = (ActivityInfo) field_mActivityInfo.get(activity);
        } catch (Exception e) {
            PluginManager.deliver(activity, false, pkgName, ErrorType.ERROR_CLIENT_CHANGE_ACTIVITYINFO_FAIL);
            PluginDebugLog.log(TAG, e.getStackTrace());
            return;
        }
        PluginLoadedApk mLoadedApk = PluginManager.getPluginLoadedApkByPkgName(pkgName);


        if (null != mActivityInfo) {
            mActivityInfo.applicationInfo = mLoadedApk.getPluginMapping().getPackageInfo().applicationInfo;
            if (origActInfo != null) {
                origActInfo.applicationInfo = mActivityInfo.applicationInfo;
                origActInfo.configChanges = mActivityInfo.configChanges;
                origActInfo.descriptionRes = mActivityInfo.descriptionRes;
                origActInfo.enabled = mActivityInfo.enabled;
                origActInfo.exported = mActivityInfo.exported;
                origActInfo.flags = mActivityInfo.flags;
                origActInfo.icon = mActivityInfo.icon;
                origActInfo.labelRes = mActivityInfo.labelRes;
                origActInfo.logo = mActivityInfo.logo;
                origActInfo.metaData = mActivityInfo.metaData;
                origActInfo.name = mActivityInfo.name;
                origActInfo.nonLocalizedLabel = mActivityInfo.nonLocalizedLabel;
                origActInfo.packageName = mActivityInfo.packageName;
                origActInfo.permission = mActivityInfo.permission;
                // origActInfo.processName
                origActInfo.screenOrientation = mActivityInfo.screenOrientation;
                origActInfo.softInputMode = mActivityInfo.softInputMode;
                origActInfo.targetActivity = mActivityInfo.targetActivity;
                origActInfo.taskAffinity = mActivityInfo.taskAffinity;
                origActInfo.theme = mActivityInfo.theme;
            }
        }
        // Handle ActionBar title
        if (null != origActInfo) {
            if (origActInfo.nonLocalizedLabel != null) {
                activity.setTitle(origActInfo.nonLocalizedLabel);
            } else if (origActInfo.labelRes != 0) {
                activity.setTitle(origActInfo.labelRes);
            } else {
                if (origActInfo.applicationInfo != null) {
                    if (origActInfo.applicationInfo.nonLocalizedLabel != null) {
                        activity.setTitle(origActInfo.applicationInfo.nonLocalizedLabel);
                    } else if (origActInfo.applicationInfo.labelRes != 0) {
                        activity.setTitle(origActInfo.applicationInfo.labelRes);
                    } else {
                        activity.setTitle(origActInfo.applicationInfo.packageName);
                    }
                }
            }
        }
        if (null != mActivityInfo) {
            PluginDebugLog.log(TAG, "changeActivityInfo->changeTheme: " + " theme = " +
                    mActivityInfo.getThemeResource() + ", icon = " + mActivityInfo.getIconResource()
                    + ", logo = " + mActivityInfo.logo + ", labelRes" + mActivityInfo.labelRes);
        }
    }
}
