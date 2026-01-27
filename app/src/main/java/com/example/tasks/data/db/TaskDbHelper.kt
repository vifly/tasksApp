package com.example.tasks.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

import java.util.UUID

class TaskDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_NAME_UUID TEXT")
            // Populate UUIDs for existing rows
            val cursor = db.query(TABLE_NAME, arrayOf(COLUMN_NAME_ID), null, null, null, null, null)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_NAME_ID))
                val uuid = UUID.randomUUID().toString()
                db.execSQL("UPDATE $TABLE_NAME SET $COLUMN_NAME_UUID = '$uuid' WHERE $COLUMN_NAME_ID = $id")
            }
            cursor.close()
            // Ensure unique constraint (SQLite ALTER TABLE doesn't support adding UNIQUE directly easily with data, 
            // but for now we rely on app logic or recreate table. Simple ADD COLUMN is safer for migration)
        }
    }

    companion object {
        const val DATABASE_VERSION = 2
        const val DATABASE_NAME = "Tasks.db"

        private const val TABLE_NAME = "tasks"
        private const val COLUMN_NAME_ID = "id"
        private const val COLUMN_NAME_UUID = "uuid"
        private const val COLUMN_NAME_CONTENT = "content"
        private const val COLUMN_NAME_TAGS = "tags"
        private const val COLUMN_NAME_CREATED_AT = "created_at"
        private const val COLUMN_NAME_UPDATED_AT = "updated_at"
        private const val COLUMN_NAME_IS_PINNED = "is_pinned"
        private const val COLUMN_NAME_CUSTOM_SORT_ORDER = "custom_sort_order"

        private const val SQL_CREATE_ENTRIES = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_NAME_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME_UUID TEXT UNIQUE,
                $COLUMN_NAME_CONTENT TEXT,
                $COLUMN_NAME_TAGS TEXT,
                $COLUMN_NAME_CREATED_AT INTEGER,
                $COLUMN_NAME_UPDATED_AT INTEGER,
                $COLUMN_NAME_IS_PINNED INTEGER,
                $COLUMN_NAME_CUSTOM_SORT_ORDER INTEGER
            )
            """

        private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS $TABLE_NAME"
    }
}