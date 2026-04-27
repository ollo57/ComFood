package com.example.comfood.data

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class PhoneWearDataListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val uri = event.dataItem.uri
            if (!uri.path.orEmpty().startsWith(PENDING_MEAL_PATH_PREFIX)) return@forEach

            runBlocking(Dispatchers.IO) {
                WearPendingMealSync.importDataItem(
                    context = applicationContext,
                    dataMapItem = DataMapItem.fromDataItem(event.dataItem),
                    uri = uri
                )
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != PENDING_MEAL_MESSAGE_PATH) return
        runBlocking(Dispatchers.IO) {
            WearPendingMealSync.importMessagePayload(
                context = applicationContext,
                payload = messageEvent.data
            )
        }
    }
}
