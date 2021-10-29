package org.streamx.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize
import org.androidannotations.annotations.Bean
import org.androidannotations.annotations.EService
import org.androidannotations.annotations.SystemService
import org.streamx.IRemoteService
import org.streamx.di.DepUtils
import org.streamx.utils.StreamxConstants
import org.streamx.utils.logit
import org.streamx.utils.quickToast
import java.util.*

@EService
open class MyService : Service() {
    @SystemService
    lateinit var notifManager: NotificationManager
    private val binder = object : IRemoteService.Stub() {
        override fun removeUser(roomId: String) {

            /*logit(
                "myser g: ${
                    depUtils.miniPrefs.currentGroupId().get()
                } u:${depUtils.miniPrefs.currentUserId().get()} temp: ${depUtils.getGroupId()} " +
                        "temp2: ${getSharedPreferences("org.MyApp_MiniPrefs", MODE_PRIVATE).all}")
            logit("myser grp: ${depUtils.miniPrefs.currentGroupId().get()} temp: ${depUtils.getGroupId()}")
            logit("Called")*/
            removeUserIfPresent(roomId)
        }
    }

    @Bean
    lateinit var depUtils: DepUtils

    private fun removeRoom(roomId: String) {
        if (roomId == "") {
            //stopForeground(true)
            return
        }

        Firebase.database
            .getReference(StreamxConstants.ReferenceKeys.KEY_ALL_USERS)
            .child("users")
            .orderByKey()
            .equalTo(roomId)
            .get()
            .addOnSuccessListener {
                if (it.value != null) {
                    Firebase.database.getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
                        .child(roomId)
                        .removeValue()
                        .addOnSuccessListener {
                            depUtils.usersliveViewModel.liveRoomId.value = ""
                            depUtils.usersliveViewModel.liveVideoFile.value = ""

                            depUtils.miniPrefs.currentGroupId().put("")
                            //stopForeground(true)
                        }.addOnFailureListener {
                            //stopForeground(true)
                        }
                } else {
                    logit("Room not exist: $roomId")
                    //stopForeground(true)
                }
            }
    }

    fun removeUserIfPresent(roomId: String) {
        logit(
            "myser Trying to remove user ${
                depUtils.miniPrefs.currentUserId().get()
            } from ${depUtils.miniPrefs.currentGroupId().get()} new:$roomId"
        )
        if (roomId.isBlank() && depUtils.miniPrefs.currentUserId().get().isNullOrBlank()
        ) {
            //stopForeground(true)
            return
        } else if (depUtils.miniPrefs.currentUserId().get() == roomId)
            removeRoom(roomId)
        else {
            removeUserFromRoom(
                userId = depUtils.miniPrefs.currentUserId().get(),
                roomId = roomId
            )
        }
    }

    private fun removeUserFromRoom(roomId: String, userId: String) {
        if (roomId.isBlank() || userId.isBlank()) {
            //stopForeground(true)
            return
        }
        Firebase.database
            .getReference(StreamxConstants.ReferenceKeys.KEY_ALL_USERS)
            .child("users")
            .orderByKey()
            .equalTo(roomId)
            .get()
            .addOnSuccessListener {
                if (it.value != null) {
                    depUtils.usersliveViewModel.liveListUsers.value!!.remove(userId)
                    Firebase.database.getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
                        .child(roomId)
                        .child("users")
                        .setValue(depUtils.usersliveViewModel.liveListUsers.value!!)
                    Firebase.database.getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
                        .child(roomId)
                        .get().addOnSuccessListener {
                            val ee = it.value as HashMap<Any, Any>
                            if (ee["users"] != null) {
                                depUtils.usersliveViewModel.liveListUsers.value =
                                    ee["users"] as ArrayList<String>
                                depUtils.usersliveViewModel.liveListUsers.value!!.remove(userId)
                                depUtils.miniPrefs.currentGroupId().put("")

                                depUtils.usersliveViewModel.liveRoomId.postValue("")
                                ee["users"] = depUtils.usersliveViewModel.liveListUsers.value!!
                                Firebase.database
                                    .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
                                    .child(roomId)
                                    .setValue(ee)
                                    .addOnCompleteListener {
                                        //stopForeground(true)
                                    }
                            } else {
                                depUtils.usersliveViewModel.liveRoomId.postValue("")
                                depUtils.miniPrefs.currentGroupId().put("")
                                //stopForeground(true)
                            }
                        }.addOnFailureListener {
                            //stopForeground(true)
                        }
                } else {
                    depUtils.miniPrefs.currentGroupId().put("")
                    depUtils.usersliveViewModel.liveRoomId.postValue("")
                    this quickToast "User doesn't exist"
                    //stopForeground(true)
                }
            }.addOnFailureListener {
                //stopForeground(true)
            }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    open fun startMyOwnForeground() {
        val chan = NotificationChannel(
            "101",
            "Background Service",
            NotificationManager.IMPORTANCE_NONE
        )
        chan.lightColor = Color.WHITE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        notifManager.createNotificationChannel(chan)
        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, "101")
        val notification: Notification = notificationBuilder
            .setOngoing(true)
            .setSilent(true)
            .setContentTitle("App is running in background")
            .setPriority(NotificationManager.IMPORTANCE_NONE)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(101, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        removeUserIfPresent(depUtils.miniPrefs.currentGroupId().get())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Firebase.initialize(this)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            startMyOwnForeground()
        } else {
            startForeground(101, Notification())
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }
}