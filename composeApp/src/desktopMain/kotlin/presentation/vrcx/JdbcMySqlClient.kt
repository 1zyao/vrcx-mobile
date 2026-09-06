package io.github.vrcmteam.vrcm.presentation.vrcx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

/** Desktop MariaDB/MySQL 只读客户端。 */
internal class JdbcMySqlClient(
    private val config: RemoteDatabaseConfig,
) : ReadOnlyDatabaseClient {
    init {
        Class.forName("org.mariadb.jdbc.Driver")
    }

    override suspend fun query(sql: String, args: Map<String, Any?>): List<List<Any?>> =
        withContext(Dispatchers.IO) {
            newConnection().use { connection ->
                connection.createStatement().use { it.execute("SET SESSION TRANSACTION READ ONLY") }
                val (positionalSql, values) = toPositional(sql, args)
                connection.prepareStatement(positionalSql).use { prepared ->
                    values.forEachIndexed { index, value -> prepared.setObject(index + 1, value) }
                    prepared.executeQuery().use { result ->
                        val count = result.metaData.columnCount
                        buildList {
                            while (result.next()) add((1..count).map(result::getObject))
                        }
                    }
                }
            }
        }

    private fun newConnection(): Connection {
        val url = "jdbc:mariadb://${config.host}:${config.port}/${config.database}" +
            "?sslMode=${if (config.tls) "trust" else "disable"}&connectTimeout=10000&socketTimeout=60000"
        return DriverManager.getConnection(url, Properties().apply {
            setProperty("user", config.username)
            setProperty("password", config.password)
        })
    }

    private fun toPositional(sql: String, args: Map<String, Any?>): Pair<String, List<Any?>> {
        val values = mutableListOf<Any?>()
        val output = StringBuilder(sql.length + 16)
        var cursor = 0
        for (match in Regex(":([A-Za-z_][A-Za-z0-9_]*)").findAll(sql)) {
            output.append(sql, cursor, match.range.first).append('?')
            val name = match.groupValues[1]
            require(args.containsKey(name)) { "查询缺少绑定参数: $name" }
            values += args[name]
            cursor = match.range.last + 1
        }
        output.append(sql, cursor, sql.length)
        return output.toString() to values
    }
}
