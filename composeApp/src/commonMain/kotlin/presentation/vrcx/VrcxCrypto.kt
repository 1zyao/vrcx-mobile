package io.github.vrcmteam.vrcm.presentation.vrcx

internal expect object VrcxCrypto {
    fun sha1(input: ByteArray): ByteArray
    fun sha256(input: ByteArray): ByteArray
    fun hmacSha256(key: ByteArray, value: ByteArray): ByteArray
}
