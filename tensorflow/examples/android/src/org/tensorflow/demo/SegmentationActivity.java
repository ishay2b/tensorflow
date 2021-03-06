/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.demo;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import org.tensorflow.demo.OverlayView.DrawCallback;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;
import org.tensorflow.demo.R;
import org.tensorflow.demo.tracking.SegTracker;


/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class SegmentationActivity extends CameraActivity implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  // Configuration values for the prepackaged multibox model.
  private static final int MB_INPUT_SIZE = 128;
  private static final int MB_IMAGE_MEAN = 128; //Scale from 0..255 to -128..127
  private static final float MB_IMAGE_STD = 128.f;//Scale from -128..127 to -1..+1
  private static final String MB_INPUT_NAME = "input_1";
  private static final String MB_OUTPUT_LOCATIONS_NAME = "activation_16/Sigmoid";
  private static final String MB_MODEL_FILE = "file:///android_asset/hand_model_weights_resnet_binary_128.pb";


  private static final int CROP_SIZE = 128;

  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE = 1.0f;

  private static final boolean MAINTAIN_ASPECT = true;

  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;

  private Integer sensorOrientation;

  private TensorFlowSegmentation detector;

  private int previewWidth = 0;
  private int previewHeight = 0;
  private byte[][] yuvBytes;
  private int[] rgbBytes = null;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;

  private boolean computing = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private Bitmap cropCopyBitmap;

  private byte[] luminance;

  private BorderedText borderedText;

  private long lastProcessingTimeMs;

    final Paint paint= new Paint();

    SegTracker tracker;

  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

      paint.setColor(Color.RED);
      paint.setStyle(Style.STROKE);
      paint.setStrokeWidth(2.0f);

      detector =
            TensorFlowSegmentation.create(
              getAssets(),
              MB_MODEL_FILE,
                    MB_INPUT_SIZE,
              MB_IMAGE_MEAN,
              MB_IMAGE_STD,
              MB_INPUT_NAME,
              MB_OUTPUT_LOCATIONS_NAME);

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    final Display display = getWindowManager().getDefaultDisplay();
    final int screenOrientation = display.getRotation();

    LOGGER.i("Sensor orientation: %d, Screen orientation: %d", rotation, screenOrientation);

    sensorOrientation = rotation + screenOrientation;

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbBytes = new int[previewWidth * previewHeight];
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(CROP_SIZE, CROP_SIZE, Config.ARGB_8888);

    frameToCropTransform =
        ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            CROP_SIZE, CROP_SIZE,
            sensorOrientation, MAINTAIN_ASPECT);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    yuvBytes = new byte[3][];

      tracker = new SegTracker(this, detector.outputs);

      trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
      trackingOverlay.addCallback(
              new DrawCallback() {
                  @Override
                  public void drawCallback(final Canvas canvas) {
                      tracker.draw(canvas);
                      if (isDebug()) {
                          tracker.drawDebug(canvas);
                   }
  /*
                      int z=0;
                      for (int i=0; i<MB_INPUT_SIZE; ++i){
                          for (int j=0; j<MB_INPUT_SIZE; ++j){
                              if (detector.outputs[z]>0.2){
                                  float xy[]=new float[]{(float)i, (float)j};
                                  cropToFrameTransform.mapPoints(xy);
                                  canvas.drawPoint(xy[0], xy[1] , paint);

                              }

                              z += 1;
                          }
                      }
*/
                  }
              });



      addCallback(
        new DrawCallback() {
          @Override
          public void drawCallback(final Canvas canvas) {
            if (!isDebug()) {
              return;
            }
            final Bitmap copy = cropCopyBitmap;
            if (copy == null) {
              return;
            }

            final int backgroundColor = Color.argb(100, 0, 0, 0);
            canvas.drawColor(backgroundColor);

            final Matrix matrix = new Matrix();
            final float scaleFactor = 2;
            matrix.postScale(scaleFactor, scaleFactor);
            matrix.postTranslate(
                canvas.getWidth() - copy.getWidth() * scaleFactor,
                canvas.getHeight() - copy.getHeight() * scaleFactor);
            canvas.drawBitmap(copy, matrix, new Paint());

            final Vector<String> lines = new Vector<String>();
            if (detector != null) {
              final String statString = detector.getStatString();
              final String[] statLines = statString.split("\n");
              for (final String line : statLines) {
                lines.add(line);
              }
            }
            lines.add("");

            lines.add("Frame: " + previewWidth + "x" + previewHeight);
            lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
            lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
            lines.add("Rotation: " + sensorOrientation);
            lines.add("Inference time: " + lastProcessingTimeMs + "ms");

            borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
          }
        });
  }

  OverlayView trackingOverlay;

  @Override
  public void onImageAvailable(final ImageReader reader) {
    Image image = null;

    ++timestamp;
    final long currTimestamp = timestamp;

    try {
      image = reader.acquireLatestImage();

      if (image == null) {
        return;
      }

      Trace.beginSection("imageAvailable");

      final Plane[] planes = image.getPlanes();
      fillBytes(planes, yuvBytes);

        tracker.onFrame(
                previewWidth,
                previewHeight,
                planes[0].getRowStride(),
                sensorOrientation,
                yuvBytes[0],
                timestamp);

      trackingOverlay.postInvalidate();

      // No mutex needed as this method is not reentrant.
      if (computing) {
        image.close();
        return;
      }
      computing = true;

      final int yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();
      ImageUtils.convertYUV420ToARGB8888(
          yuvBytes[0],
          yuvBytes[1],
          yuvBytes[2],
          previewWidth,
          previewHeight,
          yRowStride,
          uvRowStride,
          uvPixelStride,
          rgbBytes);

      image.close();
    } catch (final Exception e) {
      if (image != null) {
        image.close();
      }
      LOGGER.e(e, "Exception!");
      Trace.endSection();
      return;
    }

    rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);


    if (luminance == null) {
      luminance = new byte[yuvBytes[0].length];
    }
    System.arraycopy(yuvBytes[0], 0, luminance, 0, luminance.length);

    runInBackground(
        new Runnable() {
          @Override
          public void run() {
            final long startTime = SystemClock.uptimeMillis();
            //final List<Classifier.Recognition> results =
              detector.recognizeImage(croppedBitmap);
              lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

              // For examining the actual TF input.
              if (SAVE_PREVIEW_BITMAP) {
                  ImageUtils.saveBitmap(croppedBitmap);
                  PyDump.dumpFloatsArray("net_outs.json", detector.outputs);
                  PyDump.dumpFloatsArray("net_ins.json", detector.floatValues);

              }


              cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
            final Canvas canvas = new Canvas(cropCopyBitmap);

              int z=0;
              for (int i=0; i<MB_INPUT_SIZE; ++i){
                  for (int j=0; j<MB_INPUT_SIZE; ++j){
                      if (detector.outputs[z]>0.2){
                          canvas.drawPoint(i, j , paint);
                          //float xy[]=new float[]{(float)i, (float)j};
                          //cropToFrameTransform.mapPoints(xy);

                      }

                      z += 1;
                  }
              }


/*
            for (final Classifier.Recognition result : results) {
              final RectF location = result.getLocation();
              if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE) {
                canvas.drawRect(location, paint);

                cropToFrameTransform.mapRect(location);
                result.setLocation(location);
                mappedRecognitions.add(result);
              }
            }

            //tracker.trackResults(mappedRecognitions, luminance, currTimestamp);
*/
        trackingOverlay.postInvalidate();

            requestRender();
            computing = false;
          }
        });

    Trace.endSection();
  }

  @Override
  protected int getLayoutId() {
    return R.layout.camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  @Override
  public void onSetDebug(final boolean debug) {
    if (null != detector) {
      detector.enableStatLogging(debug);
    }
  }

    @Override
    protected void processImageRGBbytes(int[] rgbBytes) {
        LOGGER.d("RGB");
    }
}
