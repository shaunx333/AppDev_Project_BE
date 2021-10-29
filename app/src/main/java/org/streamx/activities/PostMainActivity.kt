package org.streamx.activities

import android.animation.ObjectAnimator
import android.app.ProgressDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.MediaCodec
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.animation.doOnStart
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.palette.graphics.Palette
import androidx.transition.TransitionManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.util.Util
import com.google.android.material.appbar.AppBarLayout
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import org.androidannotations.annotations.Bean
import org.androidannotations.annotations.EActivity
import org.streamx.IRemoteService
import org.streamx.R
import org.streamx.base.BaseActivity
import org.streamx.databinding.ActivityPostMainBinding
import org.streamx.databinding.DialogInputLinkBinding
import org.streamx.di.DepUtils
import org.streamx.services.MyService_
import org.streamx.utils.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.abs
import kotlin.math.max


@EActivity
open class PostMainActivity :
    BaseActivity<ActivityPostMainBinding>(ActivityPostMainBinding::inflate) {

    private var bg: Int = 0
    private var timer: Timer? = null

    private var mPlayer: SimpleExoPlayer? = null

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    private var iRemoteService: IRemoteService? = null
    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            iRemoteService = IRemoteService.Stub.asInterface(p1)
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
        }

    }

    @Bean
    lateinit var depUtils: DepUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        serviceInit()
        super.onCreate(savedInstanceState)

        initViews()
        initWhenUrlClicked(intent)
        addButtonListeners()
        checkIfOldRoomExist()
    }

    private fun initViews() {
        binding.infoLabelShimmer.hideShimmer()
        binding.toolbar.setCollapsedTitleTextColor(Color.WHITE)
        binding.toolbar.setExpandedTitleColor(Color.WHITE)
        binding.appbar.setBackgroundColor(getColor(R.color.black))

        var widthImage = 0

        depUtils.usersliveViewModel.liveListUsers.observe(this) {
            if (it == null){
                return@observe
            }
            if (depUtils.isUserAdmin())
                resetInfoLabel("Users joined ${it.size}")
            logit("liveListUsers ${it.size}")
        }
        depUtils.usersliveViewModel.liveVideoPlaying.observe(this) {
            if (it)
                mPlayer?.play()
            else
                mPlayer?.pause()
            logit("liveVideoPlaying $it")
        }
        depUtils.usersliveViewModel.liveVideoDuration.observe(this) { dur ->
            mPlayer?.let {
                //it.seekTo(TimeUnit.SECONDS.toMillis(dur))
                val offset = dur - TimeUnit.MILLISECONDS.toSeconds(it.currentPosition)
                /* Maintain 2s delay*/
                if (abs(offset) > 1) {
                    it.seekTo(TimeUnit.SECONDS.toMillis(dur))
                }
            }
            logit("liveVideoDuration $dur")
        }
        depUtils.usersliveViewModel.liveVideoFile.observe(this) {
            if (depUtils.getRoomIdFromPref().isBlank())
                return@observe

            logit("liveVideoFile $it")

            if (!depUtils.isUserAdmin())
                resetInfoLabel("Waiting for\nAdmin to start!")
            else {
                resetInfoLabel("Play a video!")
            }
            animateToVideoView(hidePlayBtn = false)
            //generateDynamicLinkWithRoomId(depUtils.getRoomIdFromPref())

            //binding.shareBtn.isVisible = true
            //binding.exitBtn.isVisible = true

            if (it.isBlank())
                mPlayer?.run {
                    binding.vidCard.visibility = View.INVISIBLE
                    getColor(R.color.black).animateColorsOfWindow(true)
                    setVideoSurface(null)
                    clearMediaItems()
                    stop()
                    timer?.cancel()
                    release()
                }
            setupExoplayer(it.toUri())
            decorateBackgroundItems(it)
        }
        binding.root.post {
            getScreenSize()
            widthImage = binding.profileImg.width
            Firebase.auth.currentUser!!.run {
                binding.toolbar.title = displayName

                Glide.with(this@PostMainActivity)
                    .load(photoUrl.toString())
                    .thumbnail(.02f)
                    .placeholder(R.drawable.placeholder)
                    .into(binding.profileImg)
            }
        }

        binding.appbar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, i ->
            val abs = abs(i).toFloat() / appBarLayout.totalScrollRange
            val f2: Float = 1.0f - abs /* 1 to 0 */
            binding.profileImg.translationY = widthImage.div(2).plus(16.toPx) * abs
            binding.profileImg.translationX =
                (screenWidth.div(2) - widthImage.div(2) + 24.toPx) * abs
            binding.profileImg.scaleX = max(0.3f, f2)
            binding.profileImg.scaleY = max(0.3f, f2)
        })

    }

    private fun checkIfOldRoomExist() {
        binding.infoLabelShimmer.showShimmer(true)
        resetInfoLabel("Fetching room details", hideShimmer = false)
        Firebase.database
            .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
            .orderByKey()
            .equalTo(depUtils.getRoomIdFromPref())
            .get()
            .addOnSuccessListener {
                if (it.value != null) {
                    observeThisRoomId(depUtils.getRoomIdFromPref())
                } else {
                    animateToVideoView(hide = false, hidePlayBtn = true)
                    resetInfoLabel(delay = true)
                    iRemoteService?.removeUser(depUtils.getRoomIdFromPref())
                }
            }.addOnFailureListener {
                resetInfoLabel()
                iRemoteService?.removeUser(depUtils.getRoomIdFromPref())
                //it.printStackTrace()
            }
    }


    private fun generateDynamicLinkWithRoomId(roomId: String, share: Boolean = false) {
        if (roomId.isBlank())
            return

        val shareUrl = getString(R.string.dynamic_url) + "/$roomId"
        if (share) {
            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.putExtra(
                Intent.EXTRA_TEXT, shareUrl
            )
            sendIntent.type = "text/plain"
            startActivity(
                Intent.createChooser(
                    sendIntent,
                    "Share via"
                )
            )
        } else {
            /*val prefix = "https://$shareUrl"

            val dynamicLink = Firebase.dynamicLinks.dynamicLink {
                link = Uri.parse("https://$shareUrl/$roomId")
                domainUriPrefix = prefix
            }*/

            setClipboard(shareUrl)
            this quickToast "Copied room url"
        }

        /*Firebase.dynamicLinks.shortLinkAsync {
            link = dynamicLink.uri
            domainUriPrefix = prefix
        }.addOnSuccessListener {
            setClipboard(it.shortLink.toString())
            this quickToast "Copied room url"
        }*/
    }

    fun animateToVideoView(hide: Boolean = true, hidePlayBtn: Boolean = true) {
        binding.run {
            //resetInfoLabel("Room joined")

            if (hide)
                lottieView.cancelAnimation()
            else
                lottieView.playAnimation()
            TransitionManager.beginDelayedTransition(binding.childViews.getChildAt(0) as ViewGroup)
            val visibilityVal = if (hide) View.GONE else View.VISIBLE
            val visibilityRevVal = if (!hide) View.GONE else View.VISIBLE

            lottieView.visibility = visibilityVal
            joinRoom.visibility = visibilityVal
            createRoom.visibility = visibilityVal

            logit("Hide play btn=$hidePlayBtn")
            if (depUtils.isUserAdmin()) {
                broadcastFile.visibility = if (hidePlayBtn) View.GONE else View.VISIBLE
            } else {
                broadcastFile.visibility = View.GONE
            }

            shareBtn.visibility = visibilityRevVal
            exitBtn.visibility = visibilityRevVal
        }
    }

    fun Int.animateColorsOfWindow(textColorDef: Boolean = false) {
        val defBg = if (binding.root.background is ColorDrawable) {
            (binding.root.background as ColorDrawable).color
        } else
            getColor(R.color.black)
        ObjectAnimator.ofArgb(defBg, this).run {
            doOnStart {
                reverseAlltextColors(defBg)
                //animateToVideoView()
            }
            addUpdateListener {
                val color = it.animatedValue as Int
                val darkBg = if (!textColorDef) ColorUtils.blendARGB(
                    color,
                    color.getContrastColor(true),
                    .05f
                ) else this@animateColorsOfWindow
                binding.root.setBackgroundColor(color)
                window.statusBarColor = color
                window.navigationBarColor = color
                binding.appbar.setBackgroundColor(color)
                binding.playerView.setShutterBackgroundColor(darkBg)
                binding.playerView.setBackgroundColor(darkBg)
            }
            duration = 2000
            start()
        }
    }

    private fun reverseAlltextColors(defBg: Int) {
        val textC = defBg.getContrastColor()
        binding.toolbar.setCollapsedTitleTextColor(textC)
        binding.infoLabel.setTextColor(textC)
        (binding.shareBtn.getChildAt(0) as ImageView).imageTintList = ColorStateList.valueOf(textC)
        (binding.exitBtn.getChildAt(0) as ImageView).imageTintList = ColorStateList.valueOf(textC)
    }

    private fun decorateBackgroundItems(it: String?) {
        if (it.isNullOrBlank())
            return

        binding.infoLabelShimmer.showShimmer(true)
        val options = RequestOptions().frame(TimeUnit.SECONDS.toMicros(1))
        Glide.with(baseContext).load(it).apply(options).thumbnail(0.03f)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.infoLabelShimmer.hideShimmer()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    resource?.let {
                        val bm = it.toBitmap(50, 50)
                        Palette.from(bm).generate().let {

                            val m =
                                if (it.darkVibrantSwatch != null) it.darkVibrantSwatch else it.dominantSwatch

                            bg = m!!.rgb

                            val textC = ColorUtils.blendARGB(bg, bg.getContrastColor(), .5f)
                            //val textC = it.dominantSwatch!!.bodyTextColor
                            bg.animateColorsOfWindow()
                            binding.toolbar.setCollapsedTitleTextColor(textC)
                            binding.infoLabel.setTextColor(textC)
                            (binding.shareBtn.getChildAt(0) as ImageView).imageTintList =
                                ColorStateList.valueOf(textC)
                            (binding.exitBtn.getChildAt(0) as ImageView).imageTintList =
                                ColorStateList.valueOf(textC)
                            resetInfoLabel("Playing video!", delay = true)
                        }
                    } ?: binding.infoLabelShimmer.hideShimmer()
                    return false
                }
            }).into(binding.profileImg)
    }

    private fun addButtonListeners() {
        binding.broadcastFile.setOnClickListener {
            val inpLayout = LayoutInflater.from(this).inflate(R.layout.dialog_input_link, null)
            val editText: EditText = inpLayout.findViewById(R.id.inputText)

            val vl =
                AlertDialog.Builder(
                    this,
                    R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog
                )
                    .setCancelable(false)
                    .setView(inpLayout)
                    .setNeutralButton("Cancel", null)
                    .setPositiveButton("Accept") { d, _ ->
                        if (editText.text.toString().trim().isBlank()) {
                            this quickToast "Enter a valid link"
                        } else {
                            updateVideoLink(editText.text.toString().toUri())
                            /*depUtils.usersliveViewModel.liveVideoFile.value =
                                editText.text.toString()*/
                            d.dismiss()
                            editText.text.clear()
                        }
                    }
                    .create()
            vl.setContentView(R.layout.dialog_input_link)
            if (!this.isDestroyed)
                vl.show()
        }
        binding.createRoom.setOnClickListener {
            createRoom(
                view = it,
                userId = depUtils.getUserIdFromPref()
            )
        }
        binding.joinRoom.setOnClickListener {
            val dialogInputLinkBinding = DialogInputLinkBinding.inflate(LayoutInflater.from(this))
            dialogInputLinkBinding.inputText.hint = "xxxx xxxx"
            dialogInputLinkBinding.linkDrive.isVisible = false
            dialogInputLinkBinding.title.text = "Room ID"
            val vl =
                AlertDialog.Builder(
                    this,
                    R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog
                )
                    .setCancelable(false)
                    .setView(dialogInputLinkBinding.root)
                    .setNeutralButton("Cancel", null)
                    .setPositiveButton("Ok") { d, _ ->
                        if (dialogInputLinkBinding.inputText.text.toString().trim().isBlank()) {
                            this quickToast "Enter a valid room id"
                        } else {
                            pushUserToRoom(
                                userId = depUtils.getUserIdFromPref(),
                                roomId = dialogInputLinkBinding.inputText.text.toString(),
                                view = it
                            )
                            d.dismiss()
                            dialogInputLinkBinding.inputText.text.clear()
                        }
                    }
                    .create()
            vl.setContentView(R.layout.dialog_input_link)
            if (!this.isDestroyed)
                vl.show()
        }

        binding.exitBtn.setOnClickListener {
            exitAppTask()
        }
        binding.shareBtn.setOnClickListener {
            generateDynamicLinkWithRoomId(depUtils.getRoomIdFromPref(), true)
        }
    }

    private fun exitAppTask(backPress: Boolean = false) {
        if (depUtils.getRoomIdFromPref().isBlank()) {
            if (backPress) {
                super.onBackPressed()
            }
            return
        }

        AlertDialog.Builder(this, R.style.MyThemeOverlay_MaterialComponents_MaterialAlertDialog)
            .run {
                if (depUtils.isUserAdmin()) {
                    setMessage("Exit?\n${depUtils.usersliveViewModel.liveListUsers.value!!.size} members will be removed")
                } else {
                    setMessage("Leave room?")
                }
                setNeutralButton("Cancel", null)
                setPositiveButton("Ok") { v, _ ->
                    v.dismiss()
                    mPlayer?.stop()
                    timer?.cancel()
                    binding.infoLabelShimmer.showShimmer(true)
                    resetInfoLabel("Exiting...", delay = true)
                    mPlayer?.release()
                    iRemoteService?.removeUser(depUtils.getRoomIdFromPref())
                    if (backPress)
                        super.onBackPressed()

                    //depUtils.usersliveViewModel.liveVideoFile.value = ""
                }
                if (!isDestroyed)
                    show()
            }
    }

    override fun onPause() {
        super.onPause()
        mPlayer?.pause()
    }

    private fun serviceInit() {
        val inten = Intent(this, MyService_::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(inten)
        } else
            startService(inten)

        /*if (!isMyServiceRunning(MyService_::class.java)) {
        }*/
        bindService(inten, mConnection, BIND_ADJUST_WITH_ACTIVITY)
    }

    private fun createVideoChooser() {
        if (!depUtils.isUserAdmin())
            return

        val intent = Intent()
        intent.type = "video/mp4"
        intent.action = Intent.ACTION_GET_CONTENT
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            it.data?.data?.let { updateVideoLink(url = it) }
        }.launch(Intent.createChooser(intent, "Select video to play"))
    }

    private fun setupExoplayer(link: Uri) {
        if (link.toString().isBlank()) {
            return
        }
        binding.vidCard.visibility = View.VISIBLE

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
                    updatePlaying(isPlaying)
                    if (isPlaying) {
                        timer = Timer()
                        timer?.schedule(
                            object : TimerTask() {
                                override fun run() {
                                    runOnUiThread {
                                        updateTimeDuration(TimeUnit.MILLISECONDS.toSeconds(mPlayer!!.currentPosition))
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
        mPlayer?.playWhenReady = true
        mPlayer?.prepare()

        mPlayer?.seekTo(1000)
        binding.playerView.player = mPlayer
    }

    override fun onBackPressed() {
        exitAppTask(backPress = true)
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
                        logit(snapshot.value)
                        if (snapshot.value == null) {
                            depUtils.usersliveViewModel.liveListUsers.value = ArrayList()
                            /*resetViewsIfRoomDel()
                            observeThisRoomId("")*/
                        } else {
                            logit(snapshot.value)
                            try {
                                depUtils.usersliveViewModel.liveListUsers.value =
                                    snapshot.value as ArrayList<String>
                                logit(depUtils.usersliveViewModel.liveListUsers.value)

                                if (!depUtils.usersliveViewModel.liveListUsers.value!!.contains(
                                        depUtils.getUserIdFromPref()
                                    )
                                ) {
                                    animateToVideoView(false, hidePlayBtn = true)
                                    depUtils.usersliveViewModel.run {
                                        liveVideoFile.value = ""
                                        liveRoomId.value = ""
                                    }
                                }
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
        observeVideoLink(roomId)
        observePlaying(roomId)
    }

    override fun onStart() {
        super.onStart()
        mPlayer?.play()
    }

    override fun onStop() {
        super.onStop()
        mPlayer?.pause()
    }


    fun updatePlaying(isPlaying: Boolean) {
        if (!depUtils.isUserAdmin())
            return
        val roomId = depUtils.getUserIdFromPref()
        if (roomId == "")
            return

        Firebase.database
            .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
            .child(roomId)
            .child("playing")
            .setValue(isPlaying)
    }

    fun updateTimeDuration(duration: Long = 0) {
        if (!depUtils.isUserAdmin())
            return
        val roomId = depUtils.getUserIdFromPref()
        if (roomId == "" || depUtils.usersliveViewModel.liveVideoFile.value.isNullOrBlank())
            return
        Firebase.database
            .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
            .child(roomId)
            .child("time")
            .setValue(duration.plus(1))
    }

    fun updateVideoLink(url: Uri) {
        if (!depUtils.isUserAdmin())
            return
        val roomId = depUtils.getUserIdFromPref()
        if (roomId == "")
            return
        Firebase.database
            .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
            .child(roomId)
            .child("currentVideo")
            .setValue(url.toString())
    }

    override fun onDestroy() {
        mPlayer?.release()
        logit(iRemoteService)
        depUtils.usersliveViewModel.onCleared()
        iRemoteService?.removeUser(depUtils.getRoomIdFromPref())
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

    fun observeVideoLink(roomId: String) {
        if (roomId.isBlank())
            return
        Firebase.database.getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
            .child(roomId).child("currentVideo").addValueEventListener(object :
                ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    logit(snapshot.value)
                    if (snapshot.value != null)
                        depUtils.usersliveViewModel.liveVideoFile.value = snapshot.value as String
                    else {
                        depUtils.usersliveViewModel.liveVideoFile.value = ""
                        depUtils.miniPrefs.currentGroupId().put("")
                    }
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
                    if (snapshot.value != null) {
                        depUtils.usersliveViewModel.liveVideoPlaying.value =
                            snapshot.value as Boolean
                        //animateToVideoView(hide = true, hidePlayBtn = false)
                    } else {
                        resetViewsIfRoomDel()
                        depUtils.miniPrefs.currentGroupId().put("")
                    }
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }

    private fun resetViewsIfRoomDel() {
        depUtils.usersliveViewModel.liveListUsers.value?.clear()
        animateToVideoView(false, hidePlayBtn = true)
        resetInfoLabel()
    }

    fun resetInfoLabel(
        reason: String = getString(R.string.join_room),
        delay: Boolean = false,
        hideShimmer: Boolean = true
    ) {
        if (hideShimmer) {
            if (!delay) {
                binding.infoLabelShimmer.hideShimmer()
            } else {
                binding.infoLabelShimmer.postDelayed({
                    binding.infoLabelShimmer.hideShimmer()
                }, 2000)
            }
        }
        val spannable: Spannable = SpannableString(reason)

        binding.infoLabel.text = spannable.apply {
            setSpan(
                ForegroundColorSpan(
                    if (reason == getString(R.string.join_room)) Color.RED else Color.GREEN
                ),
                if (spannable.toString().contains(" ")) spannable.toString()
                    .lastIndexOf(" ") else spannable.toString().length,
                spannable.toString().length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.createRoom.isEnabled = true
        binding.infoLabel.requestLayout()
    }

    fun createRoom(userId: String, pd: ProgressDialog? = null, view: View? = null) {
        logit("Creating room id: $userId")
        if (view != null) {
            view.isEnabled = false
            binding.infoLabel.text =
                Html.fromHtml(getString(R.string.creating_room), Html.FROM_HTML_MODE_LEGACY)
            binding.infoLabelShimmer.showShimmer(true)
        }
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
                                this quickToast "Room not active, creating one"
                                Firebase.database
                                    .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
                                    .child(userId)
                                    .get()
                                    .addOnSuccessListener {
                                        var m = HashMap<Any, Any>()
                                        m["playing"] = false
                                        m["time"] = 0L
                                        m["currentVideo"] = ""

                                        // No room active with this ID create it
                                        if (it.value != null) {
                                            m = it.value as HashMap<Any, Any>
                                        }
                                        depUtils.miniPrefs.currentGroupId().put(userId)

                                        Firebase.database
                                            .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
                                            .child(userId)
                                            .setValue(m)
                                            .addOnCompleteListener {
                                                pd?.dismiss()
                                                if (it.isSuccessful) {
                                                    generateDynamicLinkWithRoomId(userId)
                                                    resetInfoLabel("Room created!")
                                                    animateToVideoView(false, hidePlayBtn = false)
                                                    //binding.shareBtn.isVisible = true
                                                    //binding.exitBtn.isVisible = true
                                                } else {
                                                    resetInfoLabel()
                                                    this quickToast it.exception?.localizedMessage
                                                }
                                                observeThisRoomId(userId)
                                                m.clear()
                                            }
                                    }

                            } else {
                                pd?.dismiss()
                                observeThisRoomId(userId)
                                generateDynamicLinkWithRoomId(userId)
                                resetInfoLabel("Room found!")
                                animateToVideoView(false, hidePlayBtn = false)
                                //binding.shareBtn.isVisible = true
                                //binding.exitBtn.isVisible = true
                            }
                        }
                } else {
                    pd?.dismiss()
                    this quickToast "Invalid room id"
                    resetInfoLabel()
                }
            }
            .addOnFailureListener {
                pd?.dismiss()
                this quickToast it.localizedMessage
                resetInfoLabel()
            }
    }

    fun pushUserToRoom(roomId: String, userId: String, view: View? = null) {
        view?.isEnabled = false
        if (roomId.isBlank()) {
            view?.isEnabled = true
            this quickToast "Room id is empty"
            return
        }
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
                depUtils.miniPrefs.currentGroupId().put(roomId)
                val roomDetails: HashMap<String, String> =
                    it.child(roomId).value as HashMap<String, String>

                if (it.value == null) {
                    this quickToast "Invalid room id"
                    view?.isEnabled = true
                    pd.dismiss()
                } else if (depUtils.usersliveViewModel.liveListUsers.value!!.contains(userId)) {
                    animateToVideoView()
                    logit("User exist")
                    view?.isEnabled = true
                    this quickToast "Already present in room"
                    pd.dismiss()
                } else if (roomId == userId) {
                    createRoom(userId, pd, view = view)
                    view?.isEnabled = true
                    //pd.dismiss()
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
                                    createRoom(roomId, pd, view = view)
                                } else {
                                    view?.isEnabled = true
                                    this quickToast "Room not active anymore"
                                    pd.dismiss()
                                }
                            } else {
                                //depUtils.miniPrefs.isUserPresenting.put(false)
                                logit("Adding user=${userId} into room=${roomId}")

                                this quickToast "Joining room of ${roomDetails["name"]}"

                                depUtils.usersliveViewModel.liveListUsers.value!!.add(userId)
                                Firebase.database
                                    .getReference(StreamxConstants.ReferenceKeys.KEY_ACTIVE_ROOM)
                                    .child(roomId)
                                    .child("users")
                                    .setValue(depUtils.usersliveViewModel.liveListUsers.value!!)
                                    .addOnCompleteListener {
                                        view?.isEnabled = true
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
                resetInfoLabel()
                animateToVideoView()
                view?.isEnabled = true
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
                        userId = depUtils.getUserIdFromPref()
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
        val isLandScape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        val visGone = if (isLandScape) View.GONE else View.VISIBLE

        mPlayer?.let { player ->
            if (player.playbackState == Player.STATE_READY) {
                (binding.infoLabelShimmer.parent as ViewGroup).visibility = visGone
                binding.exitBtn.visibility = visGone
                binding.shareBtn.visibility = visGone
                binding.infoLabel.visibility = visGone
                val lp: FrameLayout.LayoutParams =
                    binding.vidCard.layoutParams as FrameLayout.LayoutParams
                lp.setMargins(0, 0, 0, 0)
                binding.vidCard.layoutParams = lp
            }
        }
        if (isLandScape) {
            binding.appbar.visibility = View.GONE
            (binding.childViews.layoutParams as CoordinatorLayout.LayoutParams).behavior =
                null
            getColor(R.color.black).animateColorsOfWindow()
            mPlayer?.videoScalingMode =
                MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            val lp2 = binding.vidCard.layoutParams as FrameLayout.LayoutParams
            lp2.setMargins(0, 0, 0, 0)
            binding.vidCard.layoutParams = lp2

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
            }

            binding.vidCard.updateLayoutParams {
                height = screenHeight
            }

        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(true)
            }

            val lp2 = binding.vidCard.layoutParams as FrameLayout.LayoutParams
            lp2.setMargins(16.toPx, 16.toPx, 16.toPx, 16.toPx)
            binding.vidCard.layoutParams = lp2
            mPlayer?.videoScalingMode = MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT
            binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            binding.appbar.visibility = View.VISIBLE
            (binding.childViews.layoutParams as CoordinatorLayout.LayoutParams).behavior =
                AppBarLayout.ScrollingViewBehavior()
            bg.animateColorsOfWindow()
        }
    }

    private fun getScreenSize() {
        val display: Display = windowManager.defaultDisplay
        val size = Point()

        display.getSize(size)
        screenWidth = size.x
        screenHeight = size.y
    }
}