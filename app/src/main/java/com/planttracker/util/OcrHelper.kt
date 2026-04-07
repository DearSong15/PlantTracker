package com.planttracker.util

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * OCR 识别结果
 */
data class OcrResult(
    val rawText: String,
    val nickname: String?,
    val matureTimeText: String?,
    val matureTimeMillis: Long?
)

/**
 * OCR 识别助手
 * 使用 ML Kit 识别屏幕上的文字
 */
class OcrHelper {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    /**
     * 识别图片中的文字
     * @param bitmap 截图
     * @return OCR 识别结果
     */
    suspend fun recognizeText(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        return@withContext suspendCancellableCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text
                    
                    // 提取昵称
                    val nickname = TimeParser.extractNickname(rawText)
                    
                    // 提取成熟时间
                    val matureTimeResult = TimeParser.extractMatureTimeFromOcr(rawText)
                    
                    val result = OcrResult(
                        rawText = rawText,
                        nickname = nickname,
                        matureTimeText = matureTimeResult?.first,
                        matureTimeMillis = matureTimeResult?.second
                    )
                    
                    continuation.resume(result)
                }
                .addOnFailureListener { e ->
                    continuation.resume(
                        OcrResult(
                            rawText = "",
                            nickname = null,
                            matureTimeText = null,
                            matureTimeMillis = null
                        )
                    )
                }
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        recognizer.close()
    }
}
