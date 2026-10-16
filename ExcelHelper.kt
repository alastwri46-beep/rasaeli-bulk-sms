package com.bulk.messages

import android.content.ContentResolver
import android.net.Uri
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class ContactItem(
    val name: String,
    val phone: String
)

object ExcelHelper {

    /**
     * يقرأ الملف تلقائياً سواء كان CSV أو Excel (XLSX / XLS)
     */
    fun readContacts(contentResolver: ContentResolver, uri: Uri): List<ContactItem> {
        val contacts = mutableListOf<ContactItem>()
        val mimeType = contentResolver.getType(uri) ?: ""
        val fileName = uri.lastPathSegment?.lowercase() ?: ""

        val isCsv = mimeType.contains("csv") || mimeType.contains("comma-separated") || fileName.endsWith(".csv")

        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        if (inputStream != null) {
            if (isCsv) {
                // قراءة CSV أصلياً فائقة السرعة بدون استهلاك ذاكرة
                contacts.addAll(readCsvStream(inputStream))
            } else {
                // قراءة ملف Excel عبر مكتبة POI الموثوقة
                try {
                    contacts.addAll(readExcelStream(inputStream))
                } catch (e: Exception) {
                    // إذا فشلت محاولة POI، نجرب القراءة كـ CSV كإجراء احترازي
                    val fallbackStream = contentResolver.openInputStream(uri)
                    if (fallbackStream != null) {
                        contacts.addAll(readCsvStream(fallbackStream))
                    }
                }
            }
        }

        return contacts
    }

    private fun readCsvStream(inputStream: InputStream): List<ContactItem> {
        val result = mutableListOf<ContactItem>()
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            var line: String? = reader.readLine()
            var nameColIdx = 0
            var phoneColIdx = 1
            var isFirstLine = true

            while (line != null) {
                val tokens = line.split(",").map { it.replace(""", "").trim() }
                if (tokens.isNotEmpty()) {
                    if (isFirstLine) {
                        // كشف تلقائي لأسماء الأعمدة في السطر الأول
                        tokens.forEachIndexed { index, title ->
                            val lower = title.lowercase()
                            if (lower.contains("اسم") || lower.contains("name")) {
                                nameColIdx = index
                            }
                            if (lower.contains("هاتف") || lower.contains("جوال") || lower.contains("phone") || lower.contains("mobile") || lower.contains("رقم")) {
                                phoneColIdx = index
                            }
                        }
                        isFirstLine = false
                    } else {
                        val name = if (tokens.size > nameColIdx) tokens[nameColIdx] else ""
                        val phone = if (tokens.size > phoneColIdx) tokens[phoneColIdx] else ""
                        val cleanPhone = SmsHelper.cleanPhoneNumber(phone)
                        if (cleanPhone.isNotEmpty()) {
                            result.add(ContactItem(name = name.ifEmpty { "عميل" }, phone = cleanPhone))
                        }
                    }
                }
                line = reader.readLine()
            }
        }
        return result
    }

    private fun readExcelStream(inputStream: InputStream): List<ContactItem> {
        val result = mutableListOf<ContactItem>()
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)

        var nameColIdx = 0
        var phoneColIdx = 1

        val headerRow = sheet.getRow(0)
        if (headerRow != null) {
            for (cell in headerRow) {
                val value = cell.toString().lowercase().trim()
                if (value.contains("اسم") || value.contains("name")) {
                    nameColIdx = cell.columnIndex
                }
                if (value.contains("هاتف") || value.contains("جوال") || value.contains("phone") || value.contains("mobile") || value.contains("رقم")) {
                    phoneColIdx = cell.columnIndex
                }
            }
        }

        for (i in 1..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            val nameCell = row.getCell(nameColIdx)
            val phoneCell = row.getCell(phoneColIdx)

            val name = nameCell?.toString()?.trim() ?: ""
            val rawPhone = phoneCell?.toString()?.trim() ?: ""
            val cleanPhone = SmsHelper.cleanPhoneNumber(rawPhone)

            if (cleanPhone.isNotEmpty()) {
                result.add(ContactItem(name = name.ifEmpty { "عميل" }, phone = cleanPhone))
            }
        }

        workbook.close()
        return result
    }
}