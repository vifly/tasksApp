package com.example.tasks.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TaskDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "Tasks.db"

        private const val TABLE_NAME = "tasks"
        private const val COLUMN_NAME_ID = "id"
        private const val COLUMN_NAME_CONTENT = "content"
        private const val COLUMN_NAME_TAGS = "tags"
        private const val COLUMN_NAME_CREATED_AT = "created_at"
        private const val COLUMN_NAME_UPDATED_AT = "updated_at"
        private const val COLUMN_NAME_IS_PINNED = "is_pinned"
        private const val COLUMN_NAME_CUSTOM_SORT_ORDER = "custom_sort_order"

        private const val SQL_CREATE_ENTRIES = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_NAME_ID INTEGER PRIMARY KEY AUTOINCREMENT,
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