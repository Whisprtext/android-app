package com.whisprtext.app.data.remote

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.whisprtext.app.WhisprTextApp
import com.whisprtext.app.data.local.entity.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles incoming Firebase Cloud Messaging (FCM) messages.
 *
 * This service is invoked by the OS when:
 *   - A DATA-ONLY push arrives while the app is backgrounded, foregrounded, or terminated.
 *
 * The backend sends only data payloads (no notification block), so this service
 * is always called — giving the app full control over notification display.
 *
 * Expected data keys:
 *   message_id, conversation_id, sender_id, content, created_at
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Called when a new FCM token is generated or the existing one is refreshed.
     * We must re-register it with the backend so pushes can reach this device.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed: ${token.take(16)}...")
        val app = application as? WhisprTextApp ?: return
        scope.launch {
            try {
                app.chatRepository.registerPushToken(token)
                Log.i(TAG, "FCM token registered with backend")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register FCM token with backend", e)
            }
        }
    }

    /**
     * Called when a data-only FCM message is received.
     *
     * This fires in ALL app states (foreground, background, killed) for data-only messages.
     * We handle two types:
     *   1. "new_message" (default) — a new chat message from another user
     *   2. "receipt_update" — a delivery/read receipt sent when the sender is offline
     *
     * For new messages we persist locally and send a delivery ack via HTTP.
     * For receipt updates we update the local message status.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        if (data.isEmpty()) {
            Log.w(TAG, "Received FCM message with no data payload – ignoring")
            return
        }

        val app = application as? WhisprTextApp ?: return
        val repo = app.chatRepository

        // Check if this is a receipt update push (sent when sender is offline)
        val pushType = data["type"]
        if ("receipt_update" == pushType) {
            handleReceiptUpdatePush(data, app)
            return
        }

        // Otherwise it's a new message push
        val messageId = data["message_id"] ?: return
        val conversationId = data["conversation_id"] ?: return
        val senderId = data["sender_id"] ?: return
        val content = data["content"] ?: ""
        val createdAt = data["created_at"] ?: ""

        Log.d(TAG, "FCM data push: message=$messageId conversation=$conversationId")

        scope.launch {
            // Parse timestamp
            val createdAtMillis = try {
                java.time.Instant.parse(createdAt).toEpochMilli()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            // Get current user ID to skip own messages (e.g. multi-device)
            val currentUserId = try {
                app.preferencesManager.userId.first() ?: ""
            } catch (e: Exception) {
                ""
            }

            // Skip if this message is from ourselves (can happen if multi-device)
            if (senderId == currentUserId) {
                Log.d(TAG, "Ignoring FCM push for own message: $messageId")
                return@launch
            }

            // Build and persist the message entity
            val entity = MessageEntity(
                id = messageId,
                conversationId = conversationId,
                senderId = senderId,
                senderDeviceId = "",
                encryptedContent = content,
                createdAt = createdAtMillis,
                syncStatus = "delivered"
            )

            try {
                // Upsert the message (avoids duplicates if WS and FCM both arrive)
                val db = app.database
                val existing = db.messageDao().getById(messageId)
                if (existing == null) {
                    db.messageDao().insert(entity)
                    Log.d(TAG, "FCM message persisted: $messageId")
                } else {
                    Log.d(TAG, "FCM message already exists locally: $messageId")
                }

                // Update conversation last message preview
                val conv = db.conversationDao().getById(conversationId)
                if (conv != null) {
                    db.conversationDao().insert(
                        conv.copy(
                            lastMessageText = content,
                            lastMessageTime = createdAtMillis
                        )
                    )
                } else {
                    // Conversation not cached yet – trigger a sync
                    repo.syncConversations()
                }

                // Send delivery receipt back to server via HTTP (WebSocket may be disconnected)
                val isViewingThisChat = repo.isAppInForeground &&
                        repo.activeConversationId == conversationId
                val receiptStatus = if (isViewingThisChat) "read" else "delivered"

                try {
                    val success = app.apiClient.sendReceipt(messageId, receiptStatus)
                    if (success) {
                        Log.d(TAG, "Receipt sent for message $messageId: $receiptStatus")
                    } else {
                        Log.w(TAG, "Receipt request returned non-success for $messageId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send receipt for message $messageId", e)
                }

                // Show a local notification if the app is backgrounded or not viewing this chat
                if (!isViewingThisChat) {
                    db.conversationDao().incrementUnreadCount(conversationId)
                    val senderName = conv?.title ?: conv?.username ?: "New message"
                    app.notificationHelper.showMessageNotification(
                        conversationId = conversationId,
                        senderName = senderName,
                        messageText = content
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist FCM message: $messageId", e)
            }
        }
    }

    /**
     * Handles a receipt_update push notification.
     * Updates the local message status when the sender notifies us of delivery/read.
     */
    private fun handleReceiptUpdatePush(data: Map<String, String>, app: WhisprTextApp) {
        val messageId = data["message_id"] ?: return
        val status = data["status"] ?: return

        Log.d(TAG, "Receipt update push: message=$messageId status=$status")

        scope.launch {
            try {
                val db = app.database
                val existing = db.messageDao().getById(messageId)
                if (existing != null) {
                    // Use monotonic status update (only upgrade, never downgrade)
                    val precedence = mapOf("pending" to 0, "sent" to 1, "delivered" to 2, "read" to 3)
                    val currentPrio = precedence[existing.syncStatus] ?: -1
                    val newPrio = precedence[status] ?: -1
                    if (newPrio > currentPrio) {
                        db.messageDao().insert(existing.copy(syncStatus = status))
                        Log.d(TAG, "Receipt update applied: $messageId -> $status")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process receipt update push: $messageId", e)
            }
        }
    }

    companion object {
        private const val TAG = "FCMService"
    }
}
