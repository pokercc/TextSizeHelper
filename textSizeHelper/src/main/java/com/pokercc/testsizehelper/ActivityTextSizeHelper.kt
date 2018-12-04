package com.pokercc.testsizehelper

import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.lang.IllegalArgumentException

/**
 * 动态修改单个activity文字大小的帮助类
 *
 *   对某个TextView 或者是ViewGroup 禁用字体缩放的方法
 *    1. 如果某个textView 不需要缩放字体，请设置tag={user_dp...}
 *    2. 或者是代码设置tag,TextView.setTag(R.id.TEXT_SIZE_HELPER_USER_DP,1)
 *    3. 实现自定义的ViewPredicate,排除不支持的view
 */
const val USE_DP = "use_dp"

class ActivityTextSizeHelper(
    /**
     * 允许字体大小缩放的根布局
     */
    private val rootView: ViewGroup,
    /**
     * 自定义是否允许字体缩放的校验器
     */
    private var viewPredicate: (View) -> Boolean = { true }
) {


    /**
     * 扩展属性，原始字体大小，单位sp
     */
    private var TextView.originSize: Float?
        get() = getTag(R.id.TEXT_SIZE_HELPER_ORIGIN_SIZE) as Float?
        set(value) {
            setTag(R.id.TEXT_SIZE_HELPER_ORIGIN_SIZE, value)
        }

    // 默认缩放比例
    private val defaultScaledDensity: Float = rootView.resources.displayMetrics.scaledDensity
    // 字体缩放的比例
    var fontScaled: Float = 1.0f
        private set
    // 新的缩放比例
    val newScaledDensity: Float
        get() = defaultScaledDensity * fontScaled

    init {
        // 保存textView的默认尺寸
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            enableAllTextViews()
                .forEach {
                    if (it.originSize == null) {
                        it.originSize = it.textSize / it.resources.displayMetrics.scaledDensity
                    }
                }
        }
    }


    /**
     * 改变字体大小
     * @param fontScaled 字体缩放比例[0.1,8]
     * @param globalApp 是否是整个app生效
     */
    fun onFontScaled(fontScaled: Float) {
        if (fontScaled < 0.1 || fontScaled > 8) {
            throw IllegalArgumentException("fontScale to large or too small,is $fontScaled")
        }
        if (fontScaled == this.fontScaled) {
            return
        }

        Log.d("ActivityTextSizeHelper", "fontScaled=$fontScaled")
        this.fontScaled = fontScaled

        // 动态修改view树里面的textView
        enableAllTextViews()
            .forEach {
                it.originSize?.apply {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_SP, this)
                }
            }

    }


    /**
     * 递归获取全部的textView
     */
    private fun enableAllTextViews(): List<TextView> {

        return rootView.allTextViews {
            // 过滤代码里设置的tag,不支持缩放字体的TextView
            it.getTag(R.id.TEXT_SIZE_HELPER_USER_DP) == null &&
                    // 过滤xml或代码里设置的tag，不支持缩放字体的TextView
                    it.tag !is String || it.tag is String && USE_DP !in it.tag as String
        }
    }


    /**
     * 递归获取全部的textView
     */
    private fun ViewGroup.allTextViews(predicate: (View) -> Boolean): List<TextView> {
        val views = mutableListOf<TextView>()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (!predicate(child)) {
                continue
            }
            if (!viewPredicate(child)) {
                continue
            }

            if (child is ViewGroup) {
                views.addAll(child.allTextViews(predicate))
                continue
            }
            if (child is TextView) {
                views.add(child)
            }

        }
        return views.toList()
    }


    var resources: Resources? = null
    fun proxyResource(resources: Resources): Resources {
        if (this.resources == null) {
            this.resources = TextSizeResource(resources, 1.0f)
        }
        return this.resources!!.also {
            it.displayMetrics.scaledDensity = newScaledDensity
        }
    }
}

private class TextSizeResource(resources: Resources, scaledDensity: Float) : Resources(
    resources.assets,
    DisplayMetrics().also {
        it.setTo(resources.displayMetrics)
        it.scaledDensity = scaledDensity
    },
    resources.configuration
)