/*
 *
 * Copyright 2018 iQIYI.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.qiyi.pluginlibrary.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import org.qiyi.pluginlibrary.install.PluginInstaller;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;


public final class FileUtils {
    private static final String TAG = PluginDebugLog.TAG;
    /** apk 中 lib 目录的前缀标示。比如 lib/x86/libshare_v2.so */
    private static final String APK_LIB_DIR_PREFIX = "lib/";
    /** lib中so后缀 */
    private static final String APK_LIB_SUFFIX = ".so";

    /** utility class private constructor */
    private FileUtils() {
    }

    /**
     * Copy data from a source stream to destFile. Return true if succeed,
     * return false if failed.
     *
     * @param inputStream source file inputstream
     * @param destFile destFile
     *
     * @return success return true
     */
    public static boolean copyToFile(InputStream inputStream, File destFile) {
        PluginDebugLog.log(TAG, "copyToFile:" + inputStream + "," + destFile);
        if (inputStream == null || destFile == null) {
            return false;
        }

        FileOutputStream out = null;
        BufferedOutputStream bos = null;
        try {
            if (destFile.exists()) {
                destFile.delete();
                destFile.createNewFile();
            }
            out = new FileOutputStream(destFile);
            bos = new BufferedOutputStream(out);
            byte[] buffer = new byte[4096];
            int bytesRead = -1;
            while ((bytesRead = inputStream.read(buffer)) >= 0) {
                bos.write(buffer, 0, bytesRead);
            }
            bos.flush();
            PluginDebugLog.log(TAG, "拷贝成功");
            return true;
        } catch (IOException e) {
            PluginDebugLog.log(TAG, "拷贝失败");
            return false;
        } finally {
            closeQuietly(out);
            closeQuietly(bos);
        }
    }

    /**
     * Copy data from a source stream to destFile. Return true if succeed,
     * return false if failed.
     *
     * @param srcFile source file
     * @param destFile destFile
     *
     * @return success return true
     */
    public static boolean copyToFile(File srcFile, File destFile) {
        PluginDebugLog.log(TAG, "copyToFile:" + srcFile + "," + destFile);
        if (srcFile == null || !srcFile.exists() || destFile == null) {
            return false;
        }

        boolean result = false;
        InputStream inputStream = null;
        BufferedInputStream bis = null;
        try {
            inputStream = new FileInputStream(srcFile);
            bis = new BufferedInputStream(inputStream);

            result = copyToFile(bis, destFile);
        } catch (IOException e) {
            /* ignore */
        } finally {
            closeQuietly(inputStream);
            closeQuietly(bis);
        }

        return result;
    }

    /**
     * 安装 apk 中的 so 库。
     *
     * @param apkFilePath
     * @param libDir lib目录。
     */
    public static boolean installNativeLibrary(String apkFilePath, String libDir) {
        PluginDebugLog.installFormatLog("plugin", "apkFilePath: %s, libDir: %s", apkFilePath, libDir);
        boolean installResult = false;
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(apkFilePath);

            if (installNativeLibrary(zipFile, libDir, Build.CPU_ABI) || installNativeLibrary(zipFile, libDir, Build.CPU_ABI2)) {
                installResult = true;
            } else {
                PluginDebugLog.installFormatLog("plugin", "can't install native lib of %s as no matched ABI", apkFilePath);
            }

        } catch (IOException e) {
            /* ignore */
        } finally {
            closeQuietly(zipFile);
        }

        return installResult;
    }

    /**
     * 安装拷贝so库
     */
    private static boolean installNativeLibrary(ZipFile apk, String libDir, String abi) {
        PluginDebugLog.installFormatLog(TAG, "start to extract native lib for ABI: %s", abi);
        boolean installResult = false;
        Enumeration<? extends ZipEntry> entries = apk.entries();
        ZipEntry entry;
        while (entries.hasMoreElements()) {
            entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith(APK_LIB_DIR_PREFIX + abi) || !name.endsWith(APK_LIB_SUFFIX)) {
                continue;
            }

            int lastSlash = name.lastIndexOf("/");
            InputStream entryInputStream = null;
            try {
                entryInputStream = apk.getInputStream(entry);
                String soFileName = name.substring(lastSlash);
                PluginDebugLog.installFormatLog(TAG, "libDir: %s, soFileName: %s", libDir, soFileName);
                File targetSo = new File(libDir, soFileName);
                if (targetSo.exists()) {
                    PluginDebugLog.installFormatLog(TAG, "soFileName: %s already exist", soFileName);
                } else {
                    installResult = copyToFile(entryInputStream, targetSo);
                }
            } catch (IOException e) {
                // ignore
            } catch (ArrayIndexOutOfBoundsException e) {
                // ignore
            } finally {
                closeQuietly(entryInputStream);
            }
        }
        return installResult;
    }

    /**
     * Deletes a directory recursively.
     *
     * @param directory directory to delete
     * @throws IOException in case deletion is unsuccessful
     */
    public static boolean deleteDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return true;
        }

        boolean deleted = false;
        try {
            cleanDirectory(directory);
            deleted = true;
        } catch (Exception e) {
            // ignore
        }
        // delete directory self
        return directory.delete() && deleted;
    }

    /**
     * Cleans a directory without deleting it.
     *
     * @param directory directory to clean
     * @throws IOException in case cleaning is unsuccessful
     */
    public static void cleanDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            String message = directory + " does not exist";
            throw new IllegalArgumentException(message);
        }

        if (!directory.isDirectory()) {
            String message = directory + " is not a directory";
            throw new IllegalArgumentException(message);
        }

        File[] files = directory.listFiles();
        if (files == null) { // null if security restricted
            throw new IOException("Failed to list contents of " + directory);
        }

        IOException exception = null;
        for (File file : files) {
            try {
                forceDelete(file);
            } catch (IOException ioe) {
                exception = ioe;
            }
        }

        if (null != exception) {
            throw exception;
        }
    }

    /**
     * Deletes a file. If file is a directory, delete it and all
     * sub-directories. <p> The difference between File.delete() and this method
     * are: <ul> <li>A directory to be deleted does not have to be empty.</li>
     * <li>You get exceptions when a file or directory cannot be deleted.
     * (java.io.File methods returns a boolean)</li> </ul>
     *
     * @param file file or directory to delete, must not be <code>null</code>
     * @throws NullPointerException if the directory is <code>null</code>
     * @throws FileNotFoundException if the file was not found
     * @throws IOException in case deletion is unsuccessful
     */
    public static void forceDelete(File file) throws IOException {
        if (file.isDirectory()) {
            deleteDirectory(file);
        } else {
            boolean filePresent = file.exists();
            if (!file.delete()) {
                if (!filePresent) {
                    throw new FileNotFoundException("File does not exist: " + file);
                }
                String message = "Unable to delete file: " + file;
                throw new IOException(message);
            }
        }
    }

    /**
     * 迁移文件
     * @param sourceFile  源文件
     * @param targetFile  目标文件
     */
    public static void moveFile(File sourceFile, File targetFile) {
        moveFile(sourceFile, targetFile, true);
    }

    /**
     * 迁移文件
     * @param sourceFile  源文件
     * @param targetFile  目标文件
     * @param needDeleteSource  是否删除源文件
     */
    public static void moveFile(File sourceFile, File targetFile, boolean needDeleteSource) {
        copyToFile(sourceFile, targetFile);
        if (needDeleteSource) {
            sourceFile.delete();
        }
    }

    /**
     * 检测生成的oat文件是否损坏，如果已经损坏则删除
     * @param optDir
     * @param apkFile
     */
    public static void checkOtaFileValid(File optDir, File apkFile) {
        // see issue https://github.com/Tencent/tinker/issues/328
        String apkName = apkFile.getName();
        String oatName = apkName.substring(0, apkName.indexOf(PluginInstaller.APK_SUFFIX)) + PluginInstaller.DEX_SUFFIX;
        File oatFile = new File(optDir, oatName);
        if (!oatFile.exists() || !oatFile.canRead()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            PluginDebugLog.runtimeFormatLog(TAG, "check dexopt oat file format: %s, size: %d",
                    oatFile.getAbsolutePath(), oatFile.length());
            int type;
            try {
                type = ShareElfFile.getFileTypeByMagic(oatFile);
            } catch (IOException e) {
                // read error just ignore
                return;
            }
            if (type == ShareElfFile.FILE_TYPE_ELF) {
                ShareElfFile elfFile = null;
                try {
                    elfFile = new ShareElfFile(oatFile);
                } catch (Throwable tr) {
                    PluginDebugLog.warningFormatLog("oat file %s is not elf format, try to delete it", oatFile.getAbsolutePath());
                    oatFile.delete();
                    ErrorUtil.throwErrorIfNeed(tr);
                } finally {
                    closeQuietly(elfFile);
                }
            }
        }
    }

    /**
     * 判断当前手机的指令集
     */
    private static String currentInstructionSet = null;
    public static String getCurrentInstructionSet() throws Exception {
        if (currentInstructionSet != null) {
            return currentInstructionSet;
        }

        Class<?> clazz = Class.forName("dalvik.system.VMRuntime");
        Method currentGet = clazz.getDeclaredMethod("getCurrentInstructionSet");

        currentInstructionSet = (String) currentGet.invoke(null);
        Log.d(TAG, "getCurrentInstructionSet:" + currentInstructionSet);
        return currentInstructionSet;
    }

    /**
     * 获取当前进程名
     */
    private static String currentProcessName = null;
    public static String getCurrentProcessName(Context context) {
        if (!TextUtils.isEmpty(currentProcessName)) {
            return currentProcessName;
        }

        int pid = android.os.Process.myPid();
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses()) {
            if (process.pid == pid) {
                return process.processName;
            }
        }

        // try to read process name in /proc/pid/cmdline if no result from activity manager
        String cmdline = null;
        BufferedReader processFileReader = null;
        try {
            processFileReader = new BufferedReader(new FileReader(String.format(Locale.getDefault(), "/proc/%d/cmdline", Process.myPid())));
            cmdline = processFileReader.readLine().trim();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            closeQuietly(processFileReader);
        }

        return cmdline;
    }


    public static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    public static void closeQuietly(ZipFile zipFile) {
        if (zipFile != null) {
            try {
                zipFile.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }



    static class CentralDirectory {
        long offset;
        long size;
    }

    /* redefine those constant here because of bug 13721174 preventing to compile using the
     * constants defined in ZipFile */
    private static final int ENDHDR = 22;
    private static final int ENDSIG = 0x6054b50;

    /**
     * Size of reading buffers.
     */
    private static final int BUFFER_SIZE = 0x4000;

    /**
     * Compute crc32 of the central directory of an apk. The central directory contains
     * the crc32 of each entries in the zip so the computed result is considered valid for the whole
     * zip file. Does not support zip64 nor multidisk but it should be OK for now since ZipFile does
     * not either.
     */
    static long getZipCrc(File apk) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(apk, "r");
        try {
            CentralDirectory dir = findCentralDirectory(raf);

            return computeCrcOfCentralDir(raf, dir);
        } finally {
            raf.close();
        }
    }

    /* Package visible for testing */
    static CentralDirectory findCentralDirectory(RandomAccessFile raf) throws IOException,
            ZipException {
        long scanOffset = raf.length() - ENDHDR;
        if (scanOffset < 0) {
            throw new ZipException("File too short to be a zip file: " + raf.length());
        }

        long stopOffset = scanOffset - 0x10000 /* ".ZIP file comment"'s max length */;
        if (stopOffset < 0) {
            stopOffset = 0;
        }

        int endSig = Integer.reverseBytes(ENDSIG);
        while (true) {
            raf.seek(scanOffset);
            if (raf.readInt() == endSig) {
                break;
            }

            scanOffset--;
            if (scanOffset < stopOffset) {
                throw new ZipException("End Of Central Directory signature not found");
            }
        }
        // Read the End Of Central Directory. ENDHDR includes the signature
        // bytes,
        // which we've already read.

        // Pull out the information we need.
        raf.skipBytes(2); // diskNumber
        raf.skipBytes(2); // diskWithCentralDir
        raf.skipBytes(2); // numEntries
        raf.skipBytes(2); // totalNumEntries
        CentralDirectory dir = new CentralDirectory();
        dir.size = Integer.reverseBytes(raf.readInt()) & 0xFFFFFFFFL;
        dir.offset = Integer.reverseBytes(raf.readInt()) & 0xFFFFFFFFL;
        return dir;
    }

    /* Package visible for testing */
    static long computeCrcOfCentralDir(RandomAccessFile raf, CentralDirectory dir)
            throws IOException {
        CRC32 crc = new CRC32();
        long stillToRead = dir.size;
        raf.seek(dir.offset);
        int length = (int) Math.min(BUFFER_SIZE, stillToRead);
        byte[] buffer = new byte[BUFFER_SIZE];
        length = raf.read(buffer, 0, length);
        while (length != -1) {
            crc.update(buffer, 0, length);
            stillToRead -= length;
            if (stillToRead == 0) {
                break;
            }
            length = (int) Math.min(BUFFER_SIZE, stillToRead);
            length = raf.read(buffer, 0, length);
        }
        return crc.getValue();
    }
}
