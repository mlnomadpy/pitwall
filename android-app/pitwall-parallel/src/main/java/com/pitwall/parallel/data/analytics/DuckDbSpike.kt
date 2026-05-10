package com.pitwall.parallel.data.analytics

import java.io.File
import java.sql.DriverManager

object DuckDbSpike {
    fun verifyConnection(dbFile: File): String? =
        try {
            Class.forName("org.duckdb.DuckDBDriver")
            DriverManager.getConnection("jdbc:duckdb:${dbFile.absolutePath}").use { c ->
                c.createStatement().use { st ->
                    st.executeQuery("SELECT 1 AS ok").use { rs ->
                        if (rs.next()) "duckdb_ok_${rs.getInt(1)}" else null
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
}
