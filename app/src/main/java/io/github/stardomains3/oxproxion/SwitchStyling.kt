package io.github.stardomains3.oxproxion

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.view.View
import androidx.core.graphics.toColorInt
import com.google.android.material.materialswitch.MaterialSwitch

// 🔥 Shared switch style (your exact original style)
fun MaterialSwitch.styleSwitch() {
    val thumbTintSelector = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        ),
        intArrayOf(
            "#000000".toColorInt(),  // Checked: Black thumb
            "#686868".toColorInt()   // Unchecked: Gray thumb
        )
    )
    val trackTintSelector = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        ),
        intArrayOf(
            "#a0610a".toColorInt(),  // Checked: Orange track
            "#000000".toColorInt()   // Unchecked: Black track
        )
    )

    trackTintList = trackTintSelector
    thumbTintList = thumbTintSelector
    thumbTintMode = PorterDuff.Mode.SRC_ATOP
    trackTintMode = PorterDuff.Mode.SRC_ATOP
}

// 🔥 Convenience: style every MaterialSwitch inside a view (matches your old loop)
fun styleAllSwitches(root: View, vararg ids: Int) {
    ids.forEach { id ->
        root.findViewById<MaterialSwitch>(id)?.styleSwitch()
    }
}
