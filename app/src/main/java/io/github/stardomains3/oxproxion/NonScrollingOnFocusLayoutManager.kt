package io.github.stardomains3.oxproxion

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class NonScrollingOnFocusLayoutManager(context: Context) : LinearLayoutManager(context) {

    /**
     * This method is called when a child view requests to be brought on screen.
     * By returning false, we are telling the RecyclerView to IGNORE this request,
     * which prevents it from scrolling when the TextView gains focus during a long-press.
     */
    override fun requestChildRectangleOnScreen(
        parent: RecyclerView, child: View, rect: Rect,
        immediate: Boolean, focusedChildVisible: Boolean
    ): Boolean {
        // Allow TextViews (popup needs brief focus) → minimal/no wild scroll
        return if (child is TextView) {
            super.requestChildRectangleOnScreen(parent, child, rect, immediate, focusedChildVisible)
        } else {
            false  // Block everything else → no wild list scroll
        }
    }
}