/* Copyright 2016 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.demo;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Trace;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Vector;
import org.tensorflow.Operation;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

/** A classifier specialized to label images using TensorFlow. */
public class TensorFlowSegmentation implements Classifier {
  static {
    System.loadLibrary("tensorflow_demo");
  }

  private static final String TAG = "TFSeg";

  // Only return this many results with at least this confidence.
  private static final int MAX_RESULTS = 3;
  private static final float THRESHOLD = 8.f;

  // Config values.
  private String inputName;
  private String outputName;
  private int inputSize;
  private int imageMean;
  private float imageStd;

  // Pre-allocated buffers. §
  private Vector<String> labels = new Vector<String>();
  private int[] intValues;
  public float[] floatValues;
  public float[] outputs;
  private String[] outputNames;

  private boolean logStats = false;

  private TensorFlowInferenceInterface inferenceInterface;

  private TensorFlowSegmentation() {}

  /**
   * Initializes a native TensorFlow session for classifying images.
   *
   * @param assetManager The asset manager to be used to load assets.
   * @param modelFilename The filepath of the model GraphDef protocol buffer.
   * @param inputSize The input size. A square image of inputSize x inputSize is assumed.
   * @param imageMean The assumed mean of the image values.
   * @param imageStd The assumed std of the image values.
   * @param inputName The label of the image input node.
   * @param outputName The label of the output node.
   * @throws IOException
   */
  public static TensorFlowSegmentation create(
      AssetManager assetManager,
      String modelFilename,
      int inputSize,
      int imageMean,
      float imageStd,
      String inputName,
      String outputName) {
    TensorFlowSegmentation c = new TensorFlowSegmentation();
    c.inputName = inputName;
    c.outputName = outputName;

    c.inferenceInterface = new TensorFlowInferenceInterface(assetManager, modelFilename);

    // The shape of the output is [N, NUM_CLASSES], where N is the batch size.
    final Operation operation = c.inferenceInterface.graphOperation(outputName);
    final int numClasses = 120; //(int) operation.output(0).shape().size(1);
    Log.i(TAG, "Read " + c.labels.size() + " labels, output layer size is " + numClasses);

    // Ideally, inputSize could have been retrieved from the shape of the input operation.  Alas,
    // the placeholder node for input in the graphdef typically used does not specify a shape, so it
    // must be passed in as a parameter.
    c.inputSize = inputSize;
    c.imageMean = imageMean;
    c.imageStd = imageStd;

    // Pre-allocate buffers.
    c.outputNames = new String[] {outputName};
    c.intValues = new int[inputSize * inputSize];
    c.floatValues = new float[inputSize * inputSize * 3];
    c.outputs = new float[inputSize *inputSize];

    return c;
  }

  @Override
  public List<Recognition> recognizeImage(final Bitmap bitmap) {
    // Log this method so that it can be analyzed with systrace.
    Trace.beginSection("recognizeImage");

    Trace.beginSection("preprocessBitmap");
    // Preprocess the image data from 0-255 int to normalized float based
    // on the provided parameters.
      int bmW = bitmap.getWidth();
      int bmH = bitmap.getHeight();

    bitmap.getPixels(intValues, 0, bmW , 0, 0, bmW, bmH);
    final float inv_imageStd = 1.0f/imageStd;
    //From ARGB to RGB floats interleved -1..+1

    final boolean INTERLEVED = false; // false because Segnetinput is (3,input_size,input_size) rather than interleaved (input_size,input_size,3)
    final boolean NO_FEED = false; //For Debug set this to true and the network will be fed with zeros and the the image.

    if (!INTERLEVED && !NO_FEED) {//Seperate to 3 matrix of All R, All G, All B
      final int R = 0; // This is the loop that should run
      final int G = inputSize*inputSize; // G first index
      final int B = inputSize*inputSize*2; // B first index

      for (int i = 0; i < intValues.length; ++i) {
        final int val = intValues[i];
        floatValues[R+i] = (((val >> 16) & 0xFF) - imageMean) * inv_imageStd;
        floatValues[G+i] = (((val >> 8) & 0xFF) - imageMean) * inv_imageStd;
        floatValues[B+i] = ((val & 0xFF) - imageMean) * inv_imageStd;
      }

    } else if (!NO_FEED) {
      for (int i = 0; i < intValues.length; ++i) {
        final int val = intValues[i];
        floatValues[i * 3 + 0] = (((val >> 16) & 0xFF) - imageMean) * inv_imageStd;
        floatValues[i * 3 + 1] = (((val >> 8) & 0xFF) - imageMean) * inv_imageStd;
        floatValues[i * 3 + 2] = ((val & 0xFF) - imageMean) * inv_imageStd;
      }
    }
    Trace.endSection();

    // Copy the input data into TensorFlow.
    float []keep_prob ={1.f};

    Trace.beginSection("feed");
    //inferenceInterface.feed("keep_prob", keep_prob, 1);
    inferenceInterface.feed(inputName, floatValues, 1, 3, inputSize, inputSize);
    Trace.endSection();

    // Run the inference call.
    Trace.beginSection("run");
    inferenceInterface.run(outputNames, logStats);
    Trace.endSection();

    // Copy the output Tensor back into the output array.
    Trace.beginSection("fetch");
    inferenceInterface.fetch(outputName, outputs);
    Trace.endSection();

    Trace.endSection(); // "recognizeImage"
    return null; // Results sits in public member called outputs,
  }

  @Override
  public void enableStatLogging(boolean logStats) {
    this.logStats = logStats;
  }

  @Override
  public String getStatString() {
    return inferenceInterface.getStatString();
  }

  @Override
  public void close() {
    inferenceInterface.close();
  }
}
