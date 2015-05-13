package org.qiyi.pluginnew.service;

import java.util.ArrayList;
import java.util.List;

import org.qiyi.plugin.manager.ProxyEnvironmentNew;
import org.qiyi.pluginlibrary.ProxyEnvironment;
import org.qiyi.pluginlibrary.ErrorType.ErrorType;
import org.qiyi.pluginlibrary.utils.JavaCalls;
import org.qiyi.pluginlibrary.utils.PluginDebugLog;
import org.qiyi.pluginnew.ReflectionUtils;
import org.qiyi.pluginnew.context.CMContextWrapperNew;

import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;

/**
 * Plugin service's host service(Real service)
 */
public class ServiceProxyNew extends Service {
	private static final String TAG = ServiceProxyNew.class.getSimpleName();

	@Override
	public void onCreate() {
		PluginDebugLog.log(TAG, "ServiceProxyNew>>>>>onCreate()");
		super.onCreate();
		handleSlefLaunchPluginService();
	}

	/**
	 * Must invoke on the main thread
	 */
	private void handleSlefLaunchPluginService() {
		List<PluginServiceWrapper> selfLaunchServices = new ArrayList<PluginServiceWrapper>(1);
		for (PluginServiceWrapper plugin : ProxyEnvironmentNew.sAliveServices.values()) {
			ProxyEnvironmentNew.sAliveServices.remove(PluginServiceWrapper.getIndeitfy(
					plugin.getPkgName(), plugin.getServiceClassName()));
			if (plugin.mNeedSelfLaunch) {
				selfLaunchServices.add(plugin);
			}
		}
		for (PluginServiceWrapper item : selfLaunchServices) {
			loadTargetService(item.getPkgName(), item.getServiceClassName());
		}
	}

	private PluginServiceWrapper findPluginService(String pkgName, String clsName) {
		return ProxyEnvironmentNew.sAliveServices.get(PluginServiceWrapper.getIndeitfy(pkgName,
				clsName));
	}

	public PluginServiceWrapper loadTargetService(String targetPackageName, String targetClassName) {
		PluginServiceWrapper currentPlugin = findPluginService(targetPackageName, targetClassName);
		PluginDebugLog.log(TAG, "ServiceProxyNew>>>>>loadTargetService()" + "target:"
				+ (currentPlugin == null ? "null" : currentPlugin.getClass().getName()));
		if (currentPlugin == null) {
			PluginDebugLog.log(TAG, "ServiceProxyNew>>>>ProxyEnvironment.hasInstance:"
					+ ProxyEnvironmentNew.hasInstance(targetPackageName) + ";targetPackageName:"
					+ targetPackageName);

			try {
				ProxyEnvironmentNew env = ProxyEnvironmentNew.getInstance(targetPackageName);
				if (null == env) {
					return null;
				}
				Service pluginService = ((Service) env.getDexClassLoader()
						.loadClass(targetClassName).newInstance());
				CMContextWrapperNew actWrapper = new CMContextWrapperNew(env.getApplication(),
						targetPackageName);
				JavaCalls.invokeMethod(
						pluginService,
						"attach",
						new Class<?>[] { Context.class,
								ReflectionUtils.getFieldValue(this, "mThread").getClass(),
								String.class, IBinder.class, Application.class, Object.class },
						new Object[] { actWrapper, ReflectionUtils.getFieldValue(this, "mThread"),
								targetClassName, ReflectionUtils.getFieldValue(this, "mToken"),
								env.getApplication(),
								ReflectionUtils.getFieldValue(this, "mActivityManager") });
				currentPlugin = new PluginServiceWrapper(targetClassName, targetPackageName, this,
						pluginService);
				pluginService.onCreate();
				currentPlugin.updateServiceState(PluginServiceWrapper.PLUGIN_SERVICE_CREATED);

				ProxyEnvironmentNew.sAliveServices.put(targetPackageName + "." + targetClassName,
						currentPlugin);

				PluginDebugLog.log(TAG, "ServiceProxyNew>>>start service, pkgName: "
						+ targetPackageName + ", clsName: " + targetClassName);
			} catch (InstantiationException e) {
				currentPlugin = null;
				e.printStackTrace();
				ProxyEnvironment.deliverPlug(false, targetPackageName,
						ErrorType.ERROR_CLIENT_LOAD_INIT_EXCEPTION_INSTANTIATION);
			} catch (IllegalAccessException e) {
				currentPlugin = null;
				e.printStackTrace();
				ProxyEnvironment.deliverPlug(false, targetPackageName,
						ErrorType.ERROR_CLIENT_LOAD_INIT_EXCEPTION_ILLEGALACCESS);
			} catch (ClassNotFoundException e) {
				currentPlugin = null;
				e.printStackTrace();
				ProxyEnvironment.deliverPlug(false, targetPackageName,
						ErrorType.ERROR_CLIENT_LOAD_INIT_EXCEPTION_CLASSNOTFOUND);
			} catch (Exception e) {
				e.printStackTrace();
				ProxyEnvironment.deliverPlug(false, targetPackageName,
						ErrorType.ERROR_CLIENT_LOAD_INIT_EXCEPTION);
				currentPlugin = null;
				PluginDebugLog.log("plugin", "初始化target失败");
			}
		}
		return currentPlugin;
	}

	@Override
	public IBinder onBind(Intent paramIntent) {

		PluginDebugLog.log(TAG, "ServiceProxyNew>>>>>onBind():"
				+ (paramIntent == null ? "null" : paramIntent));
		if (paramIntent == null) {
			return null;
		}
		String targetClassName = paramIntent.getStringExtra(ProxyEnvironment.EXTRA_TARGET_SERVICE);
		String targetPackageName = paramIntent
				.getStringExtra(ProxyEnvironment.EXTRA_TARGET_PACKAGNAME);
		PluginServiceWrapper currentPlugin = loadTargetService(targetPackageName, targetClassName);

		if (currentPlugin != null && currentPlugin.getCurrentService() != null) {
			currentPlugin.updateBindCounter(1);
			return currentPlugin.getCurrentService().onBind(paramIntent);
		} else {
			return null;
		}
	}

	@Override
	public void onConfigurationChanged(Configuration paramConfiguration) {
		if (ProxyEnvironmentNew.sAliveServices != null) {
			// Notify all alive plugin service
			for (PluginServiceWrapper plugin : ProxyEnvironmentNew.sAliveServices.values()) {
				if (plugin != null && plugin.getCurrentService() != null) {
					plugin.getCurrentService().onConfigurationChanged(paramConfiguration);
				}
			}
		} else {
			super.onConfigurationChanged(paramConfiguration);
		}
	}

	@Override
	public void onDestroy() {
		PluginDebugLog.log(TAG, "ServiceProxyNew_onDestory");
		if (ProxyEnvironmentNew.sAliveServices != null) {
			// Notify all alive plugin service to do destroy
			for (PluginServiceWrapper plugin : ProxyEnvironmentNew.sAliveServices.values()) {
				if (plugin != null && plugin.getCurrentService() != null) {
					plugin.getCurrentService().onDestroy();
				}
			}
			ProxyEnvironmentNew.sAliveServices.clear();
		}
		super.onDestroy();
	}

	public void onLowMemory() {
		if (ProxyEnvironmentNew.sAliveServices.size() > 0) {
			// Notify all alive plugin service to do destroy
			for (PluginServiceWrapper plugin : ProxyEnvironmentNew.sAliveServices.values()) {
				if (plugin != null && plugin.getCurrentService() != null) {
					plugin.getCurrentService().onLowMemory();
				}
			}
		} else {
			super.onLowMemory();
		}
	}

	@Override
	public int onStartCommand(Intent paramIntent, int paramInt1, int paramInt2) {
		PluginDebugLog.log(TAG, "ServiceProxyNew>>>>>onStartCommand():"
				+ (paramIntent == null ? "null" : paramIntent));
		if (paramIntent == null) {
			return super.onStartCommand(paramIntent, paramInt1, paramInt2);
		}
		String targetClassName = paramIntent.getStringExtra(ProxyEnvironment.EXTRA_TARGET_SERVICE);
		String targetPackageName = paramIntent
				.getStringExtra(ProxyEnvironment.EXTRA_TARGET_PACKAGNAME);
		PluginServiceWrapper currentPlugin = loadTargetService(targetPackageName, targetClassName);

		if (currentPlugin != null && currentPlugin.getCurrentService() != null) {
			currentPlugin.updateStartStatus(PluginServiceWrapper.PLUGIN_SERVICE_STARTED);
			int result = currentPlugin.getCurrentService().onStartCommand(paramIntent, paramInt1,
					paramInt2);
			if (result == START_REDELIVER_INTENT || result == START_STICKY) {
				currentPlugin.mNeedSelfLaunch = true;
			}
			return result;
		} else {
			return super.onStartCommand(paramIntent, paramInt1, paramInt2);
		}
	}

	@Override
	public boolean onUnbind(Intent paramIntent) {
		PluginDebugLog.log(TAG, "ServiceProxyNew>>>>>onUnbind():"
				+ (paramIntent == null ? "null" : paramIntent));
		String targetClassName = paramIntent.getStringExtra(ProxyEnvironment.EXTRA_TARGET_SERVICE);
		String targetPackageName = paramIntent
				.getStringExtra(ProxyEnvironment.EXTRA_TARGET_PACKAGNAME);
		PluginServiceWrapper plugin = findPluginService(targetPackageName, targetClassName);
		boolean result = false;
		if (plugin != null && plugin.getCurrentService() != null) {
			plugin.updateBindCounter(-1);
			result = plugin.getCurrentService().onUnbind(paramIntent);
			plugin.tryToDestroyService(paramIntent);
		}
		super.onUnbind(paramIntent);
		return result;
	}

	@Override
	@Deprecated
	public void onStart(Intent intent, int startId) {
		PluginDebugLog.log(TAG, "ServiceProxyNew>>>>>onStart():"
				+ (intent == null ? "null" : intent));
		if (intent == null) {
			super.onStart(intent, startId);
			return;
		}
		String targetClassName = intent.getStringExtra(ProxyEnvironment.EXTRA_TARGET_SERVICE);
		String targetPackageName = intent.getStringExtra(ProxyEnvironment.EXTRA_TARGET_PACKAGNAME);
		PluginServiceWrapper currentPlugin = loadTargetService(targetPackageName, targetClassName);

		if (currentPlugin != null && currentPlugin.getCurrentService() != null) {
			currentPlugin.updateBindCounter(1);
			currentPlugin.getCurrentService().onStart(intent, startId);
		}
		super.onStart(intent, startId);
	}

	@Override
	public void onTrimMemory(int level) {
		if (ProxyEnvironmentNew.sAliveServices.size() > 0) {
			// Notify all alive plugin service to do onTrimMemory
			for (PluginServiceWrapper plugin : ProxyEnvironmentNew.sAliveServices.values()) {
				if (plugin != null && plugin.getCurrentService() != null) {
					plugin.getCurrentService().onTrimMemory(level);
				}
			}
		} else {
			super.onTrimMemory(level);
		}
	}

	@Override
	public void onRebind(Intent intent) {
		PluginDebugLog.log(TAG, "ServiceProxyNew>>>>>onRebind():"
				+ (intent == null ? "null" : intent));
		if (intent == null) {
			super.onRebind(intent);
			return;
		}
		String targetClassName = intent.getStringExtra(ProxyEnvironment.EXTRA_TARGET_SERVICE);
		String targetPackageName = intent.getStringExtra(ProxyEnvironment.EXTRA_TARGET_PACKAGNAME);
		PluginServiceWrapper currentPlugin = findPluginService(targetPackageName, targetClassName);

		if (currentPlugin != null && currentPlugin.getCurrentService() != null) {
			currentPlugin.updateBindCounter(1);
			currentPlugin.getCurrentService().onRebind(intent);
		}
		super.onRebind(intent);
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
	}
}