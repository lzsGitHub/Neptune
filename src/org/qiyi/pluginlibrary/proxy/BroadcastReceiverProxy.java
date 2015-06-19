package org.qiyi.pluginlibrary.proxy;

import org.qiyi.plugin.manager.ProxyEnvironmentNew;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BroadcastReceiverProxy extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            String targetReceiver = intent.getStringExtra(ProxyEnvironmentNew.EXTRA_TARGET_RECEIVER);
            String targetPkgName = intent.getStringExtra(ProxyEnvironmentNew.EXTRA_TARGET_PACKAGNAME);
            try {
                BroadcastReceiver target = ((BroadcastReceiver) ProxyEnvironmentNew.getInstance(targetPkgName)
                        .getDexClassLoader().loadClass(targetReceiver).asSubclass(BroadcastReceiver.class)
                        .newInstance());
                target.onReceive(ProxyEnvironmentNew.getInstance(targetPkgName).getApplication(), intent);
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
