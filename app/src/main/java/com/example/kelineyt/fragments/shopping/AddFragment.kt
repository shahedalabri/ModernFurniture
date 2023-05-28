package com.example.kelineyt.fragments.shopping

import androidx.fragment.app.Fragment
import android.app.Activity
import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.kelineyt.R
import com.example.kelineyt.data.Product
import com.example.kelineyt.databinding.FragmentAddBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID


class AddFragment: Fragment() {

    private lateinit var binding: FragmentAddBinding
    private val selectedColors = mutableListOf<Int>()
    private var selectedImages = mutableListOf<Uri>()
    private val fireStore = Firebase.firestore
    private val storage = FirebaseStorage.getInstance()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAddBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAdd.setOnClickListener {
            val productValidation = validateInformation()
            if (!productValidation) {
                Toast.makeText(requireActivity(), "Check your inputs", Toast.LENGTH_SHORT).show()
            } else {
                saveProducts {
                    if(it) {
                        Toast.makeText(requireActivity(), "Product Add Successfully", Toast.LENGTH_SHORT).show()
                        binding.progressbar.visibility = View.VISIBLE
                        findNavController().popBackStack(R.id.addFragment, false)
                    } else {
                        Toast.makeText(requireActivity(), "Something Wrong!", Toast.LENGTH_SHORT).show()
                        binding.progressbar.visibility = View.VISIBLE
                    }
                }
            }
        }

        binding.buttonColorPicker.setOnClickListener {
            ColorPickerDialog
                .Builder(requireContext())
                .setTitle("Product color")
                .setPositiveButton("Select", object : ColorEnvelopeListener {

                    override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                        envelope?.let {
                            selectedColors.add(it.color)
                            updateColors()
                        }
                    }

                }).setNegativeButton("Cancel") { colorPicker, _ ->
                    colorPicker.dismiss()
                }.show()
        }

        val selectImagesActivityResult =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val intent = result.data

                    //Multiple images selected
                    if (intent?.clipData != null) {
                        val count = intent.clipData?.itemCount ?: 0
                        (0 until count).forEach {
                            val imagesUri = intent.clipData?.getItemAt(it)?.uri
                            imagesUri?.let { image -> selectedImages.add(image) }
                        }

                        //One images was selected
                    } else {
                        val imageUri = intent?.data
                        imageUri?.let { selectedImages.add(it) }
                    }
                    updateImages()
                }
            }
        //6
        binding.buttonImagesPicker.setOnClickListener {
            val intent = Intent(ACTION_GET_CONTENT)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.type = "image/*"
            selectImagesActivityResult.launch(intent)
        }
    }

    //2
    private fun validateInformation(): Boolean {
        if (selectedImages.isEmpty())
            return false
        if (binding.edName.text.toString().trim().isEmpty())
            return false
        if (binding.edCategory.text.toString().trim().isEmpty())
            return false
        if (binding.edPrice.text.toString().trim().isEmpty())
            return false
        return true
    }

    //3
    private fun saveProducts(state: (Boolean) -> Unit) {
        val sizes = getSizesList(binding.edSizes.text.toString().trim())
        val imagesByteArrays = getImagesByteArrays() //7
        val name = binding.edName.text.toString().trim()
        val images = mutableListOf<String>()
        val category = binding.edCategory.text.toString().trim()
        val productDescription = binding.edDescription.text.toString().trim()
        val price = binding.edPrice.text.toString().trim()
        val offerPercentage = binding.edOfferPercentage.text.toString().trim()

        lifecycleScope.launch {
            showLoading()
            try {
                async {
                    Log.d("test1", "test")
                    imagesByteArrays.forEach {
                        val id = UUID.randomUUID().toString()
                        launch {
                            val imagesStorage = storage.reference.child("products/images/$id")
                            val result = imagesStorage.putBytes(it).await()
                            val downloadUrl = result.storage.downloadUrl.await().toString()
                            images.add(downloadUrl)
                        }
                    }
                }.await()
            } catch (e: java.lang.Exception) {
                hideLoading()
                state(false)
            }

            Log.d("test2", "test")

            val product = Product(
                UUID.randomUUID().toString(),
                name,
                category,
                price.toFloat(),
                if (offerPercentage.isEmpty()) null else offerPercentage.toFloat(),
                if (productDescription.isEmpty()) null else productDescription,
                selectedColors,
                sizes,
                images
            )

            fireStore.collection("Products").add(product).addOnSuccessListener {
                state(true)
                hideLoading()
            }.addOnFailureListener {
                Log.e("test2", it.message.toString())
                state(false)
                hideLoading()
            }
        }
    }

    private fun hideLoading() {
        binding.progressbar.visibility = View.INVISIBLE
    }

    private fun showLoading() {
        binding.progressbar.visibility = View.VISIBLE

    }

    private fun getImagesByteArrays(): List<ByteArray> {
        val imagesByteArray = mutableListOf<ByteArray>()
        selectedImages.forEach {
            val stream = ByteArrayOutputStream()
            val imageBmp = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, it)
            if (imageBmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)) {
                val imageAsByteArray = stream.toByteArray()
                imagesByteArray.add(imageAsByteArray)
            }
        }
        return imagesByteArray
    }

    private fun getSizesList(sizes: String): List<String>? {
        if (sizes.isEmpty())
            return null
        val sizesList = sizes.split(",").map { it.trim() }
        return sizesList
    }

    //5
    private fun updateColors() {
        var colors = ""
        selectedColors.forEach {
            colors = "$colors ${Integer.toHexString(it)}, "
        }
        binding.tvSelectedColors.text = colors
    }

    private fun updateImages() {
        binding.tvSelectedImages.text = selectedImages.size.toString()
    }

}