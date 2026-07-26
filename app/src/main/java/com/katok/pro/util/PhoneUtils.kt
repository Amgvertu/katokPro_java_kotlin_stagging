package com.katok.pro.util

import android.text.TextUtils

object PhoneUtils {

    /**
     * Форматирует телефонный номер в формат +7 962 798 88 88
     * @param phone номер телефона (может быть в формате +7XXXXXXXXXX, 8XXXXXXXXXX или 7XXXXXXXXXX)
     * @return отформатированный номер или исходную строку, если формат не распознан
     */
    @JvmStatic
    fun formatPhoneNumberForDisplay(phone: String?): String {
        if (TextUtils.isEmpty(phone)) return ""

        // Оставляем только цифры
        val digits = phone!!.replace("[^\\d]".toRegex(), "")

        // Для российских номеров: +7 и 10 цифр
        return if (digits.length == 11 && digits.startsWith("7")) {
            // Убираем первую 7, оставляем 10 цифр
            val number = digits.substring(1)
            "+7 " + number.substring(0, 3) + " " + number.substring(3, 6) + " " +
                    number.substring(6, 8) + " " + number.substring(8, 10)
        } else if (digits.length == 10) {
            "+7 " + digits.substring(0, 3) + " " + digits.substring(3, 6) + " " +
                    digits.substring(6, 8) + " " + digits.substring(8, 10)
        } else {
            phone // не удалось распознать, возвращаем как есть
        }
    }

    /**
     * Форматирует телефонный номер:
     * - Если номер начинается с 8, заменяет на +7
     * - Если номер не содержит код страны, добавляет +7
     * - Оставляет номер как есть, если он уже в формате +7XXXXXXXXXX
     *
     * @param phone входной номер телефона
     * @return отформатированный номер в формате +7XXXXXXXXXX
     */
    @JvmStatic
    fun formatPhoneNumber(phone: String?): String {
        if (TextUtils.isEmpty(phone)) return phone!!

        // Удаляем все нецифровые символы, кроме +
        var cleaned = phone!!.replace("[^\\d+]".toRegex(), "")

        // Если номер начинается с 8, заменяем на +7
        if (cleaned.startsWith("8") && cleaned.length == 11) {
            cleaned = "+7" + cleaned.substring(1)
        }
        // Если номер начинается с 8 и имеет 10 цифр (без кода)
        else if (cleaned.startsWith("8") && cleaned.length == 10) {
            cleaned = "+7" + cleaned.substring(1)
        }
        // Если номер начинается с 7 (без +), добавляем +
        else if (cleaned.startsWith("7") && cleaned.length == 11) {
            cleaned = "+" + cleaned
        }
        // Если номер не содержит кода (10 цифр), добавляем +7
        else if (cleaned.length == 10 && !cleaned.startsWith("+")) {
            cleaned = "+7" + cleaned
        }
        // Если номер введен с +7, оставляем как есть

        return cleaned
    }

    /**
     * Проверяет корректность номера телефона
     * @param phone номер телефона
     * @return true если номер корректен (формат +7XXXXXXXXXX, где X - цифра)
     */
    @JvmStatic
    fun isValidPhoneNumber(phone: String?): Boolean {
        if (TextUtils.isEmpty(phone)) return false

        val formatted = formatPhoneNumber(phone)
        // Проверяем формат +7 и 10 цифр после +7
        return formatted.matches("^\\+7\\d{10}$".toRegex())
    }
}
