package cn.drapl.backup;

public interface BlacklistListener {
    void onBlacklistChanged(CharSequence[] blacklist, int id);
}
