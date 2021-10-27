package org.streamx.activities

import android.app.ProgressDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.media.MediaCodec
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Display
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelector
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.util.Util
import com.google.android.material.appbar.AppBarLayout
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.dynamiclinks.ktx.dynamicLink
import com.google.firebase.dynamiclinks.ktx.dynamicLinks
import com.google.firebase.ktx.Firebase
import org.androidannotations.annotations.Bean
import org.androidannotations.annotations.EActivity
import org.streamx.IRemoteService
import org.streamx.MyService_
import org.streamx.R
import org.streamx.base.BaseActivity
import org.streamx.databinding.ActivityPostMainBinding
import org.streamx.di.DepUtils
import org.streamx.utils.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap
import kotlin.math.abs
import kotlin.math.max


@EActivity
open class PostMainActivity :
    BaseActivity<ActivityPostMainBinding>(ActivityPostMainBinding::inflate) {

    private var timer: Timer? = null
    private val VIDEO_PICKER_REQ_CODE = 111

    private var mPlayer: SimpleExoPlayer? = null

    var width: Int = 0
    var height: Int = 0
    var iRemoteService: IRemoteService? = null

    val mConnection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            iRemoteService = IRemoteService.Stub.asInterface(p1)
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
        }

    }

    @Bean
    lateinit var depUtils: DepUtils

    private fun generateDynamicLinkWithRoomId(roomId: String) {
        val dynamicLink = Firebase.dynamicLinks.dynamicLink {
            val rootUrl = getString(R.string.dynamic_url)
            link = Uri.parse("https://$rootUrl/$roomId")
            domainUriPrefix = "https://$rootUrl"
        }

        setClipboard(dynamicLink.uri.toString())
        this quickToast "Copied room url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        logit("onCreate")
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        val inten = Intent(this, MyService_::class.java)

        if (!isMyServiceRunning(MyService_::class.java)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(inten)
            } else
                startService(inten)
        }
        bindService(inten, mConnection, Context.BIND_ADJUST_WITH_ACTIVITY)

        var widthImage = 0
        depUtils.usersliveViewModel.liveListUsers.observe(this) {
            logit(it.size)
        }
        binding.root.post {
            getScreenSize()
            widthImage = binding.profileImg.width
        }
        binding.playerView.setShutterBackgroundColor(Color.TRANSPARENT)
        binding.playerView.setKeepContentOnPlayerReset(true)

        binding.appbar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, i ->
            val abs = abs(i).toFloat() / appBarLayout.totalScrollRange
            val f2: Float = 1.0f - abs /* 1 to 0 */
            binding.profileImg.translationY = widthImage.div(2).plus(16.toPx) * abs
            binding.profileImg.translationX = (width.div(2) - widthImage.div(2) + 24.toPx) * abs
            binding.profileImg.scaleX = max(0.3f, f2)
            binding.profileImg.scaleY = max(0.3f, f2)
        })
        Firebase.auth.currentUser!!.run {
            binding.toolbar.title = displayName

            Glide.with(this@PostMainActivity)
                .load(photoUrl.toString()).thumbnail(.2f).placeholder(R.drawable.placeholder)
                .into(binding.profileImg)
        }

        logit(Firebase.auth.currentUser?.photoUrl)

        initWhenUrlClicked(intent)
        observeThisRoomId(depUtils.miniPrefs.currentGroupId().get())
    }

    private fun createVideoChooser() {
        val intent = Intent()
        intent.type = "video/mp4"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(
            Intent.createChooser(intent, "Select video to play"),
            VIDEO_PICKER_REQ_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VIDEO_PICKER_REQ_CODE) {
            data?.data?.let {
                setupExoplayer(it)
            }
        }
    }

    private fun setupExoplayer(link: Uri) {
        val options = RequestOptions().frame(1000L)
        Glide.with(baseContext).load(link).apply(options).thumbnail(0.03f).into(binding.prev)

        val trackSelector: TrackSelector =
            DefaultTrackSelector(this, AdaptiveTrackSelection.Factory())
        val bandwidthMeter: DefaultBandwidthMeter =
            DefaultBandwidthMeter.Builder(this).setResetOnNetworkTypeChange(true)
                .setInitialBitrateEstimate(Util.getUserAgent(this, this.packageName))
                .build()

        mPlayer = SimpleExoPlayer.Builder(this).setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setUseLazyPreparation(true)
            .setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .build()
        mPlayer?.setMediaItem(MediaItem.fromUri(link))

        mPlayer?.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    logit(isPlaying)
                    updatePlaying(depUtils.miniPrefs.currentGroupId().get(), isPlaying)
                    if (isPlaying) {
                        timer = Timer()
                        timer?.schedule(
                            object : TimerTask() {
                                override fun run() {
                                    runOnUiThread {
                                        updateTimeDuration(
                                            depUtils.miniPrefs.currentGroupId().get(),
                                            TimeUnit.MILLISECONDS.toSeconds(mPlayer!!.currentPosition)
                                        )
                                    }
                                }
                            }, 0, 1000
                        )
                    } else
                        timer?.cancel()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    super.onPlaybackStateChanged(playbackState)
                    logit("${TimeUnit.MILLISECONDS.toSeconds(mPlayer!!.currentPosition)}")
                }

                override fun onPlayerError(error: PlaybackException) {
                    super.onPlayerError(error)
                    error.printStackTrace()
                    this@PostMainActivity quickToast error.message
                }
            })

        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_MOVIE)
            .build()
        mPlayer?.setAudioAttributes(audioAttributes, true)
        mPlayer?.prepare()

        mPlayer?.playWhenReady = false
        binding.playerView.player = mPlayer
    }

    override fun onBackPressed() {
        if (!depUtils.miniPrefs.currentGroupId().get().isNullOrBlank()) {
            AlertDialog.Builder(
                this,
                R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog
            )
                .run {
                    if (depUtils.miniPrefs.currentGroupId().get()
                            .isNotBlank() && depUtils.miniPrefs.currentGroupId()
                            .get() == depUtils.miniPrefs.currentUserId().get()
                    ) {
                        setMessage("Exit?\n${depUtils.usersliveViewModel.liveListUsers.value!!.size} members will be removed")
                    } else {
                        setMessage("Leave room?")
                    }
                    setNeutralButton("Cancel", null)
                    setPositiveButton("Ok") { v, _ ->
                        v.dismiss()
                        super.onBackPressed()
                    }
                    if (!isDestroyed)
                        show()
                }
        } else {
            super.onBackPressed()
        }
    }

    fun observeThisRoomId(roomId: String) {
        if (roomId == "")
            return

        logit("Observing $roomId")
        depUtils.miniPrefs.currentGroupId().put(roomId)
        Firebase.database.getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
            .child(roomId)
            .child("users")
            .addValueEventListener(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.value == null) {
                            //depUtils.usersliveViewModel.liveListUsers.value!!.clear()
                        } else {
                            logit(snapshot.value)
                            try {
                                if ((snapshot.value as ArrayList<String>) != null) {
                                    depUtils.usersliveViewModel.liveListUsers.value == snapshot.value as ArrayList<String>
                                } else
                                    depUtils.usersliveViewModel.liveListUsers.value!!.clear()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                this@PostMainActivity quickToast e.localizedMessage
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        this@PostMainActivity quickToast error.message
                    }
                })
        observeTimer(roomId)
        observePlaying(roomId)
    }

    fun updatePlaying(roomId: String, isPlaying: Boolean) {
        if (roomId == "" || roomId != depUtils.miniPrefs.currentUserId().get())
            return

        depUtils.usersliveViewModel.liveVideoPlaying.value = isPlaying
        Firebase.database
            .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
            .child(roomId)
            .child("playing")
            .setValue(isPlaying)
    }

    override fun onStart() {
        super.onStart()
        mPlayer?.play()
    }

    override fun onStop() {
        super.onStop()
        mPlayer?.pause()
    }

    fun updateTimeDuration(roomId: String, duration: Long = 0) {
        if (roomId == "" || roomId != depUtils.miniPrefs.currentUserId().get())
            return
        Firebase.database
            .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
            .child(roomId)
            .child("time")
            .setValue(duration.plus(1))
    }

    override fun onDestroy() {
        mPlayer?.release()
        logit(iRemoteService)
        iRemoteService?.removeUser(depUtils.miniPrefs.currentGroupId().get())
        //removeUserIfPresent()
        super.onDestroy()
    }

    fun observeTimer(roomId: String) {
        if (roomId.isBlank())
            return
        Firebase.database.getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
            .child(roomId).child("time").addValueEventListener(object :
                ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    logit(snapshot.value)
                    if (snapshot.value != null)
                        depUtils.usersliveViewModel.liveVideoDuration.value = snapshot.value as Long
                    else
                        depUtils.miniPrefs.currentGroupId().put("")
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }

    fun observePlaying(roomId: String) {
        if (roomId.isBlank())
            return
        Firebase.database.getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
            .child(roomId).child("playing").addValueEventListener(object :
                ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    logit(snapshot.value)
                    if (snapshot.value != null)
                        depUtils.usersliveViewModel.liveVideoPlaying.value =
                            snapshot.value as Boolean
                    else
                    depUtils.miniPrefs.currentGroupId().put("")
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }

    fun createRoom(userId: String, pd: ProgressDialog) {
        Firebase.database
            .getReference(StreamxConstants.ReferenceKeys.KEY_ALL_USERS)
            .child("users")
            .orderByKey()
            .equalTo(userId)
            .get()
            .addOnSuccessListener {
                if (it.value != null) {

                    Firebase.database
                        .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
                        .orderByKey()
                        .equalTo(userId)
                        .get()
                        .addOnSuccessListener {
                            if (it.value == null) {
                                Firebase.database
                                    .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
                                    .child(userId)
                                    .get()
                                    .addOnSuccessListener {
                                        var m = HashMap<Any, Any>()
                                        m["playing"] = false
                                        m["time"] = 0L

                                        // No room active with this ID create it
                                        if (it.value != null) {
                                            m = it.value as HashMap<Any, Any>
                                        }
                                        depUtils.setGroupId(userId)
                                        depUtils.miniPrefs.currentGroupId().put(userId)

                                        Firebase.database
                                            .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
                                            .child(userId)
                                            .setValue(m)
                                            .addOnCompleteListener {
                                                pd.dismiss()
                                                this quickToast if (it.isSuccessful) "Room created" else it.exception?.localizedMessage
                                                m.clear()
                                            }
                                    }

                            } else {
                                pd.dismiss()
                                generateDynamicLinkWithRoomId(userId)
                            }
                        }
                } else {
                    pd.dismiss()
                    this quickToast "Invalid room id"
                }
            }
            .addOnFailureListener {
                pd.dismiss()
                this quickToast it.localizedMessage
            }
    }

    fun pushUserToRoom(roomId: String, userId: String) {
        val pd = ProgressDialog(
            this,
            R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog
        ).apply {
            setCancelable(false)
            setMessage("Verifying link")
            show()
        }

        Firebase.database
            .getReference(StreamxConstants.ReferenceKeys.KEY_ALL_USERS)
            .child("users")
            .orderByKey()
            .equalTo(roomId)
            .get()
            .addOnSuccessListener {
                observeThisRoomId(roomId)
                logit(depUtils.usersliveViewModel.liveListUsers.value!!)
                logit(it.value)
                depUtils.setGroupId(roomId)
                depUtils.miniPrefs.currentGroupId().put(roomId)
                if (it.value == null) {
                    this quickToast "Invalid room id"
                    pd.dismiss()
                } else if (roomId == userId) {
                    createRoom(userId, pd)
                    this quickToast "Room not active, creating one"
                    //pd.dismiss()
                } else if (depUtils.usersliveViewModel.liveListUsers.value!!.contains(userId)) {
                    logit("User exist")
                    this quickToast "Already present in room"
                    pd.dismiss()
                } else {
                    Firebase.database
                        .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
                        .orderByKey()
                        .equalTo(roomId)
                        .get()
                        .addOnSuccessListener {
                            if (it.value == null) {
                                if (roomId == userId) {
                                    this quickToast "Room not active, creating one"
                                    createRoom(roomId, pd)
                                } else {
                                    this quickToast "Room not active anymore"
                                    pd.dismiss()
                                }
                            } else {
                                //depUtils.miniPrefs.isUserPresenting.put(false)
                                logit("Adding user=${userId} into room=${roomId}")

                                val roomDetails: HashMap<String, String> =
                                    it.child(roomId).value as HashMap<String, String>
                                this quickToast "Joining room of ${roomDetails["name"]}"

                                depUtils.usersliveViewModel.liveListUsers.value!!.add(userId)
                                Firebase.database
                                    .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
                                    .child(roomId)
                                    .child("users")
                                    .setValue(depUtils.usersliveViewModel.liveListUsers.value!!)
                                    .addOnCompleteListener {
                                        if (!it.isSuccessful) {
                                            this quickToast it.exception?.localizedMessage
                                        } else {
                                            createVideoChooser()
                                        }
                                        pd.dismiss()
                                    }
                            }
                            logit(it.value)
                        }
                }
            }.addOnFailureListener {
                this quickToast it.localizedMessage
                pd.dismiss()
            }
    }

    fun initWhenUrlClicked(intent: Intent?) {
        intent?.data?.let {
            if (it.host == getString(R.string.dynamic_url) && depUtils.miniPrefs.currentUserId()
                    .get() != ""
            ) {
                it.path?.let {
                    if (!it.contains("/"))
                        return
                    pushUserToRoom(
                        roomId = it.substringAfter("/"),
                        userId = depUtils.miniPrefs.currentUserId().get()
                    )
                    intent.data = null
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        initWhenUrlClicked(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        getScreenSize()
    }

    private fun getScreenSize() {
        val display: Display = windowManager.defaultDisplay
        val size = Point()

        display.getSize(size)
        width = size.x
        height = size.y
    }
}