package io.github.vrcmteam.vrcm.presentation.vrcx

import kotlinx.cinterop.CPointer
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
import kotlin.random.Random
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Minimal PostgreSQL v3 client for iOS. It intentionally supports read-only queries only. */
@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
internal class IosPostgresClient(
    private val config: RemoteDatabaseConfig,
) : ReadOnlyDatabaseClient {

    override suspend fun query(sql: String, args: Map<String, Any?>): List<List<Any?>> =
        withContext(Dispatchers.Default) {
            require(!config.tls) {
                "iOS PostgreSQL 驱动当前只支持非 TLS 连接；请先关闭 TLS 或等待 iOS TLS 驱动接入"
            }
            val connection = SocketConnection(config.host, config.port)
            try {
                connection.startup(config)
                connection.execute("SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY")
                connection.query(toLiteralSql(sql, args))
            } finally {
                connection.close()
            }
        }

    private fun toLiteralSql(sql: String, args: Map<String, Any?>): String {
        val output = StringBuilder(sql.length + args.size * 8)
        val pattern = Regex(":([A-Za-z_][A-Za-z0-9_]*)")
        var cursor = 0
        for (match in pattern.findAll(sql)) {
            output.append(sql, cursor, match.range.first)
            val name = match.groupValues[1]
            require(args.containsKey(name)) { "查询缺少绑定参数: $name" }
            output.append(sqlLiteral(args[name]))
            cursor = match.range.last + 1
        }
        output.append(sql, cursor, sql.length)
        return output.toString()
    }

    private fun sqlLiteral(value: Any?): String = when (value) {
        null -> "NULL"
        is Number -> value.toString()
        else -> "'${value.toString().replace("'", "''")}'"
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
private class SocketConnection(private val host: String, private val port: Int) {
    private var fd: Int = -1

    fun startup(config: RemoteDatabaseConfig) {
        connectSocket()
        val payload = byteArray {
            intValue(196608)
            cstring("user"); cstring(config.username)
            cstring("database"); cstring(config.database)
            cstring("application_name"); cstring("vrcx-mobile-ios")
            byte(0)
        }
        sendMessage(null, payload)
        authenticate(config)
    }

    fun execute(sql: String) {
        query(sql)
    }

    fun query(sql: String): List<List<Any?>> {
        sendMessage('Q'.code.toByte(), sql.encodeToByteArray() + byteArrayOf(0))
        val rows = mutableListOf<List<Any?>>()
        var columns = 0
        while (true) {
            val message = readMessage()
            when (message[0].toInt() and 255) {
                'T'.code -> columns = message.intAt(0)
                'D'.code -> rows += message.readDataRow(columns)
                'C'.code -> Unit
                'Z'.code -> return rows
                'E'.code -> error(message.errorText())
                else -> Unit
            }
        }
    }

    fun close() {
        if (fd >= 0) {
            close(fd)
            fd = -1
        }
    }

    private fun authenticate(config: RemoteDatabaseConfig) {
        while (true) {
            val message = readMessage()
            when (message.intAt(0)) {
                0 -> return
                3 -> {
                    sendMessage('p'.code.toByte(), config.password.encodeToByteArray() + byteArrayOf(0))
                }
                10 -> authenticateScram(message, config)
                5 -> error("iOS PostgreSQL 驱动不支持 MD5 密码认证")
                else -> error("不支持的 PostgreSQL 认证方式: ${message.intAt(0)}")
            }
        }
    }

    private fun authenticateScram(message: ByteArray, config: RemoteDatabaseConfig) {
        val mechanisms = message.copyOfRange(4, message.size).decodeToString()
        require(mechanisms.contains("SCRAM-SHA-256")) { "PostgreSQL 未提供 SCRAM-SHA-256 认证方式" }
        val nonce = randomNonce()
        val clientFirstBare = "n=${scramName(config.username)},r=$nonce"
        val clientFirst = "n,,$clientFirstBare"
        sendMessage('p'.code.toByte(), "SCRAM-SHA-256\u0000$clientFirst".encodeToByteArray())
        val continueMessage = readMessage()
        require(continueMessage.intAt(0) == 11) { "PostgreSQL SCRAM 握手失败" }
        val serverFirst = continueMessage.copyOfRange(5, continueMessage.size).decodeToString()
        val attributes = serverFirst.split(',').associate { it.substringBefore('=') to it.substringAfter('=') }
        val serverNonce = requireNotNull(attributes["r"])
        require(serverNonce.startsWith(nonce)) { "PostgreSQL SCRAM nonce 校验失败" }
        val salt = Base64.decode(requireNotNull(attributes["s"]))
        val iterations = requireNotNull(attributes["i"]).toInt()
        val clientFinalWithoutProof = "c=biws,r=$serverNonce"
        val authMessage = "$clientFirstBare,$serverFirst,$clientFinalWithoutProof"
        val saltedPassword = hi(config.password.encodeToByteArray(), salt, iterations)
        val clientKey = hmac(saltedPassword, "Client Key".encodeToByteArray())
        val storedKey = sha256(clientKey)
        val clientSignature = hmac(storedKey, authMessage.encodeToByteArray())
        val proof = clientKey.xor(clientSignature)
        val clientFinal = "$clientFinalWithoutProof,p=${Base64.encode(proof)}"
        sendMessage('p'.code.toByte(), clientFinal.encodeToByteArray() + byteArrayOf(0))
        val finalMessage = readMessage()
        require(finalMessage.intAt(0) == 12) { "PostgreSQL SCRAM 认证失败" }
    }

    private fun connectSocket() = memScoped {
        val hints = alloc<addrinfo>().apply {
            ai_family = AF_INET
            ai_socktype = SOCK_STREAM
        }
        val result = alloc<CPointerVar<addrinfo>>()
        check(getaddrinfo(host, port.toString(), hints.ptr, result.ptr) == 0) { "无法解析 PostgreSQL 主机" }
        try {
            val address = result.value ?: error("无法解析 PostgreSQL 主机")
            fd = socket(address.pointed.ai_family, address.pointed.ai_socktype, address.pointed.ai_protocol)
            check(fd >= 0 && connect(fd, address.pointed.ai_addr, address.pointed.ai_addrlen) == 0) {
                "无法连接 PostgreSQL: $host:$port"
            }
        } finally {
            freeaddrinfo(result.value)
        }
    }

    private fun sendMessage(type: Byte?, payload: ByteArray) {
        val body = ByteArray(4 + payload.size)
        body.writeInt(4 + payload.size, 0)
        payload.copyInto(body, 4)
        val packet = if (type == null) body else byteArrayOf(type) + body
        writeAll(packet)
    }

    private fun readMessage(): ByteArray {
        val type = ByteArray(1)
        readAll(type)
        val length = ByteArray(4)
        readAll(length)
        val payload = ByteArray(length.readInt(0) - 4)
        readAll(payload)
        return byteArrayOf(type[0]) + payload
    }

    private fun writeAll(bytes: ByteArray) = bytes.usePinned { pinned ->
        var offset = 0
        while (offset < bytes.size) {
            val written = write(fd, pinned.addressOf(offset), (bytes.size - offset).convert())
            check(written > 0) { "PostgreSQL 写入失败" }
            offset += written.toInt()
        }
    }

    private fun readAll(bytes: ByteArray) = bytes.usePinned { pinned ->
        var offset = 0
        while (offset < bytes.size) {
            val count = read(fd, pinned.addressOf(offset), (bytes.size - offset).convert())
            check(count > 0) { "PostgreSQL 连接已断开" }
            offset += count.toInt()
        }
    }
}

private fun scramName(value: String) = value.replace("=", "=3D").replace(",", "=2C")

private fun randomNonce(): String = Base64.encode(Random.nextBytes(18))

private fun sha256(value: ByteArray) = VrcxCrypto.sha256(value)

private fun hmac(key: ByteArray, value: ByteArray) = VrcxCrypto.hmacSha256(key, value)

private fun hi(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
    var result = hmac(password, salt + byteArrayOf(0, 0, 0, 1))
    val first = result
    repeat(iterations - 1) {
        result = hmac(password, result)
        for (i in first.indices) first[i] = (first[i].toInt() xor result[i].toInt()).toByte()
    }
    return first
}

private fun ByteArray.xor(other: ByteArray) = ByteArray(size) { (this[it].toInt() xor other[it].toInt()).toByte() }

private class PostgresPacketBuilder {
    private val bytes = mutableListOf<Byte>()
    fun intValue(value: Int) { bytes += byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte()).toList() }
    fun byte(value: Int) { bytes += value.toByte() }
    fun cstring(value: String) { bytes += value.encodeToByteArray().toList(); byte(0) }
    fun build() = bytes.toByteArray()
}

private fun byteArray(block: PostgresPacketBuilder.() -> Unit) = PostgresPacketBuilder().apply(block).build()

private fun ByteArray.writeInt(value: Int, offset: Int) {
    this[offset] = (value shr 24).toByte(); this[offset + 1] = (value shr 16).toByte()
    this[offset + 2] = (value shr 8).toByte(); this[offset + 3] = value.toByte()
}

private fun ByteArray.readInt(offset: Int) = ((this[offset].toInt() and 255) shl 24) or
    ((this[offset + 1].toInt() and 255) shl 16) or ((this[offset + 2].toInt() and 255) shl 8) or
    (this[offset + 3].toInt() and 255)

private fun ByteArray.intAt(offset: Int) = readInt(offset + 1)

private fun ByteArray.readDataRow(columns: Int): List<Any?> {
    var offset = 5
    return (0 until columns).map {
        val length = readInt(offset); offset += 4
        if (length < 0) null else decodeToString(offset, offset + length).also { offset += length }
    }
}

private fun ByteArray.errorText(): String = decodeToString(1).split('\u0000').filter { it.length > 1 }.joinToString(" ")
