package com.pearl.keyboard.util

import android.content.Context
import android.util.TypedValue

/** Density-independent pixels → raw pixels. */
fun Context.dp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

/** Scale-independent pixels (text) → raw pixels. */
fun Context.sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

fun Context.dpInt(value: Float): Int = dp(value).toInt()
