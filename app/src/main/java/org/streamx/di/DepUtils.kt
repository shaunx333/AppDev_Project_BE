package org.streamx.di

import org.MyApp
import org.androidannotations.annotations.EBean
import org.androidannotations.annotations.RootContext
import org.androidannotations.annotations.sharedpreferences.Pref
import org.streamx.repos.viewmodels.AllUsersViewModel
import org.streamx.utils.MiniPrefs_

@EBean(scope = EBean.Scope.Singleton)
open class DepUtils {

    fun getUserIdFromPref(): String = miniPrefs.currentUserId().get()
    fun getRoomIdFromPref(): String = miniPrefs.currentGroupId().get()

    fun isUserAdmin() =
        (getRoomIdFromPref().isNotBlank() && getUserIdFromPref() == getRoomIdFromPref())

    @RootContext
    lateinit var context: MyApp

    @Pref
    lateinit var miniPrefs: MiniPrefs_

    val usersliveViewModel by lazy { AllUsersViewModel() }
}