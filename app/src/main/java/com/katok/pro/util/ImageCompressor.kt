package com.katok.pro.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageCompressor {

    private const val TAG = "ImageCompressor"

    /**
     * Сжимает изображение до указанного размера
     * @param contentResolver - для чтения URI
     * @param uri - URI выбранного изображения
     * @param maxSizeBytes - максимальный размер файла (по умолчанию 2 МБ)
     * @param maxWidth - максимальная ширина
     * @param maxHeight - максимальная высота
     * @return сжатый файл или null
     */
    fun compressImage(
        contentResolver: ContentResolver,
        uri: Uri,
        maxSizeBytes: Int = 2 * 1024 * 1024,
        maxWidth: Int = 1024,
        maxHeight: Int = 1024
    ): File? {
        return try {
            Log.d(TAG, "Начинаем сжатие изображения: $uri")

            // 1. Получаем исходный Bitmap
            val originalBitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
            if (originalBitmap == null) {
                Log.e(TAG, "Не удалось декодировать изображение")
                return null
            }

            Log.d(TAG, "Оригинальный размер: ${originalBitmap.width}x${originalBitmap.height}")

            // 2. Масштабируем (если нужно)
            val scaledBitmap = if (originalBitmap.width > maxWidth || originalBitmap.height > maxHeight) {
                val scale = minOf(maxWidth.toFloat() / originalBitmap.width, maxHeight.toFloat() / originalBitmap.height)
                val newWidth = (originalBitmap.width * scale).toInt()
                val newHeight = (originalBitmap.height * scale).toInt()
                Log.d(TAG, "Масштабируем до: ${newWidth}x${newHeight}")
                Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            } else {
                Log.d(TAG, "Масштабирование не требуется")
                originalBitmap // НЕ создаём копию, используем оригинал
            }

            // 3. Сжимаем с постепенным уменьшением качества
            var quality = 90
            var fileSize = 0L
            var compressedBytes: ByteArray? = null

            while (quality >= 20) {
                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                val bytes = outputStream.toByteArray()
                fileSize = bytes.size.toLong()

                if (fileSize <= maxSizeBytes) {
                    compressedBytes = bytes
                    Log.d(TAG, "Сжатие успешно: качество=$quality%, размер=${fileSize / 1024}KB")
                    break
                }

                quality -= 10
                outputStream.close()
            }

            // Если всё ещё больше, уменьшаем разрешение вдвое
            if (compressedBytes == null || fileSize > maxSizeBytes) {
                Log.w(TAG, "Изображение всё ещё слишком большое, уменьшаем разрешение")
                val smallerBitmap = Bitmap.createScaledBitmap(
                    scaledBitmap,
                    scaledBitmap.width / 2,
                    scaledBitmap.height / 2,
                    true
                )
                val outputStream = ByteArrayOutputStream()
                smallerBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                compressedBytes = outputStream.toByteArray()
                smallerBitmap.recycle()
                Log.d(TAG, "После уменьшения разрешения: ${compressedBytes.size / 1024}KB")
            }

            // 4. Сохраняем в файл
            if (compressedBytes != null && compressedBytes.isNotEmpty()) {
                val tempFile = File.createTempFile("avatar_", ".jpg")
                FileOutputStream(tempFile).use { fos ->
                    fos.write(compressedBytes)
                }
                Log.d(TAG, "Файл сохранён: ${tempFile.absolutePath}, размер: ${tempFile.length() / 1024}KB")

                // 5. Освобождаем память (НЕ recycle() для scaledBitmap, если он используется)
                if (scaledBitmap != originalBitmap) {
                    scaledBitmap.recycle()
                }
                originalBitmap.recycle()

                return tempFile
            } else {
                Log.e(TAG, "Не удалось сжать изображение")
                originalBitmap.recycle()
                if (scaledBitmap != originalBitmap) {
                    scaledBitmap.recycle()
                }
                return null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сжатия изображения", e)
            return null
        }
    }
}