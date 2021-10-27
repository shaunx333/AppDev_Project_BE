package org.streamx.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.Binder
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
        if (roomId == "")
            return

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
                            depUtils.setGroupId("")
                            depUtils.miniPrefs.currentGroupId().put("")
                        }
                } else
                    logit("Room not exist: $roomId")
            }
    }

    fun removeUserIfPresent(roomId: String) {
        logit(
            "myser Trying to remove user ${
                depUtils.miniPrefs.currentUserId().get()
            } from ${depUtils.miniPrefs.currentGroupId().get()}"
        )
        if (roomId.isBlank() && depUtils.miniPrefs.currentUserId().get().isNullOrBlank()
        ) return
        else if (depUtils.miniPrefs.currentUserId().get() == roomId)
            removeRoom(roomId)
        else {
            removeUserFromRoom(
                userId = depUtils.miniPrefs.currentUserId().get(),
                roomId = roomId
            )
        }
    }

    private fun removeUserFromRoom(roomId: String, userId: String) {
        if (roomId.isBlank() || userId.isBlank())
            return
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
                                depUtils.setGroupId("")
                                ee["users"] = depUtils.usersliveViewModel.liveListUsers.value!!
                                Firebase.database
                                    .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
                                    .child(roomId)
                                    .setValue(ee)
                            } else {
                                depUtils.miniPrefs.currentGroupId().put("")
                            }
                        }
                } else {
                    depUtils.miniPrefs.currentGroupId().put("")
                    //this quickToast "User doesn't exist"
                }
            }
    }

    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(this)
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            startMyOwnForeground()
        } else {
            startForeground(101, Notification())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    open fun startMyOwnForeground() {
        val chan = NotificationChannel(
            "101",
            javaClass.simpleName,
            NotificationManager.IMPORTANCE_NONE
        )
        chan.lightColor = Color.WHITE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        notifManager.createNotificationChannel(chan)
        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, "101")
        val notification: Notification = notificationBuilder.setOngoing(true)
            .setContentTitle("App is running in background")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(101, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logit("Trying to do stuff")
        /*var i = 0
        Timer().schedule(
            object : TimerTask() {
                override fun run() {
                    logit(++i)
                }

            }, 0, 1000
        )*/
        //removeUserIfPresent()
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    class LocalBinder : Binder() {
        fun getServic() = this
    }
}