package io.github.vrcmteam.vrcm.presentation.vrcx

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal actual object VrcxCrypto {
    actual fun sha1(input: ByteArray) = MessageDigest.getInstance("SHA-1").digest(input)
    actual fun sha256(input: ByteArray) = MessageDigest.getInstance("SHA-256").digest(input)
    actual fun hmacSha256(key: ByteArray, value: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(value)
}
