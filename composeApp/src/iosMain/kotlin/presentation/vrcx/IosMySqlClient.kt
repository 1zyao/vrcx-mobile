package io.github.vrcmteam.vrcm.presentation.vrcx

import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.addrinfo
import platform.posix.close
import platform.posix.connect
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.read
import platform.posix.socket
import platform.posix.write
import kotlin.experimental.xor

/** iOS 上的 MySQL/MariaDB 文本协议只读客户端。 */
@OptIn(ExperimentalForeignApi::class)
internal class IosMySqlClient(private val config: RemoteDatabaseConfig) : ReadOnlyDatabaseClient {
    override suspend fun query(sql: String, args: Map<String, Any?>): List<List<Any?>> =
        withContext(Dispatchers.Default) {
            require(!config.tls) {
                "iOS MySQL 驱动当前只支持非 TLS 连接；请先关闭 TLS 或等待 iOS TLS 驱动接入"
            }
            val connection = MySqlConnection(config.host, config.port)
            try {
                connection.connect(config)
                connection.query("SET SESSION TRANSACTION READ ONLY")
                connection.query(toLiteralSql(sql, args))
            } finally {
                connection.close()
            }
        }

    private fun toLiteralSql(sql: String, args: Map<String, Any?>): String {
        val result = StringBuilder(sql.length + args.size * 8)
        val pattern = Regex(":([A-Za-z_][A-Za-z0-9_]*)")
        var cursor = 0
        for (match in pattern.findAll(sql)) {
            result.append(sql, cursor, match.range.first)
            val name = match.groupValues[1]
            require(args.containsKey(name)) { "查询缺少绑定参数: $name" }
            result.append(sqlLiteral(args[name]))
            cursor = match.range.last + 1
        }
        result.append(sql, cursor, sql.length)
        return result.toString()
    }

    private fun sqlLiteral(value: Any?) = when (value) {
        null -> "NULL"
        is Number -> value.toString()
        else -> "'${value.toString().replace("'", "''")}'"
    }
}

@OptIn(ExperimentalForeignApi::class)
private class MySqlConnection(private val host: String, private val port: Int) {
    private var fd = -1
    private var sequence = 0

    fun connect(config: RemoteDatabaseConfig) = memScoped {
        val hints = alloc<addrinfo>().apply { ai_family = AF_INET; ai_socktype = SOCK_STREAM }
        val result = alloc<CPointerVar<addrinfo>>()
        check(getaddrinfo(host, port.toString(), hints.ptr, result.ptr) == 0) { "无法解析 MySQL 主机" }
        try {
            val address = result.value ?: error("无法解析 MySQL 主机")
            fd = socket(address.pointed.ai_family, address.pointed.ai_socktype, address.pointed.ai_protocol)
            check(fd >= 0 && connect(fd, address.pointed.ai_addr, address.pointed.ai_addrlen) == 0) {
                "无法连接 MySQL: $host:$port"
            }
        } finally {
            freeaddrinfo(result.value)
        }
        val handshake = parseHandshake(readPacket())
        require(handshake.plugin == "mysql_native_password") {
            "iOS MySQL 驱动暂不支持 ${handshake.plugin}，仅支持 mysql_native_password"
        }
        val response = MySqlPacketBuilder()
            .intValue(0x00088205)
            .intValue(16 * 1024 * 1024)
            .byte(33)
            .zeros(23)
            .cstring(config.username)
            .lengthBytes(mysqlNativePassword(config.password.encodeToByteArray(), handshake.scramble))
            .cstring(config.database)
            .cstring(handshake.plugin)
            .build()
        writePacket(response)
        val auth = readPacket()
        require(auth.firstOrNull()?.toInt()?.and(255) == 0) { "MySQL 认证失败" }
    }

    fun query(sql: String): List<List<Any?>> {
        sequence = 0
        writePacket(byteArrayOf(3) + sql.encodeToByteArray())
        val first = readPacket()
        if ((first[0].toInt() and 255) == 0) return emptyList()
        if ((first[0].toInt() and 255) == 255) error("MySQL 查询失败")
        val columnCount = readLength(first, 0).first.toInt()
        repeat(columnCount) { readPacket() }
        readPacket()
        return buildList {
            while (true) {
                val row = readPacket()
                val marker = row[0].toInt() and 255
                if (marker == 254 && row.size < 9) break
                if (marker == 255) error("MySQL 查询失败")
                var offset = 0
                add((0 until columnCount).map {
                    val value = readLength(row, offset)
                    offset = value.second
                    value.third
                })
            }
        }
    }

    fun close() {
        if (fd >= 0) close(fd)
        fd = -1
    }

    private fun writePacket(payload: ByteArray) {
        val header = byteArrayOf(
            (payload.size and 255).toByte(), (payload.size shr 8 and 255).toByte(),
            (payload.size shr 16 and 255).toByte(), sequence.toByte(),
        )
        sequence++
        writeAll(header + payload)
    }

    private fun readPacket(): ByteArray {
        val header = readAll(4)
        val length = (header[0].toInt() and 255) or
            ((header[1].toInt() and 255) shl 8) or ((header[2].toInt() and 255) shl 16)
        sequence = (header[3].toInt() and 255) + 1
        return readAll(length)
    }

    private fun writeAll(bytes: ByteArray) = bytes.usePinned { pin ->
        var offset = 0
        while (offset < bytes.size) {
            val count = write(fd, pin.addressOf(offset), (bytes.size - offset).convert())
            check(count > 0) { "MySQL 写入失败" }
            offset += count.toInt()
        }
    }

    private fun readAll(size: Int): ByteArray {
        val bytes = ByteArray(size)
        bytes.usePinned { pin ->
            var offset = 0
            while (offset < size) {
                val count = read(fd, pin.addressOf(offset), (size - offset).convert())
                check(count > 0) { "MySQL 连接已断开" }
                offset += count.toInt()
            }
        }
        return bytes
    }
}

private data class MySqlHandshake(val scramble: ByteArray, val plugin: String)

private fun parseHandshake(packet: ByteArray): MySqlHandshake {
    var offset = 0
    require((packet[offset++].toInt() and 255) == 10) { "不支持的 MySQL 协议版本" }
    while (packet[offset++] != 0.toByte()) Unit
    offset += 4
    val first = packet.copyOfRange(offset, offset + 8)
    offset += 9
    val low = packet.readShort(offset); offset += 2
    if (offset >= packet.size) return MySqlHandshake(first, "mysql_native_password")
    val charset = packet[offset++]
    offset += 2
    val high = packet.readShort(offset); offset += 2
    val capabilities = low or (high shl 16)
    val length = packet[offset++].toInt() and 255
    offset += 10
    // The first eight scramble bytes were already consumed above.
    val secondSize = if (capabilities and 0x8000 != 0) maxOf(13, length - 8) else 0
    val second = packet.copyOfRange(offset, minOf(offset + secondSize, packet.size)).dropLastWhile { it == 0.toByte() }
    offset += secondSize
    val plugin = if (capabilities and 0x00080000 != 0 && offset < packet.size) {
        packet.decodeToString(offset).substringBefore('\u0000')
    } else "mysql_native_password"
    @Suppress("UNUSED_VARIABLE") val ignoredCharset = charset
    return MySqlHandshake(first + second, plugin)
}

private fun sha1(value: ByteArray) = VrcxCrypto.sha1(value)

private fun mysqlNativePassword(password: ByteArray, scramble: ByteArray): ByteArray {
    if (password.isEmpty()) return ByteArray(0)
    val stage1 = sha1(password)
    val stage2 = sha1(stage1)
    val digest = sha1(scramble + stage2)
    return digest.mapIndexed { index, value -> value xor stage1[index] }.toByteArray()
}

private fun ByteArray.readShort(offset: Int) = (this[offset].toInt() and 255) or
    ((this[offset + 1].toInt() and 255) shl 8)

private fun readLength(packet: ByteArray, start: Int): Triple<Long, Int, String?> {
    val marker = packet[start].toInt() and 255
    if (marker == 251) return Triple(-1, start + 1, null)
    val (length, offset) = when (marker) {
        in 0..250 -> marker.toLong() to start + 1
        252 -> packet.readShort(start + 1).toLong() to start + 3
        253 -> ((packet[start + 1].toInt() and 255) or ((packet[start + 2].toInt() and 255) shl 8) or ((packet[start + 3].toInt() and 255) shl 16)).toLong() to start + 4
        else -> error("不支持的 MySQL 长度编码")
    }
    return Triple(length, offset + length.toInt(), packet.decodeToString(offset, offset + length.toInt()))
}

private class MySqlPacketBuilder {
    private val bytes = mutableListOf<Byte>()
    fun byte(value: Int) { bytes += value.toByte() }
    fun intValue(value: Int) { repeat(4) { byte(value shr (it * 8)) } }
    fun zeros(count: Int) { repeat(count) { byte(0) } }
    fun cstring(value: String) { bytes += value.encodeToByteArray().toList(); byte(0) }
    fun lengthBytes(value: ByteArray) { byte(value.size); bytes += value.toList() }
    fun build() = bytes.toByteArray()
}
