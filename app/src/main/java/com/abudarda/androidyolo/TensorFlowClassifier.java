package com.abudarda.androidyolo;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

public class TensorFlowClassifier implements Classifier{
    private static final String TAG = "TensorflowClassifier";

    // Class labels for PASCAL VOC, used because the current model (YOLO v1)
    // has been trained on this detection task.
    private final String[] class_labels =  {"aeroplane", "bicycle", "bird", "boat", "bottle", "bus", "car",
            "cat", "chair", "cow", "diningtable", "dog", "horse", "motorbike", "person",
            "pottedplant", "sheep", "sofa", "train","tvmonitor"};

    // jni native methods.
    public native int initializeTensorFlow(
            AssetManager assetManager,
            String model,
            String labels,
            int numClasses,
            int inputSize,
            int imageMean,
            float imageStd,
            String inputName,
            String outputName);

    private native String classifyImageBmp(Bitmap bitmap);
}
