package com.example.comfood.data

import android.content.Context
import android.net.Uri
import com.example.comfood.BuildConfig
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import java.util.UUID

const val PENDING_MEAL_PATH_PREFIX = "/pending_meal"
const val PENDING_MEAL_MESSAGE_PATH = "/pending_meal_message"

object WearPendingMealSync {
    suspend fun syncFromWear(context: Context): Int {
        return runCatching {
            val dataClient = Wearable.getDataClient(context)
            val buffer = Tasks.await(dataClient.dataItems)
            try {
                var importedCount = 0
                for (item in buffer) {
                    val path = item.uri.path.orEmpty()
                    if (!path.startsWith(PENDING_MEAL_PATH_PREFIX)) continue

                    val imported = importDataItem(context, DataMapItem.fromDataItem(item), item.uri)
                    if (imported) {
                        importedCount += 1
                    }
                }
                importedCount
            } finally {
                buffer.release()
            }
        }.getOrDefault(0)
    }

    suspend fun importDataItem(context: Context, dataMapItem: DataMapItem, uri: Uri? = null): Boolean {
        return runCatching {
            val dataMap = dataMapItem.dataMap
            importSubmission(
                context = context,
                approvalId = dataMap.getString("id").orEmpty().ifBlank { UUID.randomUUID().toString() },
                transcript = dataMap.getString("transcript").orEmpty(),
                createdAt = dataMap.getLong("createdAt").takeIf { it > 0L } ?: System.currentTimeMillis(),
                sourceDevice = dataMap.getString("sourceDevice").orEmpty().ifBlank { "watch" },
                cleanupUri = uri
            )
        }.getOrDefault(false)
    }

    suspend fun importMessagePayload(context: Context, payload: ByteArray): Boolean {
        return runCatching {
            val root = JSONObject(payload.decodeToString())
            importSubmission(
                context = context,
                approvalId = root.optString("id").ifBlank { UUID.randomUUID().toString() },
                transcript = root.optString("transcript"),
                createdAt = root.optLong("createdAt").takeIf { it > 0L } ?: System.currentTimeMillis(),
                sourceDevice = root.optString("sourceDevice").ifBlank { "watch" },
                cleanupUri = null
            )
        }.getOrDefault(false)
    }

    private suspend fun importSubmission(
        context: Context,
        approvalId: String,
        transcript: String,
        createdAt: Long,
        sourceDevice: String,
        cleanupUri: Uri?
    ): Boolean {
        if (transcript.isBlank()) return false

        val repository = ComFoodRepository(context)
        val existing = repository.loadPendingApprovals()
        if (existing.any { it.id == approvalId }) {
            cleanupUri?.let { Tasks.await(Wearable.getDataClient(context).deleteDataItems(it)) }
            return false
        }

        val loader = FoodKnowledgeLoader(context)
        val service = OpenFoodFactsService(
            brandProfiles = loader.loadBrandProfiles(),
            localMenuItems = loader.loadLocalMenuItems(),
            ingredientRules = loader.loadIngredientRules(),
            usdaService = UsdaFoodDataCentralService(BuildConfig.USDA_API_KEY)
        )

        val success = when (val result = service.estimateMeal(transcript)) {
            is MealLookupResult.Success -> {
                repository.savePendingApprovals(
                    listOf(
                        PendingMealApproval(
                            id = approvalId,
                            originalQuery = transcript,
                            sourceDevice = sourceDevice,
                            timestampUtcMillis = createdAt,
                            candidates = result.candidates
                        )
                    ) + existing
                )
                true
            }

            is MealLookupResult.Failure -> false
        }

        if (success) {
            cleanupUri?.let { Tasks.await(Wearable.getDataClient(context).deleteDataItems(it)) }
        }
        return success
    }
}
