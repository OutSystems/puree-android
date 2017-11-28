package com.cookpad.puree.storage;

import com.google.gson.JsonObject;

public interface PureeStorage {

    void insert(String type, JsonObject jsonLog);
    Records select(String type, int logsPerRequest);
    Records selectAll();
    int count();
    void delete(Records records);
    void truncateBufferedLogs(int maxRecords);
    void clear();
    boolean lock();
    void unlock();
}
