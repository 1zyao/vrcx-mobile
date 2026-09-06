package io.github.vrcmteam.vrcm.presentation.vrcx

internal object VrcxCrypto {
    fun sha1(input: ByteArray): ByteArray {
        val data = pad(input, 64, 8)
        var h0 = 0x67452301
        var h1 = 0xEFCDAB89.toInt()
        var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0.toInt()
        for (base in data.indices step 64) {
            val w = IntArray(80)
            for (i in 0 until 16) w[i] = data.intAt(base + i * 4, false)
            for (i in 16 until 80) w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotateLeft(1)
            var a = h0; var b = h1; var c = h2; var d = h3; var e = h4
            for (i in 0 until 80) {
                val f: Int; val k: Int
                when (i) {
                    in 0..19 -> { f = (b and c) or (b.inv() and d); k = 0x5A827999 }
                    in 20..39 -> { f = b xor c xor d; k = 0x6ED9EBA1 }
                    in 40..59 -> { f = (b and c) or (b and d) or (c and d); k = 0x8F1BBCDC.toInt() }
                    else -> { f = b xor c xor d; k = 0xCA62C1D6.toInt() }
                }
                val t = a.rotateLeft(5) + f + e + k + w[i]
                e = d; d = c; c = b.rotateLeft(30); b = a; a = t
            }
            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e
        }
        return intBytes(intArrayOf(h0, h1, h2, h3, h4), false)
    }

    fun sha256(input: ByteArray): ByteArray {
        val data = pad(input, 64, 8)
        val h = intArrayOf(0x6A09E667, 0xBB67AE85.toInt(), 0x3C6EF372, 0xA54FF53A.toInt(), 0x510E527F, 0x9B05688C.toInt(), 0x1F83D9AB, 0x5BE0CD19)
        val k = intArrayOf(0x428A2F98, 0x71374491, 0xB5C0FBCF.toInt(), 0xE9B5DBA5.toInt(), 0x3956C25B, 0x59F111F1, 0x923F82A4.toInt(), 0xAB1C5ED5.toInt(), 0xD807AA98.toInt(), 0x12835B01, 0x243185BE, 0x550C7DC3, 0x72BE5D74, 0x80DEB1FE.toInt(), 0x9BDC06A7.toInt(), 0xC19BF174.toInt(), 0xE49B69C1.toInt(), 0xEFBE4786.toInt(), 0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F, 0x4A7484AA, 0x5CB0A9DC, 0x76F988DA, 0x983E5152.toInt(), 0xA831C66D.toInt(), 0xB00327C8.toInt(), 0xBF597FC7.toInt(), 0xC6E00BF3.toInt(), 0xD5A79147.toInt(), 0x06CA6351, 0x14292967, 0x27B70A85, 0x2E1B2138, 0x4D2C6DFC, 0x53380D13, 0x650A7354, 0x766A0ABB, 0x81C2C92E.toInt(), 0x92722C85.toInt(), 0xA2BFE8A1.toInt(), 0xA81A664B.toInt(), 0xC24B8B70.toInt(), 0xC76C51A3.toInt(), 0xD192E819.toInt(), 0xD6990624.toInt(), 0xF40E3585.toInt(), 0x106AA070, 0x19A4C116, 0x1E376C08, 0x2748774C, 0x34B0BCB5, 0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3, 0x748F82EE, 0x78A5636F, 0x84C87814.toInt(), 0x8CC70208.toInt(), 0x90BEFFFA.toInt(), 0xA4506CEB.toInt(), 0xBEF9A3F7.toInt(), 0xC67178F2.toInt())
        for (base in data.indices step 64) {
            val w = IntArray(64)
            for (i in 0 until 16) w[i] = data.intAt(base + i * 4, false)
            for (i in 16 until 64) { val x = w[i - 15]; val y = w[i - 2]; w[i] = y.rotateRight(17) xor y.rotateRight(19) xor (y ushr 10) + w[i - 7] + (x.rotateRight(7) xor x.rotateRight(18) xor (x ushr 3)) + w[i - 16] }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var current = h[7]
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = current + s1 + ch + k[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                current = g; g = f; f = e; e = d + t1
                d = c; c = b; b = a; a = t1 + t2
            }
            val working = intArrayOf(a, b, c, d, e, f, g, current)
            for (i in 0 until 8) h[i] += working[i]
        }
        return intBytes(h, false)
    }

    fun hmacSha256(key: ByteArray, value: ByteArray): ByteArray { val k = if (key.size > 64) sha256(key) else key; val block = ByteArray(64); k.copyInto(block); val o = block.map { (it.toInt() xor 0x5c).toByte() }.toByteArray(); val i = block.map { (it.toInt() xor 0x36).toByte() }.toByteArray(); return sha256(o + sha256(i + value)) }

    private fun pad(input: ByteArray, block: Int, lengthBytes: Int): ByteArray { val size = ((input.size + 1 + lengthBytes + block - 1) / block) * block; return ByteArray(size).also { input.copyInto(it); it[input.size] = 0x80.toByte(); val bits = input.size.toLong() * 8; for (i in 0 until lengthBytes) it[size - 1 - i] = (bits ushr (i * 8)).toByte() } }
    private fun ByteArray.intAt(i: Int, little: Boolean) = if (little) (this[i].toInt() and 255) or ((this[i + 1].toInt() and 255) shl 8) or ((this[i + 2].toInt() and 255) shl 16) or ((this[i + 3].toInt() and 255) shl 24) else ((this[i].toInt() and 255) shl 24) or ((this[i + 1].toInt() and 255) shl 16) or ((this[i + 2].toInt() and 255) shl 8) or (this[i + 3].toInt() and 255)
    private fun intBytes(values: IntArray, little: Boolean) = ByteArray(values.size * 4) { i -> val v = values[i / 4]; (v ushr (if (little) (i % 4) * 8 else (3 - i % 4) * 8)).toByte() }
}
