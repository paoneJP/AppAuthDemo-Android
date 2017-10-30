/*
 *  AppAuth for Android demonstration application.
 *    Author: Takashi Yahata (@paoneJP)
 *    Copyright: (c) 2017 Takashi Yahata
 *    License: MIT License
 */

package net.paonejp.kndzyb.appauthdemo.util

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.support.v7.app.AppCompatActivity
import android.util.Base64
import android.util.Log
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal


private val DATA_ENC_KEY_ALIAS = "data_encrytpion_key"

private val KEY_ENC_KEY_ALIAS = "key_encryption_key"
private val KEY_ENC_KEY_SUBJECT = "CN=net.paonejp.kndzyb.appauthdemo"
private val KEY_ENC_KEY_VALIDITY_YEARS = 10
private val KEY_ENC_KEY_SERIAL_NUMBER = 1L

private val BASE64_FLAGS = Base64.NO_WRAP + Base64.NO_PADDING

private val LOG_TAG = "Crypto"


fun encryptString(context: Context, data: String?): String? {
    if (data == null) {
        return null
    }
    try {
        val key = getDataEncryptionKey(context)
        val c = Cipher.getInstance("AES/CBC/PKCS7Padding")
        c.init(Cipher.ENCRYPT_MODE, key)
        return "%s.%s".format(
                Base64.encodeToString(c.iv, BASE64_FLAGS),
                Base64.encodeToString(c.doFinal(data.toByteArray()), BASE64_FLAGS))
    } catch (ex: Exception) {
        val m = Throwable().stackTrace[0]
        Log.e(LOG_TAG, "${m}: ${ex}")
        return null
    }
}


fun decryptString(context: Context, data: String?): String? {
    if (data == null) {
        return null
    }
    try {
        val d = data.split(Regex("\\."), 2)
        val key = getDataEncryptionKey(context)
        val c = Cipher.getInstance("AES/CBC/PKCS7Padding")
        c.init(Cipher.DECRYPT_MODE, key,
                IvParameterSpec(Base64.decode(d[0].toByteArray(), BASE64_FLAGS)))
        return String(c.doFinal(Base64.decode(d[1].toByteArray(), BASE64_FLAGS)))
    } catch (ex: Exception) {
        val m = Throwable().stackTrace[0]
        Log.e(LOG_TAG, "${m}: ${ex}")
        return null
    }
}


private fun getDataEncryptionKey(context: Context): SecretKey {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        return getDataEncryptionKeyAPI23orHigher()
    } else {
        return getDataEncryptionKeyAPI22orLower(context)
    }
}


@TargetApi(Build.VERSION_CODES.M)
private fun getDataEncryptionKeyAPI23orHigher(): SecretKey {
    val ks = KeyStore.getInstance("AndroidKeyStore")
    ks.load(null)
    if (ks.isKeyEntry(DATA_ENC_KEY_ALIAS)) {
        val ke = ks.getEntry(DATA_ENC_KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return ke.secretKey
    } else {
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec
                .Builder(DATA_ENC_KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT + KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .build())
        val key = kg.generateKey()
        val m = Throwable().stackTrace[0]
        Log.i(LOG_TAG, "${m}: A new data encryption key was generated.")
        return key
    }
}


private fun getDataEncryptionKeyAPI22orLower(context: Context): SecretKey {

    fun getKeyEncryptionKeyPair(): KeyPair? {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        if (ks.isKeyEntry(KEY_ENC_KEY_ALIAS)) {
            val ke = ks.getEntry(KEY_ENC_KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
            return KeyPair(ke.certificate.publicKey, ke.privateKey)
        } else {
            val kpg = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
            val startDate = Calendar.getInstance()
            val endDate = Calendar.getInstance()
            endDate.add(Calendar.YEAR, KEY_ENC_KEY_VALIDITY_YEARS)
            kpg.initialize(
                    KeyPairGeneratorSpec
                            .Builder(context)
                            .setAlias(KEY_ENC_KEY_ALIAS)
                            .setKeySize(2048)
                            .setSubject(X500Principal(KEY_ENC_KEY_SUBJECT))
                            .setSerialNumber(BigInteger.valueOf(KEY_ENC_KEY_SERIAL_NUMBER))
                            .setStartDate(startDate.time)
                            .setEndDate(endDate.time)
                            .build())
            val kp = kpg.generateKeyPair()
            val m = Throwable().stackTrace[0]
            Log.i(LOG_TAG, "${m}: A new key encryption key pair was generated.")
            return kp
        }
    }

    fun encryptAesKey(key: ByteArray): String? {
        try {
            val kp = getKeyEncryptionKeyPair()
            val c = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            c.init(Cipher.ENCRYPT_MODE, kp?.public)
            return Base64.encodeToString(c.doFinal(key), BASE64_FLAGS)
        } catch (ex: Exception) {
            val m = Throwable().stackTrace[0]
            Log.e(LOG_TAG, "${m}: ${ex}")
            return null
        }
    }

    fun decryptAesKey(key: String?): ByteArray? {
        try {
            val kp = getKeyEncryptionKeyPair()
            val c = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            c.init(Cipher.DECRYPT_MODE, kp?.private)
            return c.doFinal(Base64.decode(key, BASE64_FLAGS))
        } catch (ex: Exception) {
            val m = Throwable().stackTrace[0]
            Log.e(LOG_TAG, "${m}: ${ex}")
            return null
        }
    }

    val prefs = context.getSharedPreferences("appAuthPreference", AppCompatActivity.MODE_PRIVATE)
    var key = decryptAesKey(prefs.getString("aesSecretKey", null))
    if (key == null) {
        key = ByteArray(16)
        SecureRandom.getInstance("SHA1PRNG").nextBytes(key)
        prefs.edit().putString("aesSecretKey", encryptAesKey(key)).apply()
        val m = Throwable().stackTrace[0]
        Log.i(LOG_TAG, "${m}: A new data encryption key was generated.")
    }
    return SecretKeySpec(key, "AES")

}