package org.streamx.di

import android.content.Context
import android.content.SharedPreferences
import org.MyApp
import org.androidannotations.annotations.EBean
import org.androidannotations.annotations.RootContext
import org.androidannotations.annotations.sharedpreferences.Pref
import org.streamx.repos.viewmodels.AllUsersViewModel
import org.streamx.utils.MiniPrefs_
import org.streamx.utils.getLocalClassName

@EBean(scope = EBean.Scope.Singleton)
open class DepUtils {

    @RootContext
    lateinit var context: MyApp

    @Pref
    lateinit var miniPrefs: MiniPrefs_

    val usersliveViewModel by lazy { AllUsersViewModel() }
    val sharedPref by lazy { context.getSharedPreferences(getLocalClassName(context), Context.MODE_PRIVATE) }

    fun setGroupId(roomId: String){
        sharedPref.edit().putString("currentGroupId", roomId).commit()
    }

    fun getGroupId() = sharedPref.getString("currentGroupId", "")!!
}