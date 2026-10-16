package com.bulk.messages

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.bulk.messages.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * تطبيق رسائلي الجماعية - متوافق مع Android 12 و Samsung Galaxy Note 10
 * يقرأ ملفات Excel / CSV ويرسل الرسائل على دفعات عبر شريحة الهاتف.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val contactsList = mutableListOf<ContactItem>()
    private val sentPhoneNumbers = mutableSetOf<String>()

    private var sendingJob: Job? = null
    private var isPaused = false
    private var isSending = false
    private var currentIndex = 0
    private var successCount = 0
    private var failCount = 0

    // منتقي الملفات الرسمي في Android لاستيراد ملفات CSV و XLSX بأمان
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            loadContactsFromUri(uri)
        }
    }

    // طلب إذن إرسال الرسائل SMS من المستخدم
    private val requestSmsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "تم منح إذن إرسال SMS بنجاح", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "يلزم منح إذن SMS لتتمكن من إرسال الرسائل من شريحة هاتفك",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        updateSmsStats()
        checkPermissions()
    }

    private fun setupListeners() {
        // زر استيراد ملف Excel أو CSV
        binding.btnImportFile.setOnClickListener {
            openDocumentLauncher.launch(
                arrayOf(
                    "text/comma-separated-values",
                    "text/csv",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel"
                )
            )
        }

        // زر حذف الأرقام المكررة
        binding.btnRemoveDuplicates.setOnClickListener {
            removeDuplicateContacts()
        }

        // إدراج وسم تخصيص الاسم في الرسالة
        binding.btnInsertNameTag.setOnClickListener {
            val currentPos = binding.etMessage.selectionStart
            val textToInsert = "{الاسم}"
            binding.etMessage.text?.insert(currentPos, textToInsert)
        }

        // تحديث إحصائيات الرسالة فور الكتابة (عدد الأحرف وأجزاء SMS)
        binding.etMessage.addTextChangedListener {
            updateSmsStats()
        }

        // زر إرسال رسالة تجريبية
        binding.btnSendTest.setOnClickListener {
            showTestDialog()
        }

        // زر بدء الإرسال الجماعي
        binding.btnStartSending.setOnClickListener {
            if (isSending) {
                togglePauseResume()
            } else {
                confirmAndStartSending()
            }
        }

        // زر إيقاف الإرسال نهائياً
        binding.btnStopSending.setOnClickListener {
            stopSending()
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    /**
     * قراءة جهات الاتصال من ملف Excel أو CSV باستخدام ContentResolver
     */
    private fun loadContactsFromUri(uri: Uri) {
        binding.progressBarLoading.visibility = View.VISIBLE
        binding.tvImportStatus.text = "جاري قراءة الملف..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val parsed = ExcelHelper.readContacts(contentResolver, uri)
                withContext(Dispatchers.Main) {
                    binding.progressBarLoading.visibility = View.GONE
                    if (parsed.isEmpty()) {
                        Toast.makeText(
                            this@MainActivity,
                            "لم يتم العثور على أرقام هواتف صالحة في الملف",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.tvImportStatus.text = "الملف فارغ أو لا يحتوي على أرقام هواتف."
                    } else {
                        contactsList.clear()
                        contactsList.addAll(parsed)
                        sentPhoneNumbers.clear()
                        currentIndex = 0
                        successCount = 0
                        failCount = 0
                        updateCountersUI()
                        updateContactsSummary()
                        Toast.makeText(
                            this@MainActivity,
                            "تم تحميل ${parsed.size} جهة اتصال بنجاح",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBarLoading.visibility = View.GONE
                    binding.tvImportStatus.text = "خطأ في قراءة الملف: ${e.localizedMessage}"
                    Toast.makeText(
                        this@MainActivity,
                        "فشل قراءة الملف: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun removeDuplicateContacts() {
        val originalSize = contactsList.size
        val uniqueSet = HashSet<String>()
        val filtered = mutableListOf<ContactItem>()

        for (contact in contactsList) {
            val clean = SmsHelper.cleanPhoneNumber(contact.phone)
            if (clean.isNotEmpty() && !uniqueSet.contains(clean)) {
                uniqueSet.add(clean)
                filtered.add(contact)
            }
        }

        val removedCount = originalSize - filtered.size
        contactsList.clear()
        contactsList.addAll(filtered)
        updateContactsSummary()

        Toast.makeText(
            this,
            if (removedCount > 0) "تم إزالة $removedCount رقم مكرر بنجاح" else "لا توجد أرقام مكررة في القائمة",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateContactsSummary() {
        binding.tvContactsCount.text = "جهات الاتصال المحملة: ${contactsList.size}"
        binding.tvImportStatus.text = "جاهز للإرسال إلى ${contactsList.size} رقم."
        binding.btnStartSending.isEnabled = contactsList.isNotEmpty()
    }

    /**
     * حساب عدد أحرف الرسالة وأجزاء الرسائل (SMS Segments)
     * للرسائل العربية: الجزء الأول 70 حرف، وإذا تعددت الأجزاء يكون كل جزء 67 حرفاً
     */
    private fun updateSmsStats() {
        val text = binding.etMessage.text?.toString() ?: ""
        val charCount = text.length
        val isArabic = SmsHelper.isArabicOrUnicode(text)

        val segments = if (charCount == 0) {
            0
        } else if (isArabic) {
            if (charCount <= 70) 1 else Math.ceil(charCount.toDouble() / 67.0).toInt()
        } else {
            if (charCount <= 160) 1 else Math.ceil(charCount.toDouble() / 153.0).toInt()
        }

        binding.tvCharCount.text = "$charCount حرف"
        binding.tvSegmentCount.text = "$segments رسالة/مستلم"
        binding.tvEncodingType.text = if (isArabic) "عربي (Unicode)" else "إنجليزي (GSM)"
    }

    /**
     * إرسال رسالة تجريبية إلى رقم هاتف محدد
     */
    private fun showTestDialog() {
        val messageTemplate = binding.etMessage.text?.toString()?.trim() ?: ""
        if (messageTemplate.isEmpty()) {
            Toast.makeText(this, "يرجى كتابة نص الرسالة أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        val input = android.widget.EditText(this).apply {
            hint = "أدخل رقم هاتفك للتجربة (مثال: 0501234567)"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }

        AlertDialog.Builder(this)
            .setTitle("إرسال رسالة تجريبية")
            .setMessage("سيتم إرسال رسالة تجريبية الآن عبر شريحتك للتأكد من وصول النص بشكل صحيح:")
            .setView(input)
            .setPositiveButton("إرسال") { _, _ ->
                val phone = input.text.toString().trim()
                if (phone.isNotEmpty()) {
                    val personalized = messageTemplate.replace("{الاسم}", "أحمد (تجريبي)")
                        .replace("{name}", "أحمد (تجريبي)")
                    sendSingleSms(phone, personalized)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    /**
     * نافذة تأكيد قبل بدء الإرسال الجماعي لتوضيح التكلفة والشروط
     */
    private fun confirmAndStartSending() {
        val messageTemplate = binding.etMessage.text?.toString()?.trim() ?: ""
        if (messageTemplate.isEmpty()) {
            Toast.makeText(this, "يرجى كتابة نص الرسالة أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        if (contactsList.isEmpty()) {
            Toast.makeText(this, "يرجى استيراد ملف الأرقام أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        val totalContacts = contactsList.size
        AlertDialog.Builder(this)
            .setTitle("تأكيد بدء الإرسال الجماعي")
            .setMessage(
                "هل أنت متأكد من رغبتك في إرسال الرسالة إلى $totalContacts مستلم؟\n\n" +
                "• سيتم الإرسال مباشرة من شريحة هاتفك (SIM).\n" +
                "• يتم تطبيق تأخير زمني بين كل رسالة لحماية شريحتك من حظر الرسائل المزعجة.\n" +
                "• يمكنك إيقاف الإرسال مؤقتاً في أي وقت."
            )
            .setPositiveButton("نعم، ابدأ الإرسال") { _, _ ->
                startBatchSending()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun startBatchSending() {
        isSending = true
        isPaused = false
        binding.btnStartSending.text = "إيقاف مؤقت ⏸️"
        binding.btnStopSending.visibility = View.VISIBLE
        binding.layoutControls.visibility = View.VISIBLE

        val delaySeconds = binding.sbDelay.progress.coerceAtLeast(3)
        val batchSize = binding.sbBatchSize.progress.coerceAtLeast(5)
        val messageTemplate = binding.etMessage.text?.toString() ?: ""

        sendingJob = lifecycleScope.launch(Dispatchers.IO) {
            var batchCounter = 0

            while (currentIndex < contactsList.size && isSending) {
                if (isPaused) {
                    delay(500)
                    continue
                }

                val contact = contactsList[currentIndex]
                val cleanPhone = SmsHelper.cleanPhoneNumber(contact.phone)

                // التحقق من عدم الإرسال للرقم نفسه مرتين
                if (!sentPhoneNumbers.contains(cleanPhone) && cleanPhone.isNotEmpty()) {
                    val personalized = messageTemplate
                        .replace("{الاسم}", contact.name)
                        .replace("{name}", contact.name)
                        .replace("{رقم}", contact.phone)

                    val success = SmsHelper.dispatchSms(this@MainActivity, cleanPhone, personalized)
                    if (success) {
                        successCount++
                        sentPhoneNumbers.add(cleanPhone)
                    } else {
                        failCount++
                    }
                } else {
                    // تم إرساله مسبقاً أو غير صالح
                    failCount++
                }

                currentIndex++
                batchCounter++

                withContext(Dispatchers.Main) {
                    updateCountersUI()
                }

                // فاصل زمني بين كل رسالة لحماية الشريحة
                delay(delaySeconds * 1000L)

                // إذا وصلنا لحجم الدفعة، نأخذ استراحة إضافية لحماية الحساب من قيود المشغّل
                if (batchCounter >= batchSize && currentIndex < contactsList.size) {
                    batchCounter = 0
                    delay(15000L) // انتظار 15 ثانية بين الدفعات
                }
            }

            withContext(Dispatchers.Main) {
                isSending = false
                isPaused = false
                binding.btnStartSending.text = "بدء الإرسال 🚀"
                binding.btnStopSending.visibility = View.GONE
                Toast.makeText(this@MainActivity, "اكتملت عملية الإرسال!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun togglePauseResume() {
        if (!isSending) return
        isPaused = !isPaused
        if (isPaused) {
            binding.btnStartSending.text = "استئناف الإرسال ▶️"
            Toast.makeText(this, "تم إيقاف الإرسال مؤقتاً", Toast.LENGTH_SHORT).show()
        } else {
            binding.btnStartSending.text = "إيقاف مؤقت ⏸️"
            Toast.makeText(this, "تم استئناف الإرسال", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopSending() {
        isSending = false
        isPaused = false
        sendingJob?.cancel()
        binding.btnStartSending.text = "بدء الإرسال 🚀"
        binding.btnStopSending.visibility = View.GONE
        Toast.makeText(this, "تم إنهاء عملية الإرسال", Toast.LENGTH_SHORT).show()
    }

    private fun sendSingleSms(phone: String, text: String) {
        val success = SmsHelper.dispatchSms(this, phone, text)
        if (success) {
            Toast.makeText(this, "تم إرسال الرسالة بنجاح إلى $phone", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "فشل الإرسال، تحقق من إذن SMS ورصيد الشريحة", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateCountersUI() {
        binding.tvSuccessCount.text = "$successCount"
        binding.tvFailCount.text = "$failCount"
        val remaining = (contactsList.size - currentIndex).coerceAtLeast(0)
        binding.tvRemainingCount.text = "$remaining"
        val progress = if (contactsList.isNotEmpty()) {
            ((currentIndex.toFloat() / contactsList.size.toFloat()) * 100).toInt()
        } else {
            0
        }
        binding.progressBarSending.progress = progress
    }
}