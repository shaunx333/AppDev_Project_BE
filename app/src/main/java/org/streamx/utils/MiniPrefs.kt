package org.streamx.utils

import org.androidannotations.annotations.sharedpreferences.DefaultBoolean
import org.androidannotations.annotations.sharedpreferences.DefaultString
import org.androidannotations.annotations.sharedpreferences.SharedPref

@SharedPref
interface MiniPrefs {

    @DefaultBoolean(false)
    fun isUserPresenting(): Boolean

    @DefaultString("")
    fun currentUserId(): String

    @DefaultString("")
    fun currentGroupId(): String

}