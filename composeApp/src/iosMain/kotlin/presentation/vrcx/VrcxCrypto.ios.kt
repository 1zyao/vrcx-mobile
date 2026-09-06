package io.github.vrcmteam.vrcm.presentation.vrcx

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCHmacAlgSHA256

@OptIn(ExperimentalForeignApi::class)
internal actual object VrcxCrypto {
    actual fun sha1(input: ByteArray): ByteArray = memScoped {
        val output = UByteArray(CC_SHA1_DIGEST_LENGTH)
        input.usePinned { inputPin ->
            output.usePinned { outputPin ->
                CC_SHA1(inputPin.addressOf(0), input.size.convert(), outputPin.addressOf(0))
            }
        }
        output.map { it.toByte() }.toByteArray()
    }

    actual fun sha256(input: ByteArray): ByteArray = memScoped {
        val output = UByteArray(CC_SHA256_DIGEST_LENGTH)
        input.usePinned { inputPin ->
            output.usePinned { outputPin ->
                CC_SHA256(inputPin.addressOf(0), input.size.convert(), outputPin.addressOf(0))
            }
        }
        output.map { it.toByte() }.toByteArray()
    }

    actual fun hmacSha256(key: ByteArray, value: ByteArray): ByteArray = memScoped {
        val output = UByteArray(CC_SHA256_DIGEST_LENGTH)
        key.usePinned { keyPin ->
            value.usePinned { valuePin ->
                output.usePinned { outputPin ->
                    CCHmac(
                        kCCHmacAlgSHA256,
                        keyPin.addressOf(0), key.size.convert(),
                        valuePin.addressOf(0), value.size.convert(), outputPin.addressOf(0),
                    )
                }
            }
        }
        output.map { it.toByte() }.toByteArray()
    }
}
