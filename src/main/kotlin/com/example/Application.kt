package com.example

import com.example.plugins.*
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class User(val place: Int, val points: Int, val userUid: String)

fun main(args: Array<String>) {
    val serviceAccount = FileInputStream("src/main/resources/durable-path-406515-firebase-adminsdk-z8c0i-808a95da6f.json")
    val options = FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
        .setDatabaseUrl("https://durable-path-406515-default-rtdb.firebaseio.com")
        .build()
    FirebaseApp.initializeApp(options)

    embeddedServer(Netty, port = 8080, host = "localhost", module = Application::module).start(wait = true)
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
