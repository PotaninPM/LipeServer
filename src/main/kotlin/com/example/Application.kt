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
data class Event(val latitude: String, val longitude: String, val creatorUid: String)

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
            val creatorUid = event.creatorUid

            val dbRef_user = FirebaseDatabase.getInstance().getReference("users")
            dbRef_user.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        dataSnapshot.children.forEach { userSnapshot ->
                            val locDb = FirebaseDatabase.getInstance().getReference("location/$creatorUid")

                            val userToken = userSnapshot.child("userToken").value.toString()

                            locDb.addListenerForSingleValueEvent(object: ValueEventListener {
                                override fun onDataChange(locationSnapshot: DataSnapshot?) {
                                    locationSnapshot?.children?.forEach {
                                        val userLat = locationSnapshot?.child("latitude")?.value.toString().toDouble()
                                        val userLong = locationSnapshot.child("longitude").value.toString().toDouble()
                                        if(distanceBetweenCoordinates(userLat, userLong, latitude, longitude) < 2.0) {
                                            sendNotificationToUser(
                                                userToken,
                                                "Новое событие!",
                                                "Рядом с вами создано новое событие!"
                                            )
                                        }
                                    }
                                }

                                override fun onCancelled(error: DatabaseError?) {
                                    TODO("Not yet implemented")
                                }

                            })
                        }
                        //call.respond(HttpStatusCode.OK, "Notifications sent")
                    } else {
                        //call.respond(HttpStatusCode.BadRequest, "No users found")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    //call.respond(HttpStatusCode.InternalServerError, "Failed to read users: ${error.toException()}")
                }
            })
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
    println(1)
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
