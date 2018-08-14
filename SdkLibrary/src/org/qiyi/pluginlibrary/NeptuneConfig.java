package org.qiyi.pluginlibrary;

import org.qiyi.pluginlibrary.pm.IVerifyPluginInfo;
import org.qiyi.pluginlibrary.utils.IRecoveryCallback;

/**
 * 插件框架运行配置信息
 *
 * author: liuchun
 * date: 2018/6/4
 */
public final class NeptuneConfig {
    /**
     * 传统的插件框架模式，使用InstrActivityProxy进行代理转发
     */
    public static final int LEGACY_MODE = 0;
    /**
     * Hook Instrumentation方案
     */
    public static final int INSTRUMENTATION_MODE = 1;
    /**
     * Hook Instrumentation方案 + Base PluginActivity方案
     */
    public static final int INSTRUMENTATION_BASEACT_MODE = 2;
    /** 插件框架运行模式，已经全面切换到Hook Instrumentation方案，适配Android P */
    private int sdkMode;

    private IVerifyPluginInfo mVerifyPluginInfo;
    private IRecoveryCallback mRecoveryCallback;
    /** Debug调试日志是否打开 */
    private boolean mIsDebug;

    NeptuneConfig(NeptuneConfigBuilder builder) {
        this.sdkMode = builder.sdkMode;
        this.mVerifyPluginInfo = builder.verifyPluginInfo;
        this.mRecoveryCallback = builder.recoveryCallback;
        this.mIsDebug = builder.isDebug;
    }


    public int getSdkMode() {
        return sdkMode;
    }

    public IVerifyPluginInfo getVerifyPluginInfo() {
        return mVerifyPluginInfo;
    }

    public IRecoveryCallback getRecoveryCallback() {
        return mRecoveryCallback;
    }

    public boolean isDebug() {
        return mIsDebug;
    }

    public static class NeptuneConfigBuilder {
        int sdkMode = 0;
        IVerifyPluginInfo verifyPluginInfo;
        IRecoveryCallback recoveryCallback;
        boolean isDebug;

        public NeptuneConfigBuilder configSdkMode(int sdkMode) {
            this.sdkMode = sdkMode;
            return this;
        }

        public NeptuneConfigBuilder setVerifyPluginInfo(IVerifyPluginInfo verifyPluginInfo) {
            this.verifyPluginInfo = verifyPluginInfo;
            return this;
        }

        public NeptuneConfigBuilder recoveryCallback(IRecoveryCallback callback) {
            this.recoveryCallback = callback;
            return this;
        }

        public NeptuneConfigBuilder enableDebug(boolean isDebugable) {
            this.isDebug = isDebugable;
            return this;
        }

        public NeptuneConfig build() {
            return new NeptuneConfig(this);
        }
    }

}
