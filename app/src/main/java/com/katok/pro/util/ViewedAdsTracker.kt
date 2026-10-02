package com.katok.pro.util

/**
 * Хранит ID объявлений, просмотренных в текущей сессии приложения.
 * Используется для дедупликации вызовов POST /ads/{id}/view.
 *
 * Сессия сбрасывается при выходе пользователя из системы
 * (см. ProfileFragment.logout()).
 */
object ViewedAdsTracker {

    private val viewedAdIds = mutableSetOf<String>()

    /**
     * Отмечает объявление как просмотренное. Возвращает true,
     * если объявление ещё не было просмотрено в этой сессии.
     */
    @Synchronized
    fun markViewed(adId: String): Boolean {
        return viewedAdIds.add(adId)
    }

    @Synchronized
    fun clear() {
        viewedAdIds.clear()
    }
}