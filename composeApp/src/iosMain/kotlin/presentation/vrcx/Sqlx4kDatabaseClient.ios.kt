package io.github.vrcmteam.vrcm.presentation.vrcx

import io.github.smyrgeorge.sqlx4k.Driver
import io.github.smyrgeorge.sqlx4k.Statement
import io.github.smyrgeorge.sqlx4k.mysql.MySQL
import io.github.smyrgeorge.sqlx4k.postgres.PostgreSQL

/** iOS 使用 sqlx4k Native；Android/Desktop 使用 JDBC，避免引入 R2DBC/Netty。 */
internal class Sqlx4kDatabaseClient(
    private val config: RemoteDatabaseConfig,
) : ReadOnlyDatabaseClient {
    private val driver: Driver = when (config.databaseType) {
        RemoteDatabaseType.PostgreSQL -> PostgreSQL(
            url = "postgresql://${config.host}:${config.port}/${config.database}?sslmode=${if (config.tls) "require" else "disable"}",
            username = config.username,
            password = config.password,
        )
        RemoteDatabaseType.MySQL -> MySQL(
            url = "mysql://${config.host}:${config.port}/${config.database}?sslMode=${if (config.tls) "trust" else "disable"}",
            username = config.username,
            password = config.password,
        )
    }

    override suspend fun query(sql: String, args: Map<String, Any?>): List<List<Any?>> {
        val connection = driver.acquire().getOrThrow()
        try {
            connection.execute("SET SESSION TRANSACTION READ ONLY").getOrThrow()
            val statement = Statement.create(sql)
            args.forEach { (name, value) -> statement.bind(name, value) }
            return connection.fetchAll(statement).getOrThrow().map { row ->
                (0 until row.size).map { index -> row.get(index).asStringOrNull() }
            }
        } finally {
            connection.close().getOrThrow()
        }
    }
}
