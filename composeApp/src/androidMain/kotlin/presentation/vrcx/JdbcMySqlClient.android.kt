package io.github.vrcmteam.vrcm.presentation.vrcx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.DriverManager

internal class JdbcMySqlClientAndroid(private val config: RemoteDatabaseConfig) : ReadOnlyDatabaseClient {
    override suspend fun query(sql: String, args: Map<String, Any?>): List<List<Any?>> = withContext(Dispatchers.IO) {
        Class.forName("org.mariadb.jdbc.Driver")
        val url = "jdbc:mariadb://${config.host}:${config.port}/${config.database}?sslMode=${if (config.tls) "verify-full" else "disable"}&connectTimeout=10000&socketTimeout=60000"
        DriverManager.getConnection(url, config.username, config.password).use { connection ->
            connection.createStatement().use { it.execute("SET SESSION TRANSACTION READ ONLY") }
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
