package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class VyoraRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfile_andPublishVideo_succeedsAndStreamsInFeed() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID().toString().take(6)}@test.com")
        val userRepo = UserRepository(firestore)
        val videoRepo = VideoRepository(firestore)

        val profileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            userRepo.ensureUserProfile(aliceUid, "alice@test.com", "Alice Creator", null)
        }
        assertTrue(profileResult.isSuccess)
        val profile = profileResult.getOrThrow()

        val videoId = withTimeout(DEFAULT_TIMEOUT_MS) {
            videoRepo.publishVideo(
                creator = profile,
                videoUrl = "https://storage.googleapis.com/vyora/test_${UUID.randomUUID()}.mp4",
                thumbnailUrl = "",
                caption = "Testing #vyora #compose",
                soundId = "",
                soundTitle = "Original Sound",
                durationSec = 15L
            ).getOrThrow()
        }
        assertTrue(videoId.isNotBlank())

        val videos = withTimeout(FLOW_TIMEOUT_MS) {
            videoRepo.observeActiveVideos().first { list -> list.any { it.id == videoId } }
        }
        assertTrue(videos.any { it.id == videoId && it.userId == aliceUid })
    }

    @Test
    fun deleteVideo_byDifferentUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_del_${UUID.randomUUID().toString().take(6)}@test.com")
        val userRepo = UserRepository(firestore)
        val videoRepo = VideoRepository(firestore)
        val aliceProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            userRepo.ensureUserProfile(aliceUid, "alice@test.com", "Alice", null).getOrThrow()
        }
        val videoId = withTimeout(DEFAULT_TIMEOUT_MS) {
            videoRepo.publishVideo(
                creator = aliceProfile,
                videoUrl = "https://storage.googleapis.com/vyora/clip.mp4",
                thumbnailUrl = "",
                caption = "Alice clip",
                soundId = "",
                soundTitle = "Original Sound",
                durationSec = 15L
            ).getOrThrow()
        }

        signInTestUser("bob_del_${UUID.randomUUID().toString().take(6)}@test.com")
        val bobVideoRepo = VideoRepository(firestore)
        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobVideoRepo.deleteVideo(videoId)
        }
        assertTrue(deleteResult.isFailure)
        val firestoreEx = deleteResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeActiveVideos_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val videoRepo = VideoRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                videoRepo.observeActiveVideos().first()
            }
        } catch (e: Throwable) {
            caught = e
        }
        val firestoreEx = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun chatConversation_onlyParticipantsCanReadMessages() = runBlocking {
        val aliceUid = signInTestUser("alice_chat_${UUID.randomUUID().toString().take(6)}@test.com")
        val userRepo = UserRepository(firestore)
        val chatRepo = ChatRepository(firestore)
        val aliceProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            userRepo.ensureUserProfile(aliceUid, "alice@test.com", "Alice", null).getOrThrow()
        }

        val bobUid = signInTestUser("bob_chat_${UUID.randomUUID().toString().take(6)}@test.com")
        withTimeout(DEFAULT_TIMEOUT_MS) {
            userRepo.ensureUserProfile(bobUid, "bob@test.com", "Bob", null).getOrThrow()
        }

        // Sign back in as Alice to start chat and send message
        signInTestUser("alice_chat_sender_${UUID.randomUUID().toString().take(6)}@test.com").let { senderUid ->
            val senderProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
                userRepo.ensureUserProfile(senderUid, "sender@test.com", "Sender", null).getOrThrow()
            }
            val convId = withTimeout(DEFAULT_TIMEOUT_MS) {
                chatRepo.getOrCreateConversation(bobUid).getOrThrow()
            }
            val sendRes = withTimeout(DEFAULT_TIMEOUT_MS) {
                chatRepo.sendMessage(
                    conversationId = convId,
                    participantIds = listOf(senderUid, bobUid).sorted(),
                    actor = senderProfile,
                    type = "text",
                    text = "Salam from VYORA!"
                )
            }
            assertTrue(sendRes.isSuccess)

            // Outsider cannot read messages
            val outsiderUid = signInTestUser("outsider_${UUID.randomUUID().toString().take(6)}@test.com")
            var caught: Throwable? = null
            try {
                withTimeout(FLOW_TIMEOUT_MS) {
                    chatRepo.observeMessages(convId, outsiderUid).first()
                }
            } catch (e: Throwable) {
                caught = e
            }
            val firestoreEx = checkNotNull(caught?.unwrapFirestoreException()) {
                "Expected PERMISSION_DENIED for outsider, got $caught"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
