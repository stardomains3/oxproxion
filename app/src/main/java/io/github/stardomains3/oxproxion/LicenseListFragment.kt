package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import de.philipp_bobek.oss_licenses_parser.OssLicensesParser
import de.philipp_bobek.oss_licenses_parser.ThirdPartyLicenseMetadata

class LicenseListFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_licenses_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = "Third-Party Licenses"
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // SAFE: Try load, fallback empty
        try {
            val metadataList = resources.openRawResource(R.raw.third_party_license_metadata)
                .use { OssLicensesParser.parseMetadata(it) }
                .sortedBy { it.libraryName }

            Log.d("Licenses", "Loaded ${metadataList.size} libs")
            toolbar.title = "Third-Party Licenses (${metadataList.size})"

            recyclerView.adapter = LicenseAdapter(metadataList) { metadata ->
                try {
                    val licenseContent = resources.openRawResource(R.raw.third_party_licenses)
                        .use { OssLicensesParser.parseLicense(metadata, it).licenseContent }

                    parentFragmentManager.beginTransaction()
                        .hide(this)
                        .add(R.id.fragment_container, LicenseDetailFragment.newInstance(metadata.libraryName, licenseContent))
                        .addToBackStack(null)
                        .commit()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "License text unavailable", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            Log.e("Licenses", "Raw files missing - normal first build", e)
            Toast.makeText(requireContext(), "Rebuild app to load licenses", Toast.LENGTH_LONG).show()
            recyclerView.adapter = LicenseAdapter(emptyList()) {}
        }
    }

    private class LicenseAdapter(
        private val licenses: List<ThirdPartyLicenseMetadata>,
        private val onLicenseClick: (ThirdPartyLicenseMetadata) -> Unit
    ) : RecyclerView.Adapter<LicenseAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameText: TextView = itemView.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.nameText.text = licenses[position].libraryName
            holder.itemView.setOnClickListener { onLicenseClick(licenses[position]) }
        }

        override fun getItemCount() = licenses.size
    }
}
