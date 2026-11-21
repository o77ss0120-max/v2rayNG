package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PennySecurityTest {

    private fun generateEcP256KeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    private fun signPayload(kp: KeyPair, payload: ByteArray): ByteArray {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(kp.private)
        sig.update(payload)
        return sig.sign()
    }

    private fun buildSignedToken(kp: KeyPair, payloadText: String, prefix: String = "PL1"): String {
        val payloadBytes = payloadText.toByteArray(StandardCharsets.UTF_8)
        val sigBytes = signPayload(kp, payloadBytes)
        return "$prefix.${PennyCryptoCodec.encodeBase64UrlNoPadding(payloadBytes)}.${PennyCryptoCodec.encodeBase64UrlNoPadding(sigBytes)}"
    }

    private fun encryptStealthVault(plaintext: String, versionMajor: Int): ByteArray {
        val plainBytes = plaintext.toByteArray(StandardCharsets.UTF_8)
        val cipher = ByteArray(plainBytes.size)
        val md = MessageDigest.getInstance("SHA-256")
        var blockIdx = 0
        var offset = 0
        while (offset < plainBytes.size) {
            val stream = md.digest(("PENNY_STEALTH_VAULT_KEY_2026_V3:$blockIdx").toByteArray(StandardCharsets.UTF_8))
            for (b in stream) {
                if (offset >= plainBytes.size) break
                cipher[offset] = (plainBytes[offset].toInt() xor b.toInt()).toByte()
                offset++
            }
            blockIdx++
        }
        val header = byteArrayOf(
            'O'.code.toByte(),
            'T'.code.toByte(),
            'T'.code.toByte(),
            'O'.code.toByte(),
            0,
            versionMajor.toByte(),
            0,
            0
        )
        return if (versionMajor >= 3) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec("PENNY_STEALTH_VAULT_MAC_2026_V3".toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            mac.update(header)
            mac.update(cipher)
            header + mac.doFinal() + cipher
        } else {
            header + cipher
        }
    }

    @Test
    fun verifyPipeLicenseTokenValidAndCustomQuota() {
        val kp = generateEcP256KeyPair()
        val pubB64 = Base64.getEncoder().encodeToString(kp.public.encoded)
        val now = 1_800_000_000_000L
        val deadline = now + 86_400_000L
        val token = buildSignedToken(kp, "ABC123|A1B2-C3D4-E5F6-0708|$deadline|90|120")

        val (verified, msg) = PennyCryptoCodec.verifyLicenseOfflineSignature(
            rawCode = token,
            currentDeviceId = "A1B2-C3D4-E5F6-0708",
            effectiveNowMs = now,
            pubKeyB64 = pubB64
        )
        assertEquals("OK", msg)
        assertNotNull(verified)
        assertEquals("ABC123", verified!!.serial)
        assertEquals(90L * 24L * 3600_000L, verified.durationMs)
        assertEquals(120L * 1024L * 1024L * 1024L, verified.maxBytes)
    }

    @Test
    fun verifyJsonLicenseTokenAndExpiryCheck() {
        val kp = generateEcP256KeyPair()
        val pubB64 = Base64.getEncoder().encodeToString(kp.public.encoded)
        val now = 1_800_000_000_000L
        val expSec = (now + 7L * 86_400_000L) / 1000L
        val maxBytes = 75L * 1024L * 1024L * 1024L
        val token = buildSignedToken(kp, """{"s":"JSON99","d":"ANY","exp":$expSec,"max":$maxBytes}""")

        val (verified, msg) = PennyCryptoCodec.verifyLicenseOfflineSignature(
            rawCode = token,
            currentDeviceId = "1111-2222-3333-4444",
            effectiveNowMs = now,
            pubKeyB64 = pubB64
        )
        assertEquals("OK", msg)
        assertNotNull(verified)
        assertEquals("JSON99", verified!!.serial)
        assertEquals(maxBytes, verified.maxBytes)
        assertTrue(verified.durationMs in (7L * 86_400_000L - 1000L)..(7L * 86_400_000L))

        val (expired, expiredMsg) = PennyCryptoCodec.verifyLicenseOfflineSignature(
            rawCode = token,
            currentDeviceId = "1111-2222-3333-4444",
            effectiveNowMs = expSec * 1000L + 10_000L,
            pubKeyB64 = pubB64
        )
        assertNull(expired)
        assertTrue(expiredMsg.contains("expired", ignoreCase = true))
    }

    @Test
    fun rejectWrongDeviceAndTamperedSignature() {
        val kp = generateEcP256KeyPair()
        val otherKp = generateEcP256KeyPair()
        val pubB64 = Base64.getEncoder().encodeToString(kp.public.encoded)
        val otherPubB64 = Base64.getEncoder().encodeToString(otherKp.public.encoded)
        val now = 1_800_000_000_000L
        val token = buildSignedToken(kp, "DEV001|AAAA-BBBB-CCCC-DDDD|${now + 60_000L}")

        val (wrongDev, devMsg) = PennyCryptoCodec.verifyLicenseOfflineSignature(
            rawCode = token,
            currentDeviceId = "9999-8888-7777-6666",
            effectiveNowMs = now,
            pubKeyB64 = pubB64
        )
        assertNull(wrongDev)
        assertFalse(devMsg.contains("AAAA-BBBB-CCCC-DDDD"))

        val (wrongKey, keyMsg) = PennyCryptoCodec.verifyLicenseOfflineSignature(
            rawCode = token,
            currentDeviceId = "AAAA-BBBB-CCCC-DDDD",
            effectiveNowMs = now,
            pubKeyB64 = otherPubB64
        )
        assertNull(wrongKey)
        assertTrue(keyMsg.contains("Invalid", ignoreCase = true))
    }

    @Test
    fun verifySignedServerListAndRejectTampering() {
        val kp = generateEcP256KeyPair()
        val pubB64 = Base64.getEncoder().encodeToString(kp.public.encoded)
        val body = "vless://11111111-1111-4111-8111-111111111111@example.com:443#Node1\nvless://22222222-2222-4222-8222-222222222222@example.net:443#Node2"
        val sigB64 = Base64.getEncoder().encodeToString(signPayload(kp, body.toByteArray(StandardCharsets.UTF_8)))
        val signedList = "#PENNY-SIG:$sigB64\n$body"

        val (ok, verifiedBody) = PennyCryptoCodec.verifySignedServerList(signedList, pubB64)
        assertTrue(ok)
        assertEquals(body, verifiedBody)

        val tampered = "$signedList\nvless://attacker@evil.example:443#Injected"
        val (tamperedOk, tamperedBody) = PennyCryptoCodec.verifySignedServerList(tampered, pubB64)
        assertFalse(tamperedOk)
        assertEquals("", tamperedBody)

        val (unsignedOk, _) = PennyCryptoCodec.verifySignedServerList(body, pubB64)
        assertFalse(unsignedOk)
    }

    @Test
    fun decryptStealthVaultV2AndAuthenticatedV3() {
        val sample = "#PENNY-SIG:TEST\nvless://example@1.1.1.1:443#Test"
        val v2Bytes = encryptStealthVault(sample, versionMajor = 2)
        assertEquals(sample, PennyCryptoCodec.decryptStealthVaultBytes(v2Bytes))

        val v3Bytes = encryptStealthVault(sample, versionMajor = 3)
        assertEquals(sample, PennyCryptoCodec.decryptStealthVaultBytes(v3Bytes))

        val tamperedV3 = v3Bytes.clone()
        tamperedV3[tamperedV3.size - 1] = (tamperedV3[tamperedV3.size - 1].toInt() xor 0xFF).toByte()
        assertEquals("", PennyCryptoCodec.decryptStealthVaultBytes(tamperedV3))

        val badMagic = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        assertEquals("", PennyCryptoCodec.decryptStealthVaultBytes(badMagic))
    }

    @Test
    fun clockRatchetResistsPermanentExpiryFromFutureJump() {
        val realNow = 1_800_000_000_000L
        val farFutureJump = realNow + 365L * 86_400_000L
        // If user accidentally set clock +1 year forward once and then corrected it,
        // local ratchet must not lock effectiveNowMs at +1 year forever.
        val resolvedWithoutServer = PennyCryptoCodec.resolveEffectiveNowMs(
            systemWallMs = realNow,
            storedLastSeenMs = farFutureJump
        )
        assertEquals(realNow, resolvedWithoutServer)

        // Small rollback (e.g., 2 days) is still resisted within the 7-day window.
        val twoDaysAhead = realNow + 2L * 86_400_000L
        val resolvedRollback = PennyCryptoCodec.resolveEffectiveNowMs(
            systemWallMs = realNow,
            storedLastSeenMs = twoDaysAhead
        )
        assertEquals(twoDaysAhead, resolvedRollback)

        // Server clock anchor takes precedence via monotonic elapsedRealtime delta.
        val resolvedFromServer = PennyCryptoCodec.resolveEffectiveNowMs(
            systemWallMs = realNow - 10L * 86_400_000L,
            storedLastSeenMs = farFutureJump,
            lastServerNowMs = realNow,
            elapsedAtServerSyncMs = 10_000L,
            currentElapsedMs = 15_000L
        )
        assertEquals(realNow + 5_000L, resolvedFromServer)
    }

    @Test
    fun hardwareIdentifiersAndSemverComparisons() {
        val digest = MessageDigest.getInstance("SHA-256").digest("test-seed".toByteArray(StandardCharsets.UTF_8))
        val devId = PennyCryptoCodec.computeDeviceIdHex(digest, bytesCount = 8)
        assertTrue(Regex("^[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}$").matches(devId))

        val uuid = PennyCryptoCodec.computeUserUuidV4(digest)
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(uuid))

        assertTrue(PennyCryptoCodec.isSemanticVersionNewer("2.9.8", "v2.9.9"))
        assertTrue(PennyCryptoCodec.isSemanticVersionNewer("2.9.8", "2.10.0"))
        assertFalse(PennyCryptoCodec.isSemanticVersionNewer("2.9.8", "v2.9.8"))
        assertFalse(PennyCryptoCodec.isSemanticVersionNewer("2.9.8", "2.9.7"))
    }
}
