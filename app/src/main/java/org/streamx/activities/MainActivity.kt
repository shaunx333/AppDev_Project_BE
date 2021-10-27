package org.streamx.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.motion.widget.MotionLayout
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import eightbitlab.com.blurview.RenderScriptBlur
import org.androidannotations.annotations.Bean
import org.androidannotations.annotations.EActivity
import org.streamx.R
import org.streamx.base.BaseActivity
import org.streamx.databinding.ActivityMainBinding
import org.streamx.di.DepUtils
import org.streamx.utils.StreamxConstants
import org.streamx.utils.logit
import org.streamx.utils.quickToast
import org.streamx.utils.startAct

@EActivity
open class MainActivity : BaseActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {

    @Bean
    lateinit var depUtils: DepUtils

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    task.result.user?.let { user ->
                        val map: HashMap<String, String?> = HashMap()


                        map["name"] = user.displayName
                        map["email"] = user.email
                        map["profileImg"] = user.photoUrl.toString()

                        Firebase.database
                            .getReference(StreamxConstants.ReferenceKeys.KEY_ALL_USERS)
                            .child("users")
                            .child(user.uid)
                            .setValue(map).addOnCompleteListener {
                                if (it.isSuccessful) {
                                    depUtils.miniPrefs.currentUserId().put(user.uid)
                                    startAct(PostMainActivity_::class.java)
                                } else {
                                    this quickToast "Failed: ${it.exception?.message}"
                                }
                            }
                    }
                    // Sign in success, update UI with the signed-in user's information
                    Log.d("AAA", "signInWithCredential:success")
                } else {
                    Log.e("AAA", "signInWithCredential:failed")
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 555) {
            val mTask: Task<GoogleSignInAccount> =
                GoogleSignIn.getSignedInAccountFromIntent(data)
            mTask.addOnSuccessListener {
                it.idToken?.let { firebaseAuthWithGoogle(it) }
            }.addOnFailureListener {
                it.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        supportActionBar?.hide()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        if (depUtils.miniPrefs.currentUserId().get() != "") {
            startAct(PostMainActivity_::class.java)
        }
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility += View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        binding.wew
            .setupWith(binding.root as ViewGroup)
            .setBlurRadius(5f)
            .setBlurEnabled(true)
            .setBlurAutoUpdate(false)
            .setBlurAlgorithm(RenderScriptBlur(this))
            .setHasFixedTransformationMatrix(true)
        val gso = GoogleSignInOptions
            .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        binding.motion.setTransitionListener(object : MotionLayout.TransitionListener {
            override fun onTransitionStarted(
                motionLayout: MotionLayout?,
                startId: Int,
                endId: Int
            ) {

            }

            override fun onTransitionChange(
                motionLayout: MotionLayout?,
                startId: Int,
                endId: Int,
                progress: Float
            ) {

            }

            override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
                val googleSignInClient = GoogleSignIn.getClient(this@MainActivity, gso)
                startActivityForResult(googleSignInClient.signInIntent, 555)
            }

            override fun onTransitionTrigger(
                motionLayout: MotionLayout?,
                triggerId: Int,
                positive: Boolean,
                progress: Float
            ) {

            }

        })
    }
}