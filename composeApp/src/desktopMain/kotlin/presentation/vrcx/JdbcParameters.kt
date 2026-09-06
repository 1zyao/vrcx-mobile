package io.github.vrcmteam.vrcm.presentation.vrcx

internal data class JdbcNamedSql(val sql: String, val names: List<String>)

internal fun namedParameters(sql: String): JdbcNamedSql {
    val names = mutableListOf<String>()
    val parsed = Regex(":([A-Za-z_][A-Za-z0-9_]*)").replace(sql) {
        names += it.groupValues[1]
        "?"
    }
    return JdbcNamedSql(parsed, names)
}
