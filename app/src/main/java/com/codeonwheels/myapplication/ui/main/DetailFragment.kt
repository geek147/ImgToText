package com.codeonwheels.myapplication.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.codeonwheels.myapplication.databinding.FragmentDetailBinding
import com.codeonwheels.myapplication.model.FirebaseItem

class DetailFragment : Fragment() {

    companion object {
        val TAG = this::class.simpleName
        const val EXTRA_USER_DETAIL = "EXTRA_USER_DETAIL:"

        @JvmStatic
        fun create(
            item: FirebaseItem
        ): DetailFragment {
            val fragment = DetailFragment()
            fragment.arguments = Bundle().apply {
                putParcelable(EXTRA_USER_DETAIL, item)
            }

            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }


    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    var item: FirebaseItem? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        item = arguments?.getParcelable(EXTRA_USER_DETAIL)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBackButton()
        with(binding) {
            editTextWritten.setText(item?.writtenText)
            editTextDistance.setText(item?.distance)
            editTextEta.setText(item?.estimatedTime)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle presses on the action bar menu items
        when (item.itemId) {
            android.R.id.home -> {
                activity?.supportFragmentManager?.popBackStack();
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupBackButton() {
        if (activity is AppCompatActivity) {
            (activity as AppCompatActivity?)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
    }

}