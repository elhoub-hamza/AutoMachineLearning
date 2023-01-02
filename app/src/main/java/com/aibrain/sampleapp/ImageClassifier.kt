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

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.ml.common.FirebaseMLException
import com.google.firebase.ml.common.modeldownload.FirebaseLocalModel
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceAutoMLImageLabelerOptions
import java.io.IOException
import java.util.Locale

/** Classifies images with ML Kit AutoML.  */
class ImageClassifier
/** Initializes an `ImageClassifier`.  */
@Throws(FirebaseMLException::class)
internal constructor(context: Context) {

  /** MLKit AutoML Image Classifier  */
  private val labeler: FirebaseVisionImageLabeler?

  init {
    FirebaseModelManager.getInstance()
            .registerLocalModel(
                    FirebaseLocalModel.Builder(LOCAL_MODEL_NAME)
                            .setAssetFilePath(LOCAL_MODEL_PATH)
                            .build()
            )

    val options = FirebaseVisionOnDeviceAutoMLImageLabelerOptions.Builder()
            .setConfidenceThreshold(CONFIDENCE_THRESHOLD)
            .setLocalModelName(LOCAL_MODEL_NAME)
            .build()

    labeler = FirebaseVision.getInstance().getOnDeviceAutoMLImageLabeler(options)

    Log.d(TAG, "Created a Firebase ML Kit AutoML Image Labeler.")
  }

  /** Classifies a frame from the preview stream.  */
  internal fun classifyFrame(bitmap: Bitmap): Task<String> {
    if (labeler == null) {
      Log.e(TAG, "Image classifier has not been initialized; Skipped.")
      val e = IllegalStateException("Uninitialized Classifier.")

      val completionSource = TaskCompletionSource<String>()
      completionSource.setException(e)
      return completionSource.task
    }

    val startTime = SystemClock.uptimeMillis()
    val image = FirebaseVisionImage.fromBitmap(bitmap)

    return labeler.processImage(image).continueWith { task ->
      val endTime = SystemClock.uptimeMillis()
      Log.d(TAG, "Time to run model inference: " + java.lang.Long.toString(endTime - startTime))

      val labelProbList = task.result

      // Indicate whether the remote or local model is used.
      // Note: in most common cases, once a remote model is downloaded it will be used. However, in
      // very rare cases, the model itself might not be valid, and thus the local model is used. In
      // addition, since model download failures can be transient, and model download can also be
      // triggered in the background during inference, it is possible that a remote model is used
      // even if the first download fails.


      var textToShow = if (labelProbList.isNullOrEmpty())
        "No Result"
      else
        printTopKLabels(labelProbList)

      // print the results
      textToShow
    }
  }

  /** Closes labeler to release resources.  */
  internal fun close() {
    try {
      labeler?.close()
    } catch (e: IOException) {
      Log.e(TAG, "Unable to close the labeler instance", e)
    }

  }

  /** Prints top-K labels, to be shown in UI as the results.  */
  private val printTopKLabels: (List<FirebaseVisionImageLabel>) -> String = {
    it.joinToString(
            separator = "\n",
            limit = RESULTS_TO_SHOW
    ) { label ->
      String.format(Locale.getDefault(), "Your Image contains %s, with Confidence %.0f%% ", label.text, 100*label.confidence)
    }
  }

  companion object {

    /** Tag for the [Log].  */
    private const val TAG = "MLKitProcess"

    /** Path of local model file stored in Assets.  */
    private const val LOCAL_MODEL_PATH = "automl/manifest.json"

    /** Number of results to show in the UI.  */
    private const val RESULTS_TO_SHOW = 1

    /* CHANGE FROM HERE */
    /** Min probability to classify the given image as belong to a category.  */
    private const val CONFIDENCE_THRESHOLD = 0.1f

    /** Name of the local model file stored in Assets.  */
    private const val LOCAL_MODEL_NAME = "model.tflite"
    /* CHANGE UPTO HERE */
  }
}
