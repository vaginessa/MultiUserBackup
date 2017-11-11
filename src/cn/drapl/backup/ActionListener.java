package cn.drapl.backup;

public interface ActionListener {
    void onActionCalled(AppInfo appInfo, BackupRestoreHelper.ActionType actionType, int mode, String user);
}
