package com.pitwall.bridge.ktor.embedded

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import java.io.File
import java.sql.*
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData

/**
 * Minimal JDBC shim over Android's [SQLiteDatabase].
 * Only the methods actually used by [EmbeddedDuckDb] and its callers are implemented;
 * everything else throws [UnsupportedOperationException].
 */

// ---------- Connection ----------
internal class AndroidSqliteConnection(private val db: SQLiteDatabase) : Connection by UnsupportedConnection() {
    override fun prepareStatement(sql: String?): PreparedStatement =
        AndroidSqlitePreparedStatement(db, sql ?: "")

    override fun createStatement(): Statement = AndroidSqliteStatement(db)

    override fun close() = db.close()
    override fun isClosed(): Boolean = !db.isOpen
    override fun getAutoCommit(): Boolean = true
    override fun setAutoCommit(autoCommit: Boolean) { /* no-op */ }

    companion object {
        fun open(dbFile: File): AndroidSqliteConnection {
            dbFile.parentFile?.mkdirs()
            val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            db.enableWriteAheadLogging()
            return AndroidSqliteConnection(db)
        }
    }
}

// ---------- Statement ----------
private class AndroidSqliteStatement(private val db: SQLiteDatabase) : Statement by UnsupportedStatement() {
    override fun execute(sql: String?): Boolean {
        if (sql == null) return false
        // SQLite cannot execute multiple statements in one call; split on ';'
        for (s in sql.split(';')) {
            val trimmed = s.trim()
            if (trimmed.isNotEmpty()) db.execSQL(trimmed)
        }
        return true
    }

    override fun close() { /* no-op */ }
}

// ---------- PreparedStatement ----------
private class AndroidSqlitePreparedStatement(
    private val db: SQLiteDatabase,
    private val sql: String,
) : PreparedStatement by UnsupportedPreparedStatement() {

    private val bindings = mutableMapOf<Int, Any?>()

    override fun setString(parameterIndex: Int, x: String?) { bindings[parameterIndex] = x }
    override fun setInt(parameterIndex: Int, x: Int) { bindings[parameterIndex] = x.toLong() }
    override fun setLong(parameterIndex: Int, x: Long) { bindings[parameterIndex] = x }
    override fun setDouble(parameterIndex: Int, x: Double) { bindings[parameterIndex] = x }
    override fun setNull(parameterIndex: Int, sqlType: Int) { bindings[parameterIndex] = null }

    private fun bindArgs(): Array<String?> {
        if (bindings.isEmpty()) return emptyArray()
        val max = bindings.keys.max()
        return Array(max) { i -> bindings[i + 1]?.toString() }
    }

    override fun executeQuery(): ResultSet {
        val cursor = db.rawQuery(sql, bindArgs())
        return AndroidSqliteResultSet(cursor)
    }

    override fun executeUpdate(): Int {
        val stmt = db.compileStatement(sql)
        for ((idx, value) in bindings) {
            when (value) {
                null -> stmt.bindNull(idx)
                is String -> stmt.bindString(idx, value)
                is Long -> stmt.bindLong(idx, value)
                is Double -> stmt.bindDouble(idx, value)
                else -> stmt.bindString(idx, value.toString())
            }
        }
        return try {
            stmt.executeInsert(); 1
        } catch (_: Exception) {
            try { stmt.executeUpdateDelete() } catch (_: Exception) { 0 }
        }
    }

    override fun close() { bindings.clear() }
}

// ---------- ResultSet ----------
private class AndroidSqliteResultSet(private val cursor: Cursor) : ResultSet by UnsupportedResultSet() {

    override fun next(): Boolean = cursor.moveToNext()

    override fun getString(columnIndex: Int): String? =
        if (cursor.isNull(columnIndex - 1)) null else cursor.getString(columnIndex - 1)

    override fun getInt(columnIndex: Int): Int = cursor.getInt(columnIndex - 1)
    override fun getLong(columnIndex: Int): Long = cursor.getLong(columnIndex - 1)
    override fun getDouble(columnIndex: Int): Double = cursor.getDouble(columnIndex - 1)
    override fun getBoolean(columnIndex: Int): Boolean = cursor.getInt(columnIndex - 1) != 0

    override fun getObject(columnIndex: Int): Any? {
        if (cursor.isNull(columnIndex - 1)) return null
        return when (cursor.getType(columnIndex - 1)) {
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(columnIndex - 1)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(columnIndex - 1)
            Cursor.FIELD_TYPE_STRING -> cursor.getString(columnIndex - 1)
            Cursor.FIELD_TYPE_NULL -> null
            else -> cursor.getString(columnIndex - 1)
        }
    }

    override fun getTimestamp(columnIndex: Int): Timestamp? {
        val s = getString(columnIndex) ?: return null
        return try {
            Timestamp.valueOf(s.replace('T', ' ').replace("Z", ""))
        } catch (_: Exception) {
            try { Timestamp(s.toLong()) } catch (_: Exception) { null }
        }
    }

    override fun getMetaData(): ResultSetMetaData = AndroidSqliteResultSetMetaData(cursor)

    override fun close() = cursor.close()
}

// ---------- ResultSetMetaData ----------
private class AndroidSqliteResultSetMetaData(private val cursor: Cursor) : ResultSetMetaData by UnsupportedResultSetMetaData() {
    override fun getColumnCount(): Int = cursor.columnCount
    override fun getColumnLabel(column: Int): String = cursor.getColumnName(column - 1)
    override fun getColumnType(column: Int): Int {
        // Cursor.getType only works when positioned; fall back to VARCHAR
        return try {
            when (cursor.getType(column - 1)) {
                Cursor.FIELD_TYPE_INTEGER -> Types.BIGINT
                Cursor.FIELD_TYPE_FLOAT -> Types.DOUBLE
                Cursor.FIELD_TYPE_STRING -> Types.VARCHAR
                Cursor.FIELD_TYPE_NULL -> Types.NULL
                else -> Types.VARCHAR
            }
        } catch (_: Exception) {
            Types.VARCHAR
        }
    }
}

// ---------- Proxy stubs (throw on unimplemented methods) ----------

private fun UnsupportedConnection(): Connection =
    java.lang.reflect.Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java),
    ) { _, method, _ ->
        throw UnsupportedOperationException("Connection.${method.name} not implemented in Android shim")
    } as Connection

private fun UnsupportedStatement(): Statement =
    java.lang.reflect.Proxy.newProxyInstance(
        Statement::class.java.classLoader,
        arrayOf(Statement::class.java),
    ) { _, method, _ ->
        throw UnsupportedOperationException("Statement.${method.name} not implemented in Android shim")
    } as Statement

private fun UnsupportedPreparedStatement(): PreparedStatement =
    java.lang.reflect.Proxy.newProxyInstance(
        PreparedStatement::class.java.classLoader,
        arrayOf(PreparedStatement::class.java),
    ) { _, method, _ ->
        throw UnsupportedOperationException("PreparedStatement.${method.name} not implemented in Android shim")
    } as PreparedStatement

private fun UnsupportedResultSet(): ResultSet =
    java.lang.reflect.Proxy.newProxyInstance(
        ResultSet::class.java.classLoader,
        arrayOf(ResultSet::class.java),
    ) { _, method, _ ->
        throw UnsupportedOperationException("ResultSet.${method.name} not implemented in Android shim")
    } as ResultSet

private fun UnsupportedResultSetMetaData(): ResultSetMetaData =
    java.lang.reflect.Proxy.newProxyInstance(
        ResultSetMetaData::class.java.classLoader,
        arrayOf(ResultSetMetaData::class.java),
    ) { _, method, _ ->
        throw UnsupportedOperationException("ResultSetMetaData.${method.name} not implemented in Android shim")
    } as ResultSetMetaData
