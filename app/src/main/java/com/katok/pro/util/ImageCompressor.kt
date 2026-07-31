package com.katok.pro.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageCompressor {

    /**
     * Сжимает изображение до нужного размера и сохраняет во временный файл
     * @param contentResolver - для чтения URI
     * @param uri - URI выбранного изображения
     * @param maxSizeBytes - максимальный размер файла в байтах (по умолчанию 2 МБ)
     * @param maxWidth - максимальная ширина в пикселях (по умолчанию 1024)
     * @param maxHeight - максимальная высота в пикселях (по умолчанию 1024)
     * @return сжатый файл или null в случае ошибки
     */
    fun compressImage(
        contentResolver: ContentResolver,
        uri: Uri,
        maxSizeBytes: Int = 2 * 1024 * 1024, // 2 МБ
        maxWidth: Int = 1024,
        maxHeight: Int = 1024
    ): File? {
        return try {
            // 1. Получаем исходный Bitmap
            val originalBitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
            if (originalBitmap == null) {
                android.util.Log.e("ImageCompressor", "Не удалось декодировать изображение")
                return null
            }

            // 2. Масштабируем изображение
            val scaledBitmap = scaleBitmap(originalBitmap, maxWidth, maxHeight)
            originalBitmap.recycle() // освобождаем память

            // 3. Сжимаем с постепенным уменьшением качества
            val compressedFile = File.createTempFile("avatar_", ".jpg")
            var quality = 90
            var fileSize = 0L

            while (quality >= 20) {
                val outputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                val bytes = outputStream.toByteArray()
                fileSize = bytes.size.toLong()

                if (fileSize <= maxSizeBytes) {
                    // Сохраняем в файл
                    FileOutputStream(compressedFile).use { fos ->
                        fos.write(bytes)
                    }
                    android.util.Log.d("ImageCompressor", "Сжатие успешно: качество=$quality%, размер=${fileSize / 1024}KB")
                    break
                }

                // Уменьшаем качество и пробуем снова
                quality -= 10
                outputStream.close()
            }

            // Если всё ещё слишком большой, уменьшаем разрешение
            if (fileSize > maxSizeBytes) {
                android.util.Log.w("ImageCompressor", "Изображение всё ещё слишком большое, уменьшаем разрешение")
                val smallerBitmap = scaleBitmap(scaledBitmap, maxWidth / 2, maxHeight / 2)
                scaledBitmap.recycle()

                FileOutputStream(compressedFile).use { fos ->
                    smallerBitmap.compress(Bitmap.CompressFormat.JPEG, 70, fos)
                }
                smallerBitmap.recycle()
            }

            scaledBitmap.recycle()
            compressedFile

        } catch (e: Exception) {
            android.util.Log.e("ImageCompressor", "Ошибка сжатия изображения", e)
            null
        }
    }

    /**
     * Масштабирует Bitmap до указанных размеров с сохранением пропорций
     */
    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}