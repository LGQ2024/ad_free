package com.example.jingwang.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.example.jingwang.core.model.PersistedSettings
import com.example.jingwang.core.model.RuleMetadata
import com.example.jingwang.core.model.TrafficStatistics
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSettingsStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    @Synchronized
    fun read(): Result<PersistedSettings> = runCatching {
        if (!file.baseFile.exists()) return Result.success(PersistedSettings())
        val encrypted = file.openRead().use { it.readBytes() }
        decode(decrypt(encrypted))
    }

    @Synchronized
    fun write(settings: PersistedSettings) {
        val encrypted = encrypt(encode(settings))
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun encrypt(plainText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(plainText)
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(FILE_MAGIC)
                output.writeInt(FILE_VERSION)
                output.writeInt(cipher.iv.size)
                output.write(cipher.iv)
                output.writeInt(cipherText.size)
                output.write(cipherText)
            }
            bytes.toByteArray()
        }
    }

    private fun decrypt(encrypted: ByteArray): ByteArray = DataInputStream(ByteArrayInputStream(encrypted)).use { input ->
        require(input.readInt() == FILE_MAGIC) { "加密状态文件标识无效" }
        require(input.readInt() == FILE_VERSION) { "不支持的加密状态文件版本" }
        val ivLength = input.readInt()
        require(ivLength in 12..16) { "GCM IV 长度无效" }
        val iv = ByteArray(ivLength).also(input::readFully)
        val cipherLength = input.readInt()
        require(cipherLength in 16..MAX_CIPHER_BYTES) { "密文长度无效" }
        val cipherText = ByteArray(cipherLength).also(input::readFully)
        require(input.read() == -1) { "加密状态文件包含尾随数据" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(cipherText)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val FILE_NAME = "private-state.bin"
        private const val KEY_ALIAS = "jingwang-private-state-v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FILE_MAGIC = 0x4a574553
        private const val FILE_VERSION = 1
        private const val MAX_CIPHER_BYTES = 512 * 1024

        internal fun encode(settings: PersistedSettings): ByteArray = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(FILE_VERSION)
                output.writeStringSet(settings.whitelist)
                output.writeStringSet(settings.bypassPackages)
                with(settings.ruleMetadata) {
                    output.writeUTF(source.take(200))
                    output.writeUTF(version.take(200))
                    output.writeLong(updatedAtEpochMillis)
                    output.writeInt(entryCount)
                    output.writeUTF(sha256.take(64))
                }
                with(settings.statistics) {
                    output.writeLong(dayEpoch)
                    output.writeLong(blockedToday)
                    output.writeLong(allowedToday)
                    output.writeLong(blockedTotal)
                    output.writeLong(allowedTotal)
                }
            }
            bytes.toByteArray()
        }

        internal fun decode(bytes: ByteArray): PersistedSettings = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == FILE_VERSION) { "不支持的状态数据版本" }
            val whitelist = input.readStringSet()
            val bypass = input.readStringSet()
            val metadata = RuleMetadata(
                source = input.readUTF(),
                version = input.readUTF(),
                updatedAtEpochMillis = input.readLong(),
                entryCount = input.readInt().coerceAtLeast(0),
                sha256 = input.readUTF().takeIf { it.matches(Regex("^[0-9a-f]{64}$")) }.orEmpty(),
            )
            val statistics = TrafficStatistics(
                dayEpoch = input.readLong(),
                blockedToday = input.readLong().coerceAtLeast(0),
                allowedToday = input.readLong().coerceAtLeast(0),
                blockedTotal = input.readLong().coerceAtLeast(0),
                allowedTotal = input.readLong().coerceAtLeast(0),
            )
            require(input.read() == -1) { "状态数据包含尾随内容" }
            PersistedSettings(whitelist, bypass, metadata, statistics)
        }

        private fun DataOutputStream.writeStringSet(values: Set<String>) {
            require(values.size <= 10_000)
            writeInt(values.size)
            values.sorted().forEach { writeUTF(it.take(253)) }
        }

        private fun DataInputStream.readStringSet(): Set<String> {
            val count = readInt()
            require(count in 0..10_000) { "集合条目数无效" }
            return buildSet(count) { repeat(count) { add(readUTF()) } }
        }
    }
}
