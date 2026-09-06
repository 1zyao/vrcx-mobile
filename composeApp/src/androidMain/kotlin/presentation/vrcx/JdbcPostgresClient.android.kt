package io.github.vrcmteam.vrcm.presentation.vrcx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.DriverManager

internal class JdbcPostgresClientAndroid(private val config: RemoteDatabaseConfig) : ReadOnlyDatabaseClient {
    override suspend fun query(sql: String, args: Map<String, Any?>): List<List<Any?>> = withContext(Dispatchers.IO) {
        Class.forName("org.postgresql.Driver")
        val url = "jdbc:postgresql://${config.host}:${config.port}/${config.database}?sslmode=${if (config.tls) "require" else "disable"}&connectTimeout=10&socketTimeout=60&ApplicationName=vrcx-mobile-android"
        DriverManager.getConnection(url, config.username, config.password).use { connection ->
            connection.createStatement().use { it.execute("SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY") }
            val parsed = namedParameters(sql)
            connection.prepareStatement(parsed.sql).use { statement ->
                parsed.names.forEachIndexed { index, name -> statement.setObject(index + 1, args[name]) }
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) add((1..result.metaData.columnCount).map { result.getObject(it) })
                    }
                }
            }
        }
    }
}
