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
import java.io.File
import java.io.FileInputStream
import java.lang.Math.*
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class User(val place: Int, val points: Int, val userUid: String)

@Serializable
data class EntEvent(
    var event_id: String,
    var type_of_event: String,
    var creator_id: String,
    var time_of_creation: String,
    var sport_type: String,
    var title: String,
    var coordinates: HashMap<String, Double>,
    var date_of_meeting: String,
    var max_people: Int,
    var age: String,
    var description: String,
    var photos: String,
    var reg_people_id: HashMap<String?, String?>,
    var amount_reg_people: Int,
    var status: String,
    var timestamp: Long
)

@Serializable
data class EcoEvent(
    var event_id: String,
    var type_of_event: String,
    var creator_id: String,
    var time_of_creation: String,
    var title: String,
    var coordinates: HashMap<String, Double>,
    var power_of_pollution: String,
    var date_of_meeting: String,
    var min_people: Int,
    var max_people: Int,
    var description: String,
    var photos: String,
    var reg_people_id: HashMap<String?, String?>,
    var amount_reg_people: Int,
    var get_points: Int,
    var status: String,
    var timestamp: Long
)

@Serializable
data class Points(
    var people: List<String>,
    var points: Int
)

data class GroupModel(
    val uid: String,
    val title: String,
    val imageUid: String,
    val members: HashMap<String, String>,
    val messages: ArrayList<String>
)

@Serializable
data class Request(val receiverUid: String, val senderUid: String)

fun main(args: Array<String>) {
    val serviceAccount = FileInputStream("src/main/resources/durable-path-406515-firebase-adminsdk-z8c0i-3242b9bf65.json")
    val options = FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
        .setDatabaseUrl("https://durable-path-406515-default-rtdb.firebaseio.com")
        .build()

    FirebaseApp.initializeApp(options)

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
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
            get("/") {
                call.respondText("Hello World!")
            }
        post("/create_ent_event") {
            val json = call.receiveText()
            val event = Json.decodeFromString<EntEvent>(json)

            val dbRef_user_your = FirebaseDatabase.getInstance().getReference("users/${event.creator_id}/yourCreatedEvents")
            val dbRef_user_groups = FirebaseDatabase.getInstance().getReference("users/${event.creator_id}/groups")
            val dbRef_user_events_amount = FirebaseDatabase.getInstance().getReference("users/${event.creator_id}/events_amount")

            val dbRef_group = FirebaseDatabase.getInstance().getReference("groups")

            val latitude = event.coordinates["latitude"]!!.toDouble()
            val longitude = event.coordinates["longitude"]!!.toDouble()
            val creatorUid = event.creator_id

            val dbRef_events = FirebaseDatabase.getInstance().getReference("current_events")
            val dbRef_user_cr = FirebaseDatabase.getInstance().getReference("users/${event.creator_id}/curRegEventsId")

            dbRef_events.child(event.event_id).setValue(event) {e, _ ->
                dbRef_user_cr.child(event.event_id).setValue(event.event_id) {e, _ ->
                    dbRef_user_your.child(event.event_id).setValue(event.event_id) {e, _ ->
                        val group = GroupModel(
                            event.event_id,
                            event.title,
                            event.photos,
                            hashMapOf(event.creator_id to event.creator_id),
                            arrayListOf()
                        )
                        dbRef_group.child(event.event_id).setValue(group) {e, _ ->
                            dbRef_user_groups.child(event.event_id).setValue(event.event_id) {e, _ ->
                                launch {call.respond(HttpStatusCode.OK, "event was created") }
                            }
                        }
                        dbRef_user_events_amount.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot?) {
                                dbRef_user_events_amount.setValue(snapshot?.value.toString().toInt() + 1) {e, _ ->

                                }
                            }

                            override fun onCancelled(error: DatabaseError?) {
                                TODO("Not yet implemented")
                            }

                        })
                    }
                }
            }

            val dbRef_user = FirebaseDatabase.getInstance().getReference("users")
            dbRef_user.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        dataSnapshot.children.forEach { userSnapshot ->
                            //if(userSnapshot.key != creatorUid) {
                                val locDb = FirebaseDatabase.getInstance().getReference("location/${userSnapshot.key}")

                                val userToken = userSnapshot.child("userToken").value.toString()

                                locDb.addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(locationSnapshot: DataSnapshot?) {
                                        locationSnapshot?.children?.forEach {
                                            val userLat =
                                                locationSnapshot?.child("latitude")?.value.toString().toDouble()
                                            val userLong =
                                                locationSnapshot.child("longitude").value.toString().toDouble()
                                            if (distanceBetweenCoordinates(
                                                    userLat,
                                                    userLong,
                                                    latitude,
                                                    longitude
                                                ) < 10.0
                                            ) {
                                                sendNotificationToUser(
                                                    userToken,
                                                    "Новое событие!",
                                                    "Рядом с вами создано новое событие!",
                                                    "new_event"
                                                )
                                            }
                                        }
                                    }

                                    override fun onCancelled(error: DatabaseError?) {
                                        TODO("Not yet implemented")
                                    }

                                })
                            //}
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
        post("/create_eco_event") {
            val json = call.receiveText()
            val event = Json.decodeFromString<EcoEvent>(json)

            val dbRef_user_your = FirebaseDatabase.getInstance().getReference("users/${event.creator_id}/yourCreatedEvents")
            val dbRef_user_groups = FirebaseDatabase.getInstance().getReference("users/${event.creator_id}/groups")
            val dbRef_user_events_amount = FirebaseDatabase.getInstance().getReference("users/${event.creator_id}/events_amount")

            val dbRef_group = FirebaseDatabase.getInstance().getReference("groups")

            val latitude = event.coordinates["latitude"]!!.toDouble()
            val longitude = event.coordinates["longitude"]!!.toDouble()

            val dbRef_events = FirebaseDatabase.getInstance().getReference("current_events")
            val dbRef_user_cr = FirebaseDatabase.getInstance().getReference("users/${event.creator_id}/curRegEventsId")

            dbRef_events.child(event.event_id).setValue(event) {e, _ ->
                dbRef_user_cr.child(event.event_id).setValue(event.event_id) {e, _ ->
                    dbRef_user_your.child(event.event_id).setValue(event.event_id) {e, _ ->
                        val group = GroupModel(
                            event.event_id,
                            event.title,
                            event.photos,
                            hashMapOf(event.creator_id to event.creator_id),
                            arrayListOf()
                        )
                        dbRef_group.child(event.event_id).setValue(group) {e, _ ->
                            dbRef_user_groups.child(event.event_id).setValue(event.event_id) {e, _ ->
                                launch {call.respond(HttpStatusCode.OK, "event was created") }
                            }
                        }
                        dbRef_user_events_amount.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot?) {
                                dbRef_user_events_amount.setValue(snapshot?.value.toString().toInt() + 1) {e, _ ->

                                }
                            }

                            override fun onCancelled(error: DatabaseError?) {
                                TODO("Not yet implemented")
                            }

                        })
                    }
                }
            }

            val dbRef_user = FirebaseDatabase.getInstance().getReference("users")
            dbRef_user.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        dataSnapshot.children.forEach { userSnapshot ->
                            //if(userSnapshot.key != creatorUid) {
                                val locDb = FirebaseDatabase.getInstance().getReference("location/${userSnapshot.key}")

                                val userToken = userSnapshot.child("userToken").value.toString()

                                locDb.addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(locationSnapshot: DataSnapshot?) {
                                        locationSnapshot?.children?.forEach {
                                            val userLat =
                                                locationSnapshot?.child("latitude")?.value.toString().toDouble()
                                            val userLong =
                                                locationSnapshot.child("longitude").value.toString().toDouble()
                                            if (distanceBetweenCoordinates(
                                                    userLat,
                                                    userLong,
                                                    latitude,
                                                    longitude
                                                ) < 10.0
                                            ) {
                                                sendNotificationToUser(
                                                    userToken,
                                                    "Новое событие!",
                                                    "Рядом с вами создано новое событие!",
                                                    "new_event"
                                                )
                                            }
                                        }
                                    }

                                    override fun onCancelled(error: DatabaseError?) {
                                        TODO("Not yet implemented")
                                    }

                                })
                            }
                        //}
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
        post("/query_to_friend") {
            val json = call.receiveText()
            val request = Json.decodeFromString<Request>(json)

            val sender = request.senderUid
            val receiver = request.receiverUid

            call.respondText(sender)

            val dbRef_user = FirebaseDatabase.getInstance().getReference("users")

            dbRef_user.child(receiver).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot?) {
                    dbRef_user.child(sender).child("firstAndLastName").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnapshot: DataSnapshot?) {
                            val receiverToken = snapshot?.child("userToken")?.value.toString()
                            val senderName = userSnapshot?.value.toString()
                            sendNotificationToUser(receiverToken, "Новая заявка в друзья!", "Вам пришла заявка от $senderName", "friendship_request")
                            //call.respondText(HttpStatusCode.OK, "Notification sent")
                        }

                        override fun onCancelled(error: DatabaseError?) {
                            //call.respond(HttpStatusCode.InternalServerError, "Failed to read sender's name")
                        }
                    })
                }

                override fun onCancelled(error: DatabaseError?) {
                    //call.respond(HttpStatusCode.InternalServerError, "Failed to read receiver's token")
                }
            })
        }
        post("/accept_query_to_friend") {
            val json = call.receiveText()
            val request = Json.decodeFromString<Request>(json)

            val sender = request.senderUid
            val receiver = request.receiverUid

            call.respondText(sender)

            val dbRef_user = FirebaseDatabase.getInstance().getReference("users")

            dbRef_user.child(sender).child("userToken").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot?) {
                    dbRef_user.child(receiver).child("firstAndLastName").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(userSnapshot: DataSnapshot?) {
                            val senderToken = snapshot?.value.toString()
                            val receiverName = userSnapshot?.value.toString()
                            sendNotificationToUser(senderToken, "Заявка в друзья принята!", "$receiverName принял вашу заявку в друзья", "accept_friendship")
                            //call.respondText(HttpStatusCode.OK, "Notification sent")
                        }

                        override fun onCancelled(error: DatabaseError?) {
                            //call.respond(HttpStatusCode.InternalServerError, "Failed to read sender's name")
                        }
                    })
                }

                override fun onCancelled(error: DatabaseError?) {
                    //call.respond(HttpStatusCode.InternalServerError, "Failed to read receiver's token")
                }
            })
        }
        post("/get_points") {
            val json = call.receiveText()
            val points = Json.decodeFromString<Points>(json)

            val selectedUsers = points.people
            val pointsInt = points.points
            val database = FirebaseDatabase.getInstance()
            val dbRefUsers = database.getReference("users")
            val dbRefRating = database.getReference("rating")

            selectedUsers.forEach { userUid ->
                val userPointsRef = dbRefUsers.child(userUid)
                userPointsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val currentPoints = snapshot.child("points").getValue(Int::class.java) ?: 0
                        val newPoints = currentPoints + pointsInt
                        userPointsRef.child("points").setValue(newPoints) {e, _ ->
                            sendNotificationToUser(snapshot.child("userToken").value.toString(), "Вам начислены баллы", "Вам начислено $pointsInt баллов. Общие баллы: $newPoints", "points")
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        print("Failed points $userUid: ${error.message}")
                    }
                })
            }
            dbRefRating.addListenerForSingleValueEvent(object: ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.children.forEach { it ->
                        val uid = it.child("userUid").value.toString()
                        if(selectedUsers.contains(uid)) {
                            val curPoints = it.child("points").value.toString().toInt()
                            dbRefRating.child(it.key).child("points").setValue(curPoints + pointsInt) { e, _ ->

                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    TODO("Not yet implemented")
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
fun sendNotificationToUser(deviceToken: String, title: String, message: String, type: String) {
    val message = Message.builder()
        .putData("title", title)
        .putData("message", message)
        .putData("type", type)
        .setToken(deviceToken)
        .build()

    val response = FirebaseMessaging.getInstance().sendAsync(message).get()
    println("Sent message: $response")
}

suspend fun deleteOldEvents() {

    val dbEvent = FirebaseDatabase.getInstance().getReference("current_events")
    val dbGroups = FirebaseDatabase.getInstance().getReference("groups")
    val reports = FirebaseDatabase.getInstance().getReference("reports")

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
                        reports.child(eventSnapshot.key).ref.removeValue() { err, _ ->
                            continuation.resume(Unit)
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
