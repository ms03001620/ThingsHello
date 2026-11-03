package org.mark.mobile.utils

import android.view.View

/**
 * 常用 View 扩展工具函数
 */
fun View.show() {
    visibility = View.VISIBLE
}

fun View.hide() {
    visibility = View.GONE
}
