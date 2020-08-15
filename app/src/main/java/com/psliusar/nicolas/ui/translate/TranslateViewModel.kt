package com.psliusar.nicolas.ui.translate

import android.content.Context
import android.os.Handler
import androidx.camera.core.AspectRatio
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.psliusar.nicolas.ui.translate.TranslateFragment.Companion.DESIRED_HEIGHT_CROP_PERCENT
import com.psliusar.nicolas.ui.translate.TranslateFragment.Companion.DESIRED_WIDTH_CROP_PERCENT
import com.psliusar.nicolas.utils.Permissioner
import com.psliusar.nicolas.utils.SingleLiveEvent
import com.psliusar.nicolas.utils.SmoothedMutableLiveData
import com.psliusar.nicolas.utils.waitForCameraPermission
import io.reactivex.disposables.CompositeDisposable
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class TranslateViewModel(
    private val context: Context
) : ViewModel(), LifecycleObserver {

    /** Returns a list of all available translation languages. */
    val availableLanguages: List<Language>
        get() = TranslateLanguage.getAllLanguages().map(::Language)

    val targetLang = MutableLiveData<Language>()
    val sourceText = SmoothedMutableLiveData<String>(SMOOTHING_DURATION)
    val translatedText = MediatorLiveData<TranslationResult>()
    val sourceLang = MediatorLiveData<Language>()

    // We set desired crop percentages to avoid having the analyze the whole image from the live
    // camera feed. However, we are not guaranteed what aspect ratio we will get from the camera, so
    // we use the first frame we get back from the camera to update these crop percentages based on
    // the actual aspect ratio of images.
    val imageCropPercentages = MutableLiveData(
        DESIRED_HEIGHT_CROP_PERCENT to DESIRED_WIDTH_CROP_PERCENT
    )

    private val _startCamera = SingleLiveEvent<Unit>()
    val startCamera: LiveData<Unit> = _startCamera

    private val _modelDownloading = SmoothedMutableLiveData<Boolean>(SMOOTHING_DURATION)
    val modelDownloading: LiveData<Boolean> = _modelDownloading

    private val _quit = SingleLiveEvent<Unit>()
    val quit: LiveData<Unit> = _quit

    private var modelDownloadTask: Task<Void> = Tasks.forCanceled()

    private var translating = false

    private var translator: Translator? = null

    private lateinit var executor: Executor

    private lateinit var languageIdentification: LanguageIdentifier

    private val disposables = CompositeDisposable()

    init {
        sourceLang.addSource(sourceText) { text ->
            languageIdentification.identifyLanguage(text)
                .addOnSuccessListener {
                    if (it != "und") {
                        sourceLang.value = Language(it)
                    }
                }
        }

        // Create a translation result or error object.
        val processTranslation = OnCompleteListener<String> { task ->
            if (task.isSuccessful) {
                translatedText.value = TranslationResult.Success(task.result)
            } else if (!task.isCanceled) {
                translatedText.value = TranslationResult.Error(task.exception)
            }
        }

        // Start translation if any of the following change: detected text, source lang, target lang.
        translatedText.addSource(sourceText) { translate().addOnCompleteListener(processTranslation) }
        translatedText.addSource(sourceLang) { translate().addOnCompleteListener(processTranslation) }
        translatedText.addSource(targetLang) { translate().addOnCompleteListener(processTranslation) }
    }

    fun init(permissioner: Permissioner, workerExecutor: Executor) {
        executor = workerExecutor

        val identificationOptions = LanguageIdentificationOptions.Builder()
            .setExecutor(executor)
            .build()
        languageIdentification = LanguageIdentification.getClient(identificationOptions)

        _modelDownloading.setValue(false)
        translating = false

        waitForCameraPermission(
            context,
            permissioner,
            { _startCamera.call() },
            { _quit.call() }
        ).let(disposables::add)
    }

    @OnLifecycleEvent(value = Lifecycle.Event.ON_STOP)
    fun onStop() {
        disposables.clear()
    }

    override fun onCleared() {
        languageIdentification.close()
        translator?.close()
    }

    private fun translate(): Task<String> {
        if (_modelDownloading.value != false || translating) {
            return Tasks.forCanceled()
        }
        val text = sourceText.value ?: return Tasks.forResult("")
        val source = sourceLang.value ?: return Tasks.forResult("")
        val target = targetLang.value ?: return Tasks.forResult("")
        val sourceLangCode =
            TranslateLanguage.fromLanguageTag(source.code) ?: return Tasks.forCanceled()
        val targetLangCode =
            TranslateLanguage.fromLanguageTag(target.code) ?: return Tasks.forCanceled()
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLangCode)
            .setTargetLanguage(targetLangCode)
            .setExecutor(executor)
            .build()
        val translator = Translation.getClient(options)
        this.translator = translator
        _modelDownloading.setValue(true)

        // Register watchdog to unblock long running downloads
        Handler().postDelayed({ _modelDownloading.setValue(false) }, 15000)

        modelDownloadTask = translator.downloadModelIfNeeded()
            .addOnCompleteListener {
                _modelDownloading.setValue(false)
            }

        translating = true

        return modelDownloadTask
            .onSuccessTask { translator.translate(text) }
            .addOnCompleteListener { translating = false }
    }

    /**
     *  [androidx.camera.core.ImageAnalysis] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by comparing absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = ln(max(width, height).toDouble() / min(width, height))
        if (abs(previewRatio - ln(RATIO_4_3_VALUE))
            <= abs(previewRatio - ln(RATIO_16_9_VALUE))
        ) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    companion object {
        // Amount of time (in milliseconds) to wait for detected text to settle
        private const val SMOOTHING_DURATION = 50L

        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }
}