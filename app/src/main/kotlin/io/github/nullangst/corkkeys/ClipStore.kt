/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.content.Context
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The saved clipboard history, encrypted twice over: the file sits in Android's
 * credential-encrypted storage (unreadable until the phone's first unlock after a reboot), and
 * its contents are AES-256-GCM encrypted with a key that lives in Android's Keystore. The key
 * can't be copied off the phone, and it only works while the phone is unlocked. When it can't
 * be used, nothing is read or written and the history stays in memory.
 */
class ClipStore(ctx: Context, name: String) {
    private val app = ctx.applicationContext ?: ctx
    private val file = File(app.filesDir, name)

    /** Whether the storage can be used right now: after the first unlock since boot. */
    fun ready(): Boolean = try {
        app.getSystemService(UserManager::class.java)?.isUserUnlocked != false
    } catch (_: Exception) {
        false
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUnlockedDeviceRequired(true)
                .build(),
        )
        return gen.generateKey()
    }

    /** The saved text, an empty string when nothing is saved, or null when it can't be read now. */
    fun read(): String? {
        if (!ready()) return null
        if (!file.exists()) return ""
        return try {
            val bytes = file.readBytes()
            val ivLen = bytes[0].toInt()
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 1, ivLen))
            String(c.doFinal(bytes, 1 + ivLen, bytes.size - 1 - ivLen), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun write(text: String) {
        if (!ready()) return
        try {
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.ENCRYPT_MODE, key())
            val iv = c.iv
            val body = c.doFinal(text.toByteArray(Charsets.UTF_8))
            val tmp = File(file.path + ".tmp")
            tmp.writeBytes(byteArrayOf(iv.size.toByte()) + iv + body)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (_: Exception) {
        }
    }

    fun delete() {
        file.delete()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "corkkeys_clips"
        private const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
