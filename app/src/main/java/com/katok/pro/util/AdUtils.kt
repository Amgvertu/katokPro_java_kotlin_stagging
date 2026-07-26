package com.katok.pro.util

import com.katok.pro.model.Ad
import com.katok.pro.model.Response

object AdUtils {

    private fun copyResponse(original: Response?): Response? {
        if (original == null) return null
        return Response().apply {
            id = original.id
            adId = original.adId
            userId = original.userId
            user = original.user
            status = original.status
            message = original.message
            createdAt = original.createdAt
            responseRole = original.responseRole
        }
    }

    @JvmStatic
    fun cloneAd(original: Ad?): Ad? {
        if (original == null) return null
        return Ad().apply {
            id = original.id
            authorId = original.authorId
            author = original.author
            type = original.type
            subType = original.subType
            status = original.status
            startTime = original.startTime
            endTime = original.endTime
            level = original.level?.toList()
            city = original.city
            team = original.team
            showTeam = original.showTeam
            contactName = original.contactName
            contactPhone = original.contactPhone
            rinkIds = original.rinkIds?.toList()
            details = original.details
            goaliesCount = original.goaliesCount
            defendersCount = original.defendersCount
            forwardsCount = original.forwardsCount
            acceptedGoaliesCount = original.acceptedGoaliesCount
            acceptedDefendersCount = original.acceptedDefendersCount
            acceptedForwardsCount = original.acceptedForwardsCount
            cityId = original.cityId
            isNew = original.isNew

            // Копируем отклики (создаём новый ArrayList, чтобы избежать проблем с мутабельностью)
            responses = original.responses?.mapNotNull { copyResponse(it) }?.toList()
        }
    }

    @JvmStatic
    fun cloneAdWithNewResponse(original: Ad?, newResponse: Response?): Ad? {
        val clone = cloneAd(original) ?: return null
        val newResponseCopy = copyResponse(newResponse) ?: return clone
        // Создаём новый список, чтобы не мутировать оригинальный
        val currentResponses = clone.responses?.toMutableList() ?: mutableListOf()
        currentResponses.add(newResponseCopy)
        clone.responses = currentResponses
        return clone
    }

    @JvmStatic
    fun cloneAdWithoutResponse(original: Ad?, responseId: String?): Ad? {
        val clone = cloneAd(original) ?: return null
        if (clone.responses != null && responseId != null) {
            clone.responses = clone.responses?.filter { it.id != responseId }?.toList()
        }
        return clone
    }

    @JvmStatic
    fun cloneAdWithUpdatedResponse(original: Ad?, updatedResponse: Response?): Ad? {
        val clone = cloneAd(original) ?: return null
        if (clone.responses != null && updatedResponse != null) {
            clone.responses = clone.responses?.map { response ->
                if (response.id == updatedResponse.id) {
                    response.status = updatedResponse.status
                    response.responseRole = updatedResponse.responseRole
                }
                response
            }
        }
        return clone
    }

    @JvmStatic
    fun recalculateAdStatus(ad: Ad): Ad {
        // Если объявление уже в архиве или на модерации – не меняем
        if (ad.status == "ARCHIVED" || ad.status == "MODERATION") {
            return ad
        }

        val isFilled = when (ad.type) {
            1 -> when (ad.subType) {
                1 -> (ad.acceptedGoaliesCount ?: 0) >= (ad.goaliesCount ?: 0)
                2 -> {
                    val defFull = (ad.acceptedDefendersCount ?: 0) >= (ad.defendersCount ?: 0)
                    val fwdFull = (ad.acceptedForwardsCount ?: 0) >= (ad.forwardsCount ?: 0)
                    defFull && fwdFull
                }
                else -> false
            }
            3 -> when (ad.subType) {
                1 -> ad.responses?.any { it.status == "APPROVED" } == true
                else -> false
            }
            4 -> ad.responses?.any { it.status == "APPROVED" } == true
            else -> false
        }

        val newStatus = if (isFilled) "FILLED" else "ACTIVE"
        // Если статус не изменился, возвращаем тот же объект
        return if (ad.status == newStatus) ad else ad.copy(status = newStatus)
    }
    @JvmStatic
    fun recalculateAcceptedCounts(ad: Ad): Ad {
        val responses = ad.responses ?: return ad
        val approved = responses.filter { it.status == "APPROVED" }
        val goalies = approved.count { it.responseRole == "GOALIE" }
        val defenders = approved.count { it.responseRole == "DEFENDER" }
        val forwards = approved.count { it.responseRole == "FORWARD" }
        return ad.copy(
            acceptedGoaliesCount = goalies,
            acceptedDefendersCount = defenders,
            acceptedForwardsCount = forwards
        )
    }

}