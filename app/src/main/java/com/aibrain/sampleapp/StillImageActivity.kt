/**
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aibrain.sampleapp

import android.app.Activity
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider

import com.google.firebase.ml.common.FirebaseMLException
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

class StillImageActivity : BaseActivity() {

  private var currentPhotoFile: File? = null
  private var imagePreview: ImageView? = null
  private var textView: TextView? = null

  private var classifier: ImageClassifier? = null
  private var currentImageIndex = 0
  private var bundledImageList: Array<String>? = null

  var temporaryResult: String? = null
  var player : MediaPlayer? = null;

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_still_image)
    imagePreview = findViewById(R.id.image_preview)
    textView = findViewById(R.id.result_text)
    findViewById<ImageButton>(R.id.photo_camera_button)?.setOnClickListener { takePhoto() }
    findViewById<ImageButton>(R.id.photo_library_button)?.setOnClickListener { chooseFromLibrary() }
    findViewById<Button>(R.id.next_image_button)?.setOnClickListener { clickNextImage() }

    // Get list of bundled images.
    bundledImageList = resources.getStringArray(R.array.image_name_array)

    // Setup image classifier.
    try {
      classifier = ImageClassifier(this)
    } catch (e: FirebaseMLException) {
      textView?.text = getString(R.string.fail_to_initialize_img_classifier)
    }

    // Classify the first image in the bundled list.
    classifyBundledImage(currentImageIndex)
  }

  override fun onDestroy() {
    classifier?.close()
    super.onDestroy()
  }

  /** Create a file to pass to camera app */
  @Throws(IOException::class)
  private fun createImageFile(): File {
    // Create an image file name
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
    val storageDir = cacheDir
    return createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
    ).apply {
      // Save a file: path for use with ACTION_VIEW intents.
      currentPhotoFile = this
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (resultCode != Activity.RESULT_OK) return

    when (requestCode) {
      // Make use of FirebaseVisionImage.fromFilePath to take into account
      // Exif Orientation of the image files.
      REQUEST_IMAGE_CAPTURE -> {
        FirebaseVisionImage.fromFilePath(this, Uri.fromFile(currentPhotoFile)).also {
          classifyImage(it.bitmap)
        }
      }
      REQUEST_PHOTO_LIBRARY -> {
        val selectedImageUri = data?.data ?: return
        FirebaseVisionImage.fromFilePath(this, selectedImageUri).also {
          classifyImage(it.bitmap)
        }
      }
    }
  }

  /** Run image classification on the given [Bitmap] */
  private fun classifyImage(bitmap: Bitmap) {
    if (classifier == null) {
      textView?.text = getString(R.string.uninitialized_img_classifier_or_invalid_context)
      return
    }

    // Show image on screen.
    imagePreview?.setImageBitmap(bitmap)

    // Classify image.
    classifier?.classifyFrame(bitmap)?.
      addOnCompleteListener { task ->
        if (task.isSuccessful) {
          temporaryResult = task.result
          val labels: List<String> = listOf("Cat", "Dog", "Horse", "Spider", "Butterfly",
            "Chicken", "Sheep", "Cow", "Squirrel", "Elephant")

          //var result: String
          var audioPath: String? = null
          for (label in labels)
          {
            var isFound: Boolean = temporaryResult!!.contains(label)


            //if (the image is classified),
            if (isFound)
              {
                //Then change the text in textView to the result
                textView?.text = task.result

                //When (label is something)
                when(label)
                {
                  //label is dog, then set audio path to the path of dog.mp3
                  "Dog" -> audioPath = "audio/dog.mp3"

                  //label is cat, then set audio path to the path of cat.mp3
                  "Cat" -> audioPath = "audio/cat.mp3"

                  //..so on
                  "Horse" -> audioPath = "audio/horse.mp3"
                  "Spider" -> audioPath = "audio/minecraftSpider.mp3"
                  "Butterfly" -> audioPath = "audio/tigerbutterfly_short.mp3"
                  "Chicken" -> audioPath = "audio/chicken.mp3"
                  "Sheep" -> audioPath = "audio/sheep.mp3"
                  "Cow" -> audioPath = "audio/cow.mp3"
                  "Squirrel" -> audioPath = "audio/yeehaw.mp3"
                  "Elephant" -> audioPath = "audio/elephant.mp3"
                }
                break

            } else
              {

                //if the image is not classified, still show the result (Error)
              textView?.text = task.result
              }
          }


          //Play audio of animal sound when the image is classified
          var afd: AssetFileDescriptor? = null
          var am: AssetManager = resources.assets


          if (audioPath != null)
          {
            try {
              afd = am.openFd(audioPath)
              player = MediaPlayer()
              player!!.setDataSource(afd)
              player!!.prepare()
              player!!.start()

              player!!.setOnCompletionListener {
                player!!.release()
              }
            } catch (e: IOException)
            {
              e.printStackTrace()
            }
          }


        } else {
          val e = task.exception
          Log.e(TAG, "Error classifying frame", e)
          textView?.text = e?.message
        }
      }
  }

  private fun chooseFromLibrary() {
    val intent = Intent(Intent.ACTION_PICK)
    intent.type = "image/*"

    // Only accept JPEG or PNG format.
    val mimeTypes = arrayOf("image/jpeg", "image/png")
    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)

    startActivityForResult(intent, REQUEST_PHOTO_LIBRARY)
  }

  private fun takePhoto() {
    Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
      // Ensure that there's a camera activity to handle the intent.
      takePictureIntent.resolveActivity(packageManager)?.also {
        // Create the File where the photo should go.
        val photoFile: File? = try {
          createImageFile()
        } catch (e: IOException) {
          // Error occurred while creating the File.
          Log.e(TAG, "Unable to save image to run classification.", e)
          null
        }
        // Continue only if the File was successfully created.
        photoFile?.also {
          val photoURI: Uri = FileProvider.getUriForFile(
            this,
            "com.aibrain.sampleapp.fileprovider",
            it
          )
          takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
          startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
      }
    }
  }

  private fun clickNextImage() {
    if (player != null)
    {
      player!!.release()
    }
    val imageList = bundledImageList
    if (imageList.isNullOrEmpty()) { return }

    currentImageIndex = (currentImageIndex + 1) % imageList.size
    classifyBundledImage(currentImageIndex)
  }

  private fun classifyBundledImage(index: Int) {
    val imageList = bundledImageList
    if (imageList.isNullOrEmpty()) { return }

    val imageName = imageList[index]
    val drawableId = resources.getIdentifier(imageName, "drawable", packageName)
    val bitmap = BitmapFactory.decodeResource(resources, drawableId)

    classifyImage(bitmap)
  }

  //this function will make the animal sound
  private fun animalSound(label: String)
  {


  }



  companion object {

    /** Tag for the [Log].  */
    private const val TAG = "StillImageActivity"

    /** Request code for starting photo capture activity  */
    private const val REQUEST_IMAGE_CAPTURE = 1

    /** Request code for starting photo library activity  */
    private const val REQUEST_PHOTO_LIBRARY = 2

  }
}
