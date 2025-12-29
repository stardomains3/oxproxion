package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar

class EditMessageFragment : Fragment() {


    private lateinit var contentEditText: EditText

    companion object {
        private const val ARG_POSITION = "position"
        private const val ARG_CONTENT = "content"

        fun newInstance(position: Int, content: String): EditMessageFragment {
            val args = Bundle().apply {
                putInt(ARG_POSITION, position)
                putString(ARG_CONTENT, content)
            }
            return EditMessageFragment().apply { arguments = args }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_message, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        contentEditText = view.findViewById(R.id.edit_text_message_content)

        // 1. Setup Toolbar
        toolbar.title = "Edit Message"
        toolbar.setNavigationOnClickListener {
            // Back button acts as Cancel (just pop back)
            parentFragmentManager.popBackStack()
        }

        // 2. Get Arguments
        //  val position = arguments?.getInt(ARG_POSITION) ?: -1
        val content = arguments?.getString(ARG_CONTENT) ?: ""

        // 3. Pre-fill data
        contentEditText.setText(content)
        contentEditText.setSelection(content.length) // Cursor at end

        // 4. Handle Save
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_save_message -> {
                    val newContent = contentEditText.text.toString().trim()
                    // Make sure you look up the position from arguments again
                    val position = arguments?.getInt(ARG_POSITION) ?: -1

                    if (newContent.isNotBlank() && position != -1) {

                        // 1. Bundle the result
                        val resultBundle = Bundle().apply {
                            putInt("position", position)
                            putString("content", newContent)
                        }

                        // 2. Send result to ChatFragment
                        parentFragmentManager.setFragmentResult("edit_request_key", resultBundle)

                        // 3. Close this fragment
                        parentFragmentManager.popBackStack()
                    }
                    true
                }
                else -> false
            }
        }
    }
}