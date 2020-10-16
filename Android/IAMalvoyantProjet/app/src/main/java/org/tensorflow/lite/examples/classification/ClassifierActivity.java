/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
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

package org.tensorflow.lite.examples.classification;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Calendar;
import java.util.Set;
import java.util.Vector;

import org.tensorflow.lite.examples.classification.env.BorderedText;
import org.tensorflow.lite.examples.classification.env.ImageUtils;
import org.tensorflow.lite.examples.classification.env.Logger;
import org.tensorflow.lite.examples.classification.tflite.Classifier;
import org.tensorflow.lite.examples.classification.tflite.Classifier.Device;
import org.tensorflow.lite.examples.classification.tflite.Classifier.Model;
import org.tensorflow.lite.examples.classification.tflite.ClassifierFloatMobileNet;
import org.tensorflow.lite.examples.classification.utils.EuclidianCalculator;
import org.tensorflow.lite.examples.classification.utils.InstanceVector;
import org.tensorflow.lite.examples.classification.utils.Speaker;

import javax.crypto.interfaces.PBEKey;

import androidx.fragment.app.DialogFragment;

public class ClassifierActivity extends CameraActivity implements OnImageAvailableListener, View.OnClickListener {
  private static final Logger LOGGER = new Logger();
  private static final boolean MAINTAIN_ASPECT = true;
  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
  private static final float TEXT_SIZE_DIP = 10;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;
  private long lastProcessingTimeMs;
  private Integer sensorOrientation;
  private Classifier classifier;
  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;
  private BorderedText borderedText;

  private  String trainclassname;
  private boolean trainingstarted=false;
  int nbframetrain=0;
  private int nbframetraindelay=60;
  private int nbframemax=300;
  private int trainok;

  //trying to vocalize
  private Speaker speaker;
  private long lastRecognition = 0;
  private long lastRecoTime = 0;

  //Name of every class
  private List<String> classes = new ArrayList<>();
  //Every instance of class currently known
  private List<InstanceVector> instances = new ArrayList<>();
  //Every custom instance
  private Set<InstanceVector> customInstances = new HashSet<>();
  //New instance vector being create
  private InstanceVector currentNewInstanceVector;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    speaker = new Speaker(this);

    takePictureButton.setOnClickListener(this);

    File savedInstance = new File(getFilesDir(),"instances");

    try {
      FileInputStream fileInputStream = new FileInputStream(savedInstance);
      ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);

      customInstances = (Set<InstanceVector>) objectInputStream.readObject();

      for(InstanceVector iv : customInstances)
        instances.add(iv);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch(EOFException e){
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
  }

  @Override
  protected int getLayoutId() {
    nbframetrain=0;
    trainingstarted=false;


    return R.layout.camera_connection_fragment;

  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    recreateClassifier(getModel(), getDevice(), getNumThreads());

    if (classifier == null) {
      LOGGER.e("No classifier on preview!");
      return;
    }


    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    sensorOrientation = rotation - getScreenOrientation();
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap =
            Bitmap.createBitmap(
                    classifier.getImageSizeX(), classifier.getImageSizeY(), Config.ARGB_8888);

    frameToCropTransform =
            ImageUtils.getTransformationMatrix(
                    previewWidth,
                    previewHeight,
                    classifier.getImageSizeX(),
                    classifier.getImageSizeY(),
                    sensorOrientation,
                    MAINTAIN_ASPECT);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    //Used to initialize the indexArray (which contains every class of imagenet, to build the characteristic vector)
    List<Classifier.Recognition> results = classifier.recognizeImage(croppedBitmap);
    for(Classifier.Recognition reco : results)
      classes.add(reco.getTitle().toLowerCase());
    Collections.sort(classes);

    //Also initialize the instances List
    for(int i=0; i < classes.size(); i++){
      InstanceVector inst = new InstanceVector(classes.get(i));
      inst.setVectorValue(i,1d);
      instances.add(inst);
    }
  }

  @Override
  protected void processImage() {

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);


    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);

    if(this.mViewMode==VIEW_SAVE_IMAGE) {

      if (trainingstarted == false) {
          trainok = 0;
          nbframetrain = 0;
          Intent intent = new Intent(this, TrainActivity.class);
          startActivityForResult(intent, 0);
          Log.e(this.getLocalClassName(), "Lance l'activité train");

      } else {
        Log.e(this.getLocalClassName(), "Commence la sauvegarde");

        if (nbframetrain > nbframetraindelay && nbframetrain < (nbframemax + nbframetraindelay)) {
          Log.e(this.getLocalClassName(), "ça sauvergarde !!!");
          trainok = 1;

          String filename = dir.getAbsolutePath() + "/";
          String date = "" + new java.util.Date().getTime();
          new File(filename + "/"+trainclassname).mkdirs();
          filename = filename + "/"+trainclassname+"/" + trainclassname + "-" + date + ".png";
          Log.e(this.getLocalClassName(), filename.toString());
          try (FileOutputStream out = new FileOutputStream(filename)) {
            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
            // PNG is a lossless format, the compression factor (100) is ignored
            Log.e(this.getLocalClassName(), "ok");
          } catch (IOException e) {
            e.printStackTrace();
          }

          runInBackground(
                  new Runnable() {
                    @Override
                    public void run() {

                      runOnUiThread(
                              new Runnable() {
                                @Override
                                public void run() {

                                  showDebugInfo("Saving to images :  ON");
                                  toolbar.setTitle("Saving to images :  ON");
                                }
                              });
                    }

                  });
        }
        nbframetrain++;
        readyForNextImage();

        if(nbframetrain>=nbframemax + nbframetraindelay){
          trainingstarted=false;
          mViewMode=VIEW_NOTHING;
          runInBackground(
                  new Runnable() {
                    @Override
                    public void run() {

                      runOnUiThread(
                              new Runnable() {
                                @Override
                                public void run() {

                                  showDebugInfo("Saving to images :  OFF");
                                  toolbar.setTitle("Saving to images :  OFF");
                                }
                              });
                    }

                  });

        }

      }
    }



    if(this.mViewMode==VIEW_ClASSIFY) {

      runInBackground(
              new Runnable() {
                @Override
                public void run() {
                  if (classifier != null) {
                    final long startTime = SystemClock.uptimeMillis();
                    final List<Classifier.Recognition> results = classifier.recognizeImage(croppedBitmap);
                    lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                    LOGGER.v("Detect: %s", results);
                    cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);

                    runOnUiThread(
                            new Runnable() {
                              @Override
                              public void run() {

                                String className;

                                showFrameInfo(previewWidth + "x" + previewHeight);
                                showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                showCameraResolution(canvas.getWidth() + "x" + canvas.getHeight());
                                showRotationInfo(String.valueOf(sensorOrientation));
                                showInference(lastProcessingTimeMs + "ms");


                                long currentTime = Calendar.getInstance().getTimeInMillis();
                                long diffTime = currentTime - lastRecognition;
                                long diffTimeSpeak = currentTime - lastRecoTime;
                                if(diffTime > 1000L){

                                  lastRecognition = currentTime;

                                  //Build an InstanceVector
                                  InstanceVector instanceVector = new InstanceVector("unknown");

                                  int i = 0;
                                  while(results.get(i).getConfidence() > 0d){
                                    instanceVector.setVectorValue(classes.indexOf(results.get(i).getTitle().toLowerCase()),
                                            (double) results.get(i).getConfidence());
                                    i++;
                                  }
                                  String instanceName = one_PPV(instanceVector);
                                  if(!instanceName.isEmpty())
                                    instanceVector.setInstanceName(instanceName);

                                  Classifier.Recognition reco = new Classifier.Recognition(instanceName, instanceName, 1f, null);
                                  results.add(0, reco);

                                  showResultsInBottomSheet(results);

                                  if(diffTimeSpeak > 5000L){
                                    lastRecoTime = currentTime;
                                    //Trying to vocalize here
                                    className = instanceVector.getInstanceName();
                                    speaker.speak(className);
                                  }

                                }

                              }
                            });
                  }
                }
              });
      readyForNextImage();

    }


    if(this.mViewMode==VIEW_NOTHING) {

      runInBackground(
              new Runnable() {
                @Override
                public void run() {
                  if (classifier != null) {
                    final long startTime = SystemClock.uptimeMillis();
                    lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                    //LOGGER.v("Detect: %s", results);
                    cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);

                    runOnUiThread(
                            new Runnable() {
                              @Override
                              public void run() {
                                //showResultsInBottomSheet(results);
                                showFrameInfo(previewWidth + "x" + previewHeight);
                                showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                showCameraResolution(canvas.getWidth() + "x" + canvas.getHeight());
                                showRotationInfo(String.valueOf(sensorOrientation));
                                showInference(lastProcessingTimeMs + "ms");
                              }
                            });
                  }
                }
              });
      readyForNextImage();

    }


  }


  @Override
  public void onActivityResult(int requesCode, int returnCode, Intent data) {
    if(requesCode==0){


      this.trainclassname=data.getStringExtra("name");
      this.trainingstarted=true;
      mViewMode=VIEW_SAVE_IMAGE;
      Log.e(this.getLocalClassName(),"retrour de l'activity");
      readyForNextImage();

    }
  }

  @Override
  protected void onInferenceConfigurationChanged() {
    if (croppedBitmap == null) {
      // Defer creation until we're getting camera frames.
      return;
    }
    Log.e(this.getLocalClassName(),"Changement de modele");
    final Device device = getDevice();
    final Model model = getModel();
    final int numThreads = getNumThreads();
    runInBackground(() -> recreateClassifier(model, device, numThreads));
  }

  private void recreateClassifier(Model model, Device device, int numThreads) {
    if (classifier != null) {
      LOGGER.d("Closing classifier.");
      classifier.close();
      classifier = null;
    }
    if (device == Device.GPU && model == Model.QUANTIZED) {
      LOGGER.d("Not creating classifier: GPU doesn't support quantized models.");
      runOnUiThread(
              () -> {
                Toast.makeText(this, "GPU does not yet supported quantized models.", Toast.LENGTH_LONG)
                        .show();
              });
      return;
    }
    try {
      LOGGER.d(
              "Creating classifier (model=%s, device=%s, numThreads=%d)", model, device, numThreads);
      classifier = Classifier.create(this, model, device, numThreads);
    } catch (IOException e) {
      LOGGER.e(e, "Failed to create classifier.");
    }
  }

  private String one_PPV(InstanceVector unknownInstance){
    String instanceClass = "";
    double minDistance = Double.MAX_VALUE;

    for(InstanceVector instance : instances){
        double currentDistance = EuclidianCalculator.euclidianDistance(unknownInstance.getVector(),instance.getVector());
        if(currentDistance < minDistance){
          minDistance = currentDistance;
          instanceClass = instance.getInstanceName();
        }
    }

    return instanceClass;
  }

    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.takePictureButton && classifier != null){
            final List<Classifier.Recognition> results = classifier.recognizeImage(croppedBitmap);

            //Build an InstanceVector
            InstanceVector instanceVector = new InstanceVector("unknown");

            int i = 0;
            while(results.get(i).getConfidence() > 0d){
              instanceVector.setVectorValue(classes.indexOf(results.get(i).getTitle().toLowerCase()),
                      (double) results.get(i).getConfidence());
              i++;
            }

            currentNewInstanceVector = instanceVector;

            InstanceNameFragment frag = new InstanceNameFragment();
            frag.show(getSupportFragmentManager(),"instanceName");
        }
    }

    public void doPositiveClick(String instanceName){
      InstanceVector instanceVector = currentNewInstanceVector;
      instanceVector.setInstanceName(instanceName);

      instances.add(instanceVector);
      customInstances.add(instanceVector);

      File savedInstance = new File(getFilesDir(),"instances");
      try {
        FileOutputStream fileOutputStream = new FileOutputStream(savedInstance);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);

        objectOutputStream.writeObject(customInstances);

        objectOutputStream.close();
        fileOutputStream.close();

        Toast.makeText(this,"Instance " + instanceName + " added to database",Toast.LENGTH_SHORT).show();
      } catch (FileNotFoundException e) {
        e.printStackTrace();
        Toast.makeText(this,"There was an error during instance saving",Toast.LENGTH_SHORT).show();
      } catch (IOException e) {
        e.printStackTrace();
        Toast.makeText(this,"There was an error during instance saving",Toast.LENGTH_SHORT).show();
      }
    }

}