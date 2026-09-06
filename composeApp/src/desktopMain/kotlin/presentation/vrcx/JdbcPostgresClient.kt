package io.github.vrcmteam.vrcm.presentation.vrcx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

/**
 * Desktop(JVM) 直连 PostgreSQL 的只读客户端。
 * 每次查询新建连接并强制 SESSION READ ONLY，作为账号只读权限之外的第二道防线。
 */
internal class JdbcPostgresClient(
    private val config: RemoteDatabaseConfig,
) : ReadOnlyDatabaseClient {

    init {
        Class.forName("org.postgresql.Driver")
    }

    override suspend fun query(sql: String, args: Map<String, Any?>): List<List<Any?>> =
        withContext(Dispatchers.IO) {
            newConnection().use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY")
                }
                val (positionalSql, values) = toPositional(sql, args)
                connection.prepareStatement(positionalSql).use { prepared ->
                    values.forEachIndexed { index, value -> prepared.setObject(index + 1, value) }
                    prepared.executeQuery().use { result ->
                        val columnCount = result.metaData.columnCount
                        buildList {
                            while (result.next()) {
                                add((1..columnCount).map { index -> result.getObject(index) })
                            }
                        }
                    }
                }
            }
        }

    private fun newConnection(): Connection {
        val url = "jdbc:postgresql://${config.host}:${config.port}/${config.database}" +
            "?sslmode=${if (config.tls) "prefer" else "disable"}" +
            "&connectTimeout=10&socketTimeout=60&maxResultBuffer=8388608" +
            "&ApplicationName=vrcx-mobile"
        return DriverManager.getConnection(
            url,
            Properties().apply {
                setProperty("user", config.username)
                setProperty("password", config.password)
                setProperty("loginTimeout", "10")
            },
        )
    }

    /** 将 :name 占位符改写为 JDBC 的 ?，并按出现顺序收集绑定值。 */
    private fun toPositional(sql: String, args: Map<String, Any?>): Pair<String, List<Any?>> {
        val values = mutableListOf<Any?>()
        val output = StringBuilder(sql.length + 16)
        val namePattern = Regex(":([A-Za-z_][A-Za-z0-9_]*)")
        var cursor = 0
        for (match in namePattern.findAll(sql)) {
            output.append(sql, cursor, match.range.first)
            output.append('?')
            val name = match.groupValues[1]
            require(args.containsKey(name)) { "查询缺少绑定参数: $name" }
            values.add(args[name])
            cursor = match.range.last + 1
        }
        output.append(sql, cursor, sql.length)
        return output.toString() to values
    }
}
