package io.github.stardomains3.oxproxion

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.graphics.toColorInt

class SystemMessageDialogAdapterCh(
    context: Context,
    private val titles: Array<String>,
    private val currentIndex: Int
) : ArrayAdapter<String>(context, android.R.layout.simple_list_item_single_choice, titles) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        if (position == currentIndex) {
            textView.setTextColor("#a0610a".toColorInt())  // Orange for current
        } else {
            textView.setTextColor(Color.WHITE)  // Default color
        }
        return view
    }
}
