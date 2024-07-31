package com.example

import com.example.plugins.*
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.gson.annotations.SerializedName
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.lang.Math.*
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Serializable
data class Request(val receiverToken: String, val senderName: String)

fun main() {
    initializeFirebaseApp()

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun initializeFirebaseApp() {
    try {
        val serviceAccount = FileInputStream("")

        val options = FirebaseOptions.Builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .setDatabaseUrl("")
            .build()

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options)
            println("FirebaseApp initialized successfully")
        }
    } catch (e: Exception) {
        println("Error initializing FirebaseApp: ${e.message}")
    }
}

fun Application.module() {
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }

    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        post("/query_to_friend") {
            val json = call.receiveText()
            val request = runBlocking { Json.decodeFromString<Request>(json) }

            val sender = request.senderName
            val receiverToken = request.receiverToken

            sendNotificationToUser(
                receiverToken,
                "Новая заявка в друзья!",
                "Вам пришла заявка от $sender",
                "friendship_request"
            )

            call.respond("Notification sent")
        }
    }
}

fun sendNotificationToUser(deviceToken: String, title: String, message: String, type: String) {
    val message = com.google.firebase.messaging.Message.builder()
        .putData("title", title)
        .putData("message", message)
        .putData("type", type)
        .setToken(deviceToken)
        .build()

    try {
        val response = FirebaseMessaging.getInstance().sendAsync(message).get()
        println("Sent message: $response")
    } catch (e: Exception) {
        println("Error sending message: ${e.message}")
    }
}
