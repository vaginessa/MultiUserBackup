package dk.jens.backup;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BackupRestoreHelper {
    final static String TAG = OAndBackup.TAG;

    public enum ActionType {
        BACKUP, RESTORE
    }


    public static int backup(Context context, File backupDir, AppInfo appInfo, ShellCommands shellCommands,
                             int backupMode) {
        return BackupRestoreHelper.backup(context, backupDir, appInfo,
                shellCommands, backupMode, "0");
    }

    public static int backup(Context context, File backupDir, AppInfo appInfo, ShellCommands shellCommands,
                             int backupMode, String user) {
        int ret = 0;
        File backupSubDir = new File(backupDir, appInfo.getPackageName());
        if (!backupSubDir.exists())
            backupSubDir.mkdirs();
        else if (backupMode != AppInfo.MODE_DATA && appInfo.getSourceDir().length() > 0) {
            if (appInfo.getLogInfo() != null && appInfo.getLogInfo().getSourceDir().length() > 0 && !appInfo.getSourceDir().equals(appInfo.getLogInfo().getSourceDir())) {
                String apk = appInfo.getLogInfo().getApk();
                if (apk != null) {
                    ShellCommands.deleteBackup(new File(backupSubDir, apk));
                    if (appInfo.getLogInfo().isEncrypted())
                        ShellCommands.deleteBackup(new File(backupSubDir, apk + ".gpg"));

                }
            }
        }

        if (appInfo.isSpecial()) {
            ret = shellCommands.backupSpecial(backupSubDir, appInfo.getLabel(), appInfo.getFilesList());
            appInfo.setBackupMode(AppInfo.MODE_DATA);
        } else {
            String dataDir = appInfo.getDataDir();
            if (!user.equals("0")) {
                dataDir = dataDir.replace("/data/user/0/", "/data/user/" + user + "/");
                if (!new File(dataDir).exists()) {
                    return 10003;
                }
            }
            ret = shellCommands.doBackup(context, backupSubDir, appInfo.getLabel(), dataDir, appInfo.getSourceDir(), backupMode);
            appInfo.setBackupMode(backupMode);
        }

        shellCommands.logReturnMessage(context, ret);
        LogFile.writeLogFile(backupSubDir, appInfo, backupMode);
        return ret;
    }


    public static int restore(Context context, File backupDir, AppInfo appInfo, ShellCommands shellCommands,
                              int mode, Crypto crypto) {
        return BackupRestoreHelper.restore(context, backupDir, appInfo, shellCommands, mode, crypto, "0");
    }

    public static int restore(Context context, File backupDir, AppInfo appInfo, ShellCommands shellCommands,
                              int mode, Crypto crypto, String user) {
        int apkRet, restoreRet, permRet, cryptoRet;
        apkRet = restoreRet = permRet = cryptoRet = 0;
        File backupSubDir = new File(backupDir, appInfo.getPackageName());
        String apk = new LogFile(backupSubDir, appInfo.getPackageName()).getApk();
        String dataDir = appInfo.getDataDir();
        // extra check for needToDecrypt here because of BatchActivity which cannot really reset crypto to null for every package to restore
        if (crypto != null && Crypto.needToDecrypt(backupDir, appInfo, mode))
            crypto.decryptFromAppInfo(context, backupDir, appInfo, mode);
        if (mode == AppInfo.MODE_APK || mode == AppInfo.MODE_BOTH) {
            if (apk != null && apk.length() > 0) {
                if (appInfo.isSystem()) {
                    apkRet = shellCommands.restoreSystemApk(backupSubDir,
                            appInfo.getLabel(), apk);
                } else {
                    apkRet = shellCommands.restoreUserApk(backupSubDir,
                            appInfo.getLabel(), apk, context.getApplicationInfo().dataDir, user);
                }
                if (appInfo.isSystem() && appInfo.getLogInfo() != null) {
                    File apkFile = new File(backupDir, appInfo.getPackageName() + "/" + appInfo.getLogInfo().getApk());
                    shellCommands.copyNativeLibraries(apkFile, backupSubDir, appInfo.getPackageName());
                }
            } else if (!appInfo.isSpecial()) {
                String s = "no apk to install: " + appInfo.getPackageName();
                Log.e(TAG, s);
                ShellCommands.writeErrorLog(appInfo.getPackageName(), s);
                apkRet = 1;
            }
        }
        if (mode == AppInfo.MODE_DATA || mode == AppInfo.MODE_BOTH) {
            if (apkRet == 0 && (appInfo.isInstalled() || mode == AppInfo.MODE_BOTH)) {
                if (appInfo.isSpecial()) {
                    restoreRet = shellCommands.restoreSpecial(backupSubDir, appInfo.getLabel(), appInfo.getFilesList());
                } else {
                    if (!user.equals("0")) {
                        if (!dataDir.matches("^/data/user/\\d+/.*")) {
                            throw new RuntimeException("Invalid dataDir on restore.");
                        }

                        dataDir = dataDir.replaceFirst("^/data/user/\\d+/",
                                "/data/user/" + user + "/");
                        if (!new File(dataDir).exists()) {
                            shellCommands.installApk(null, null, appInfo.getSourceDir(), appInfo.getLabel(), user);
                        }
                    }
//                    restoreRet = shellCommands.doRestore(context, backupSubDir, appInfo.getLabel(), appInfo.getPackageName(), appInfo.getLogInfo().getDataDir());
                    restoreRet = shellCommands.doRestore(context, backupSubDir, appInfo.getLabel(), appInfo.getPackageName(), dataDir, user);

                    permRet = shellCommands.setPermissions(dataDir);
                    if (!user.equals("0")) {
                        shellCommands.setSelinux(user, dataDir);
                    }
                }
            } else {
                Log.e(TAG, "cannot restore data without restoring apk, package is not installed: " + appInfo.getPackageName());
                apkRet = 1;
                ShellCommands.writeErrorLog(appInfo.getPackageName(), context.getString(R.string.restoreDataWithoutApkError));
            }
        }
        if (crypto != null) {
            Crypto.cleanUpDecryption(appInfo, backupSubDir, mode);
            if (crypto.isErrorSet())
                cryptoRet = 1;
        }
        int ret = apkRet + restoreRet + permRet + cryptoRet;
        shellCommands.logReturnMessage(context, ret);
        return ret;
    }

    public interface OnBackupRestoreListener {
        void onBackupRestoreDone();
    }
}
