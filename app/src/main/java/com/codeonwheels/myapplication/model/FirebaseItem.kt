package com.codeonwheels.myapplication.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class FirebaseItem(
    val timeStamp: String,
    val writtenText : String,
    val distance: String,
    val estimatedTime: String
) : Parcelable
