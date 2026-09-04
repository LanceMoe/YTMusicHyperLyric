package moe.lance.ytmusiclyric.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import moe.lance.ytmusiclyric.R

/** Native-view counterparts of HyperLyric's Miuix cards and preference rows. */
internal class HyperStyle(private val activity: Activity) {
    val background = activity.getColor(R.color.hyper_background)
    val surface = activity.getColor(R.color.hyper_surface)
    val field = activity.getColor(R.color.hyper_field)
    val text = activity.getColor(R.color.hyper_text)
    val secondary = activity.getColor(R.color.hyper_secondary)
    val primary = activity.getColor(R.color.hyper_primary)
    val primaryContainer = activity.getColor(R.color.hyper_primary_container)
    val error = activity.getColor(R.color.hyper_error)
    val errorContainer = activity.getColor(R.color.hyper_error_container)
    val success = activity.getColor(R.color.hyper_success)

    fun dp(value: Int) = (value * activity.resources.displayMetrics.density + 0.5f).toInt()

    fun shape(color: Int, radius: Int = 20) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    fun ripple(color: Int, radius: Int = 16) = RippleDrawable(
        ColorStateList.valueOf((primary and 0x00FFFFFF) or 0x18000000),
        shape(color, radius), shape(Color.WHITE, radius),
    )

    fun label(value: String, size: Float = 16f, color: Int = text, bold: Boolean = false) =
        TextView(activity).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setLineSpacing(dp(2).toFloat(), 1f)
        }

    fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

    fun row() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun card() = column().apply {
        background = shape(surface)
        clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    fun section(parent: LinearLayout, title: String): LinearLayout {
        parent.addView(label(title, 14f, secondary).apply {
            setPadding(dp(20), dp(24), dp(20), dp(10))
        })
        return card().also { parent.addView(it) }
    }

    fun paddedContent(parent: LinearLayout) = column().apply {
        setPadding(dp(20), dp(16), dp(20), dp(18))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }

    fun hint(parent: LinearLayout, value: String): TextView = label(value, 13f, secondary).apply {
        setPadding(0, dp(6), 0, dp(12))
        parent.addView(this)
    }

    fun divider(parent: LinearLayout) {
        parent.addView(View(activity).apply {
            setBackgroundColor(activity.getColor(R.color.hyper_divider))
        }, LinearLayout.LayoutParams(-1, dp(1)).apply {
            marginStart = dp(20)
            marginEnd = dp(20)
        })
    }

    fun preference(parent: LinearLayout, title: String, summary: String): LinearLayout = row().apply {
        setPadding(dp(20), dp(17), dp(20), dp(17))
        minimumHeight = dp(76)
        val copy = column()
        copy.addView(label(title))
        if (summary.isNotEmpty()) copy.addView(label(summary, 13f, secondary).apply {
            setPadding(0, dp(4), 0, 0)
        })
        addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }

    fun toggle(parent: LinearLayout, title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val row = preference(parent, title, summary)
        val toggle = Switch(activity).apply {
            contentDescription = title
            isChecked = checked
            showText = false
            minimumWidth = dp(52)
            minimumHeight = dp(48)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.rgb(0, 111, 255), secondary),
            )
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
        row.addView(toggle, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(16) })
        row.background = ripple(surface)
        row.setOnClickListener { toggle.toggle() }
    }

    fun button(label: String, prominent: Boolean = false, destructive: Boolean = false, onClick: () -> Unit) =
        Button(activity).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            minimumHeight = dp(48)
            minHeight = dp(48)
            minimumWidth = 0
            minWidth = 0
            setPadding(dp(12), dp(10), dp(12), dp(10))
            stateListAnimator = null
            val foreground = if (destructive) this@HyperStyle.error else if (prominent) Color.WHITE else primary
            setTextColor(ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(secondary, foreground),
            ))
            background = ripple(when {
                destructive -> errorContainer
                prominent -> Color.rgb(0, 111, 255)
                else -> primaryContainer
            })
            setOnClickListener { onClick() }
        }

    fun input(hintText: String, value: String = "", multiline: Boolean = false) = EditText(activity).apply {
        hint = hintText
        contentDescription = hintText
        setText(value)
        textSize = 15f
        setTextColor(this@HyperStyle.text)
        setHintTextColor(secondary)
        background = shape(field, 12)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        setSingleLine(!multiline)
        minimumHeight = dp(52)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }

    fun badge(value: String, failed: Boolean = false) = label(value, 11f, if (failed) error else primary, true).apply {
        background = shape(if (failed) errorContainer else primaryContainer, 6)
        setPadding(dp(8), dp(3), dp(8), dp(3))
    }

    fun showDialog(builder: AlertDialog.Builder): AlertDialog = builder.show().also { dialog ->
        dialog.window?.setBackgroundDrawable(shape(surface, 24))
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE).forEach {
            dialog.getButton(it)?.setTextColor(primary)
        }
    }

    /** Shared insets and typography; constrain the column on tablets and in landscape. */
    fun page(title: String, subtitle: String? = null, back: Boolean = false): LinearLayout {
        activity.window.setDecorFitsSystemWindows(false)
        val dark = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        val scroll = ScrollView(activity).apply {
            id = R.id.settings_scroll
            setBackgroundColor(this@HyperStyle.background)
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }
        val content = column().apply {
            setPadding(dp(12), dp(12), dp(12), dp(32))
            isFocusableInTouchMode = true
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        if (back) content.addView(button("‹  返回") { activity.finish() }.apply {
            contentDescription = "返回设置"
            background = ripple(this@HyperStyle.background)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) }
        })
        content.addView(label(title, 32f, bold = true).apply {
            setPadding(dp(20), dp(if (back) 8 else 32), dp(20), dp(8))
            isAccessibilityHeading = true
        })
        if (subtitle != null) content.addView(label(subtitle, 14f, secondary).apply {
            setPadding(dp(20), 0, dp(20), dp(4))
        })
        fun updatePadding(insets: WindowInsets?) {
            val bars = insets?.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val keyboard = insets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0
            val left = bars?.left ?: 0
            val right = bars?.right ?: 0
            val gutter = ((scroll.width - left - right - dp(680)) / 2).coerceAtLeast(0)
            scroll.setPadding(left + gutter, bars?.top ?: 0, right + gutter, maxOf(bars?.bottom ?: 0, keyboard))
        }
        scroll.setOnApplyWindowInsetsListener { _, insets -> updatePadding(insets); insets }
        scroll.addOnLayoutChangeListener { _, l, _, r, _, oldL, _, oldR, _ ->
            if (r - l != oldR - oldL) updatePadding(scroll.rootWindowInsets)
        }
        activity.setContentView(scroll)
        // PhoneWindow.getInsetsController() requires the DecorView created by setContentView.
        // A safe call on its result cannot protect against a null DecorView inside that getter.
        activity.window.insetsController?.setSystemBarsAppearance(if (dark) 0 else lightBars, lightBars)
        content.requestFocus()
        scroll.requestApplyInsets()
        return content
    }
}
