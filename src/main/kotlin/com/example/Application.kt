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
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.FileInputStream
import java.lang.Math.*
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class User(val place: Int, val points: Int, val userUid: String)

@Serializable
data class Event(val latitude: String, val longitude: String, val creatoUid: String)

fun main(args: Array<String>) {
    val serviceAccount = FileInputStream("src/main/resources/durable-path-406515-firebase-adminsdk-z8c0i-808a95da6f.json")
    val options = FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
        .setDatabaseUrl("https://durable-path-406515-default-rtdb.firebaseio.com")
        .build()
    FirebaseApp.initializeApp(options)

    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    launch {
        val db = FirebaseDatabase.getInstance().getReference("rating")
        db.addValueEventListener(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot?) {
                db.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        if (dataSnapshot.exists()) {
                            val usersList = mutableListOf<User>()
                            dataSnapshot.children.forEach { userSnapshot ->
                                val user = User(userSnapshot.child("place").value.toString().toInt(), userSnapshot.child("points").value.toString().toInt(), userSnapshot.child("userUid").value.toString())
                                usersList.add(user)
                            }

                            val sortedUsers = usersList.sortedByDescending { it.points }
                            sortedUsers.forEachIndexed { index, user ->
                                val userUid = dataSnapshot.children.elementAtOrNull(index)?.key
                                if (userUid != null) {
                                    db.child(userUid).child("place").setValue(index + 1) { databaseError, _ ->
                                        if (databaseError != null) {
                                            //println("${databaseError.message}")
                                        } else {
                                            //println("Data saved successfully")
                                        }
                                    }
                                    db.child(userUid).child("points").setValue(user.points) { databaseError, _ ->
                                        if (databaseError != null) {
                                            //println("${databaseError.message}")
                                        } else {
                                            //println("Data saved successfully")
                                        }
                                    }
                                    db.child(userUid).child("userUid").setValue(user.userUid) { databaseError, _ ->
                                        if (databaseError != null) {
                                            //println("${databaseError.message}")
                                        } else {
                                            //println("Data saved successfully")
                                        }
                                    }
                                }
                            }
                        } else {
                            println("No data found")
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        println("Failed to read value: ${error.toException()}")
                    }
                })
            }

            override fun onCancelled(error: DatabaseError?) {
                TODO("Not yet implemented")
            }

        })
    }
    launch {
        while(true) {
            deleteOldEvents()
            delay(1000 * 3600 * 60 * 24)
        }
    }

    install(Routing) {
        post("/events") {
            val json = call.receiveText()
            val event = Json.decodeFromString<Event>(json)

            val latitude = event.latitude.toDouble()
            val longitude = event.longitude.toDouble()
            val createUid = event.creatoUid
//            val longitude = event.longitude
//            val creatorUid = event.creatorUid
            //Рядом с вами создано новое событие!

            //curl -X POST -H "Content-Type: application/json" -d "{\"latitude\":\"55.80095081074492\", \"longitude\":\"37.61738169234994\", \"creatoUid\":\"yourUidHere\"}" http://localhost:8080/events
            val dbRef_user = FirebaseDatabase.getInstance().getReference("users")
            dbRef_user.addListenerForSingleValueEvent(object: ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot?) {

                }

                override fun onCancelled(error: DatabaseError?) {
                    TODO("Not yet implemented")
                }

            })
            sendNotificationToUser("dE-hvrX_Sjurt3Bvu3utjl:APA91bHAnOvZ18poz40Lllo_45UguYFGyhNedkxKyJX9I409PQSllK8yOQrE-yXOF828eKYTrdmmvxDerQHzhdI-4_B4_BbRwY2B_6d1R9JWN2W9RteqBtpPm1W4WdAGi1p2E-iKW_I0", "$createUid", "Проснись и пой")
            call.respond(HttpStatusCode.OK, "Notification sent")
        }
    }


}
fun distanceBetweenCoordinates(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371 // in km

    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)

    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}
fun sendNotificationToUser(deviceToken: String, title: String, message: String) {
    val message = Message.builder()
        .putData("title", title)
        .putData("message", message)
        .setToken(deviceToken)
        .build()

    val response = FirebaseMessaging.getInstance().sendAsync(message).get()
    println("Sent message: $response")
}

suspend fun deleteOldEvents() {

    val dbEvent = FirebaseDatabase.getInstance().getReference("current_events")
    val dbGroups = FirebaseDatabase.getInstance().getReference("groups")

    val currentTime = Instant.now().epochSecond

    suspendCoroutine<Unit> { continuation ->
        dbEvent.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                dataSnapshot.children.forEach { eventSnapshot ->
                    val timestamp = eventSnapshot.child("timestamp").value as Long
                    if (currentTime - timestamp > 5 * 24 * 60 * 60) {
                        eventSnapshot.ref.removeValue() { error, _ ->
                            dbGroups.child(eventSnapshot.key).ref.removeValue() { err, _ ->
                                continuation.resume(Unit)
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                continuation.resumeWithException(error.toException())
            }
        })
    }
}
