package com.cookpad.puree.storage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.cookpad.puree.internal.ProcessName;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class PureeSQLiteStorage extends SQLiteOpenHelper implements PureeStorage {

    private static final String DATABASE_NAME = "puree.db";

    private static final String TABLE_NAME = "logs";

    private static final String COLUMN_NAME_TYPE = "type";

    private static final String COLUMN_NAME_LOG = "log";

    private static final int DATABASE_VERSION = 1;

    private final JsonParser jsonParser = new JsonParser();

    private final SQLiteDatabase db;

    private final AtomicBoolean lock = new AtomicBoolean(false);

    static String databaseName(Context context) {
        // do not share the database file in multi processes
        String processName = ProcessName.getAndroidProcessName(context);
        if (TextUtils.isEmpty(processName)) {
            return DATABASE_NAME;
        } else {
            return processName + "." + DATABASE_NAME;
        }
    }

    public PureeSQLiteStorage(Context context) {
        super(context, databaseName(context), null, DATABASE_VERSION);
        db = getWritableDatabase();
    }

    @Override
    public void insert(String type, JsonObject jsonLog) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_NAME_TYPE, type);
        contentValues.put(COLUMN_NAME_LOG, jsonLog.toString());
        db.insert(TABLE_NAME, null, contentValues);
    }

    @Override
    public Records select(String type, int logsPerRequest) {
        String query = "SELECT * FROM " + TABLE_NAME +
                " WHERE " + COLUMN_NAME_TYPE + " = ?" +
                " ORDER BY id ASC" +
                " LIMIT " + logsPerRequest;
        Cursor cursor = db.rawQuery(query, new String[]{type});

        try {
            return recordsFromCursor(cursor);
        } finally {
            cursor.close();
        }
    }

    @Override
    public Records selectAll() {
        String query = "SELECT * FROM " + TABLE_NAME + " ORDER BY id ASC";
        Cursor cursor = db.rawQuery(query, null);

        try {
            return recordsFromCursor(cursor);
        } finally {
            cursor.close();
        }
    }

    private Records recordsFromCursor(Cursor cursor) {
        Records records = new Records();
        while (cursor.moveToNext()) {
            Record record = buildRecord(cursor);
            records.add(record);
        }
        return records;
    }

    private Record buildRecord(Cursor cursor) {
        return new Record(
                cursor.getInt(0),
                cursor.getString(1),
                parseJsonString(cursor.getString(2)));

    }

    @Override
    public int count() {
        String query = "SELECT COUNT(*) FROM " + TABLE_NAME;
        Cursor cursor = db.rawQuery(query, null);
        int count = 0;
        if (cursor.moveToNext()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        return count;
    }

    private JsonObject parseJsonString(String jsonString) {
        return (JsonObject) jsonParser.parse(jsonString);
    }


    @Override
    public void delete(Records records) {
        String query = "DELETE FROM " + TABLE_NAME +
                " WHERE id IN (" + records.getIdsAsString() + ")";
        db.execSQL(query);
    }

    @Override
    public void truncateBufferedLogs(int maxRecords) {
        int recordSize = count();
        if (recordSize > maxRecords) {
            String query = "DELETE FROM " + TABLE_NAME +
                    " WHERE id IN ( SELECT id FROM " + TABLE_NAME +
                    " ORDER BY id ASC LIMIT " + String.valueOf(recordSize - maxRecords) +
                    ")";
            db.execSQL(query);
        }
    }

    @Override
    public void clear() {
        String query = "DELETE FROM " + TABLE_NAME;
        db.execSQL(query);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String query = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                COLUMN_NAME_TYPE + " TEXT," +
                COLUMN_NAME_LOG + " TEXT" +
                ")";
        db.execSQL(query);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.e("PureeDbHelper", "unexpected onUpgrade(db, " + oldVersion + ", " + newVersion + ")");
    }

    @Override
    protected void finalize() throws Throwable {
        db.close();
        super.finalize();
    }

    @Override
    public boolean lock() {
        return lock.compareAndSet(false, true);
    }

    @Override
    public void unlock() {
        lock.set(false);
    }
}
