package com.bulk.messages

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager

object SmsHelper {

    /**
     * إرسال رسالة SMS عبر مدير الرسائل الرسمي في أندرويد
     * يدعم الرسائل القصيرة والطويلة متعددة الأجزاء (Multipart SMS)
     */
    fun dispatchSms(context: Context, destinationAddress: String, text: String): Boolean {
        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // متوافق مع Android 12 (API 31/32) على Samsung Galaxy Note 10
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(text)
            if (parts.size > 1) {
                // إرسال رسالة طويلة متعددة الأجزاء
                smsManager.sendMultipartTextMessage(
                    destinationAddress,
                    null,
                    parts,
                    null,
                    null
                )
            } else {
                // إرسال رسالة عادية من جزء واحد
                smsManager.sendTextMessage(
                    destinationAddress,
                    null,
                    text,
                    null,
                    null
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * تنظيف وتوحيد رقم الهاتف وإزالة المسافات والأقواس وتحويل الأرقام العربية المشرقية (٠-٩) إلى أرقام لاتينية
     */
    fun cleanPhoneNumber(raw: String): String {
        if (raw.isEmpty()) return ""
        val arabicNums = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        var result = raw
        for (i in arabicNums.indices) {
            result = result.replace(arabicNums[i], ('0' + i))
        }

        val cleaned = StringBuilder()
        for (char in result) {
            if (char.isDigit() || char == '+') {
                cleaned.append(char)
            }
        }
        return cleaned.toString()
    }

    /**
     * فحص ما إذا كان النص يحتوي على حروف عربية أو يونيكود
     */
    fun isArabicOrUnicode(text: String): Boolean {
        for (char in text) {
            if (char.code > 127) {
                return true
            }
        }
        return false
    }
}