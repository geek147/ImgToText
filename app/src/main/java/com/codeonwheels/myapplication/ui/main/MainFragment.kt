package com.codeonwheels.myapplication.ui.main

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.codeonwheels.myapplication.databinding.FragmentMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainFragment : Fragment(), LocationListener {

    companion object {
        fun newInstance() = MainFragment()
    }

    private val requestImageCapture = 1
    lateinit var currentPhotoPath: String
    private var imageBitmap //to store the captured image
            : Bitmap? = null
    private lateinit var locationManager: LocationManager
    private val locationPermissionCode = 2

    private lateinit var viewModel: MainViewModel

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private val latitudePI: Double =  -6.19308955
    private val longitudePI: Double = 106.821874260851


    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(layoutInflater)
        (activity as AppCompatActivity?)!!.supportActionBar!!.setDisplayHomeAsUpEnabled(false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            captureImageButton.setOnClickListener {
                dispatchTakePictureIntent()
            }

            detectTextButton.setOnClickListener {
                detectTextFromImage()
            }
        }
        getLocation()

    }

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val theta = lon1 - lon2
        var dist = (Math.sin(deg2rad(lat1))
                * Math.sin(deg2rad(lat2))
                + (Math.cos(deg2rad(lat1))
                * Math.cos(deg2rad(lat2))
                * Math.cos(deg2rad(theta))))
        dist = Math.acos(dist)
        dist = rad2deg(dist)
        dist *= 60 * 1.1515 * 1.609344
        return dist
    }

    private fun deg2rad(deg: Double): Double {
        return deg * Math.PI / 180.0
    }

    private fun rad2deg(rad: Double): Double {
        return rad * 180.0 / Math.PI
    }

    private fun getLocation() {
        locationManager = requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if ((ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionCode)
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5f, this)
    }
    override fun onLocationChanged(location: Location) {
        binding.latlongText.text = "Latitude: " + location.latitude + " , Longitude: " + location.longitude + getAddress(location.latitude, location.longitude)
        binding.distanceText.text = distance(location.latitude, location.longitude, latitudePI, longitudePI).toString()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Permission Granted", Toast.LENGTH_SHORT).show()
            }
            else {
                Toast.makeText(requireContext(), "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getAddress(lat: Double, lng: Double) : String {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses: List<Address>? = geocoder.getFromLocation(lat, lng, 1)
            val obj: Address = addresses!![0]
            var add: String = obj.getAddressLine(0)
            add = """
            $add
            ${obj.countryName}
            """.trimIndent()
            add = """
            $add
            ${obj.countryCode}
            """.trimIndent()
            add = """
            $add
            ${obj.adminArea}
            """.trimIndent()
            add = """
            $add
            ${obj.postalCode}
            """.trimIndent()
            add = """
            $add
            ${obj.subAdminArea}
            """.trimIndent()
            add = """
            $add
            ${obj.locality}
            """.trimIndent()
            add = """
            $add
            ${obj.subThoroughfare}
            """.trimIndent()
            Log.v("IGA", "Address$add")
            // Toast.makeText(this, "Address=>" + add,
            // Toast.LENGTH_SHORT).show();

            return add

            // TennisAppActivity.showDialog(add);
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
            Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            return ""
        }
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(requireActivity().packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    return
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        requireContext(),
                        "com.codeonwheels.myapplication", //must be the same as manifest
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, requestImageCapture)
                }
            }
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun galleryAddPic() {
        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
            val f = File(currentPhotoPath)
            mediaScanIntent.data = Uri.fromFile(f)
            requireActivity().sendBroadcast(mediaScanIntent)
        }
    }

    private fun setPic() {
        with(binding) {
            val targetW: Int = photoContainer.width
            val targetH: Int = photoContainer.height

            val bmOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true

                BitmapFactory.decodeFile(currentPhotoPath, this)

                val photoW: Int = outWidth
                val photoH: Int = outHeight

                val scaleFactor: Int = Math.max(1, Math.min(photoW / targetW, photoH / targetH))

                inJustDecodeBounds = false
                inSampleSize = scaleFactor
                inPurgeable = true
            }
            BitmapFactory.decodeFile(currentPhotoPath, bmOptions)?.also { bitmap ->
                imageBitmap=bitmap
                photoContainer.setImageBitmap(imageBitmap)
            }
        }
    }

    private fun detectTextFromImage() {
        val image = InputImage.fromBitmap(imageBitmap!!, 90)
         recognizer.process(image)
            .addOnSuccessListener { visionText ->
                binding.recognizedText.text= visionText.text
            }
            .addOnFailureListener { e ->
                Log.d("ilham",e.toString())
            }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestImageCapture && resultCode == RESULT_OK) {
            galleryAddPic() // If we want to save the picture
            setPic()
        }
    }

}