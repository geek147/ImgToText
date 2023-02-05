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
import com.codeonwheels.myapplication.ExifUtil
import com.codeonwheels.myapplication.ExifUtil.rotateBitmap
import com.codeonwheels.myapplication.R
import com.codeonwheels.myapplication.databinding.FragmentMainBinding
import com.codeonwheels.myapplication.model.FirebaseItem
import com.google.firebase.database.*
import com.google.maps.DistanceMatrixApi
import com.google.maps.GeoApiContext
import com.google.maps.model.LatLng
import com.google.maps.model.TravelMode
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

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private val latitudePI: Double =  -6.19308955
    private val longitudePI: Double = 106.821874260851

    private val API_KEY : String = "AIzaSyBWC45IjsUyCGLzfQiKHvnUrsH3vZm4uxc"
    private lateinit var database : DatabaseReference

    var listData : List<String> = listOf()
    var currentLocation: Location = Location("Current Location")

    private var timeStamp: String = ""

    private val  recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                pgProgressList.visibility = View.VISIBLE
            }

        }
    }

    // get current location latitude and longitude
    private fun getLocation() {
        locationManager = requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if ((ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionCode)
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5f, this)
    }
    override fun onLocationChanged(location: Location) {
        currentLocation = location
        gotoDetail()
    }

    // go to detail page after get written text, distance and estimated time
    private fun gotoDetail() {
        listData = calculateDistanceAndEstimatedTime(currentLocation, latitudePI, longitudePI)
        with(binding) {
            pgProgressList.visibility = View.GONE
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.container, DetailFragment.create(FirebaseItem(timeStamp = timeStamp,recognizedText.text.toString(), listData[0], listData[1])))
                .commitNow()
        }

    }


    // calculate distance and estimated tiume using distance matrix api from google
    private fun calculateDistanceAndEstimatedTime (fromlocation: Location, latTo: Double, lonTo: Double) : List<String>{
        val listDistanceEstimated = mutableListOf<String>()
        try {
            val context = GeoApiContext.Builder()
                .apiKey(API_KEY)
                .build()

            val req = DistanceMatrixApi.newRequest(context)
            val origin = LatLng(fromlocation.latitude, fromlocation.longitude)
            val destination = LatLng(latTo, lonTo)

            val trix = req.origins(origin)
                .destinations(destination)
                .mode(TravelMode.DRIVING)
                .await()


            listDistanceEstimated.add(trix.rows[0].elements[0].distance.humanReadable)
            listDistanceEstimated.add(trix.rows[0].elements[0].duration.humanReadable)

            addToFirebase(binding.recognizedText.text.toString(),listDistanceEstimated [0], listDistanceEstimated[1])

        } catch (ex: Exception) {
            Log.e("ETA", ex.message.toString())
        }

        return listDistanceEstimated
    }

    // add written text, distance and estimated time to firebase
    private fun addToFirebase(writtenText : String, distance: String, estimatedTime: String) {
        val item = FirebaseItem(timeStamp = timeStamp, writtenText = writtenText, distance = distance, estimatedTime = estimatedTime)
        database = FirebaseDatabase.getInstance().reference
        database.child(timeStamp).setValue(item).addOnSuccessListener {

            Log.e("Success Add", "Success add to Firebase")

        }.addOnFailureListener{
            Log.e("Failed Add", it.message.toString())
        }

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

    // take picture
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

    // create image file
    private fun createImageFile(): File {
        timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    // add to galery
    private fun galleryAddPic() {
        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
            val f = File(currentPhotoPath)
            mediaScanIntent.data = Uri.fromFile(f)
            requireActivity().sendBroadcast(mediaScanIntent)
        }
    }

    // set picture to container
    private fun setPic() {
        with(binding) {
            val targetW: Int = photoContainer.width
            val targetH: Int = photoContainer.height

            val bmOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true

                BitmapFactory.decodeFile(currentPhotoPath, this)

                val photoW: Int = outWidth
                val photoH: Int = outHeight

                val scaleFactor: Int = 1.coerceAtLeast((photoW / targetW).coerceAtMost(photoH / targetH))

                inJustDecodeBounds = false
                inSampleSize = scaleFactor
                inPurgeable = true
            }
            BitmapFactory.decodeFile(currentPhotoPath, bmOptions)?.also { bitmap ->

                imageBitmap= rotateBitmap(currentPhotoPath, bitmap)
                photoContainer.setImageBitmap(imageBitmap)
            }
        }
    }

    // get text from image
    private fun detectTextFromImage() {
        val image = InputImage.fromBitmap(imageBitmap!!, 0)
         recognizer.process(image)
            .addOnSuccessListener { visionText ->
                binding.recognizedText.text= visionText.text

                //get location
                getLocation()
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