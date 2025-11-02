package io.github.stardomains3.oxproxion

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class PresetDialogAdapter(context: Context, private val titles: Array<String>) :
    ArrayAdapter<String>(context, android.R.layout.simple_list_item_single_choice, titles) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            android.R.layout.simple_list_item_single_choice, parent, false
        )
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = titles[position]
        textView.setTextColor(android.graphics.Color.WHITE)
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            android.R.layout.simple_list_item_single_choice, parent, false
        )
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = titles[position]
        textView.setTextColor(android.graphics.Color.WHITE)
        return view
    }
}