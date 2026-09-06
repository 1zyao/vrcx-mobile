package io.github.vrcmteam.vrcm.presentation.vrcx

import commoncrypto.CCHmac
import commoncrypto.CC_SHA1
import commoncrypto.CC_SHA256
import commoncrypto.kCCHmacAlgSHA256
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.UByteVar

@OptIn(ExperimentalForeignApi::class)
internal actual object VrcxCrypto {
    actual fun sha1(input: ByteArray): ByteArray = memScoped {
        val output = allocArray<UByteVar>(20)
        input.usePinned { CC_SHA1(it.addressOf(0), input.size.convert(), output) }
        ByteArray(20) { output[it].toByte() }
    }

    actual fun sha256(input: ByteArray): ByteArray = memScoped {
        val output = allocArray<UByteVar>(32)
        input.usePinned { CC_SHA256(it.addressOf(0), input.size.convert(), output) }
        ByteArray(32) { output[it].toByte() }
    }

    actual fun hmacSha256(key: ByteArray, value: ByteArray): ByteArray = memScoped {
        val output = allocArray<UByteVar>(32)
        key.usePinned { keyPin ->
            value.usePinned { valuePin ->
                CCHmac(
                    kCCHmacAlgSHA256,
                    keyPin.addressOf(0), key.size.convert(),
                    valuePin.addressOf(0), value.size.convert(), output,
                )
            }
        }
        ByteArray(32) { output[it].toByte() }
    }
}
