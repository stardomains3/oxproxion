// 5. NEW: Create `LicenseDetailFragment.kt`
package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar

class LicenseDetailFragment : Fragment() {

    companion object {
        private const val ARG_LIBRARY_NAME = "libraryName"
        private const val ARG_LICENSE_CONTENT = "licenseContent"

        fun newInstance(libraryName: String, licenseContent: String): LicenseDetailFragment {
            return LicenseDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_LIBRARY_NAME, libraryName)
                    putString(ARG_LICENSE_CONTENT, licenseContent)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_license_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val args = requireArguments()
        val libraryName = args.getString(ARG_LIBRARY_NAME) ?: "License"
        val licenseContent = args.getString(ARG_LICENSE_CONTENT) ?: "License text unavailable"

        toolbar.title = libraryName  // ← DYNAMIC: "androidx-core-ktx License" or just libraryName
        view.findViewById<TextView>(R.id.licenseContent).text = licenseContent
    }
}
