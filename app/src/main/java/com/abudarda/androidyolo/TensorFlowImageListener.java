package com.abudarda.androidyolo;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Trace;

import org.junit.Assert;

import java.util.List;
import java.util.logging.Logger;

public class TensorFlowImageListener implements ImageReader.OnImageAvailableListener {
    //private static final Logger LOGGER = new Logger();

    private static final boolean SAVE_PREVIEW_BITMAP = true;

    private static final int NUM_CLASSES = 1470;
    private static final int INPUT_SIZE = 448;
    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128;
    private static final String INPUT_NAME = "Placeholder";
    private static final String OUTPUT_NAME = "19_fc";

    private static final String MODEL_FILE = "file:///android_asset/android_graph.pb";
    private static final String LABEL_FILE =
            "file:///android_asset/label_strings.txt";

    private Integer sensorOrientation;

    private final TensorFlowClassifier tensorflow = new TensorFlowClassifier();

    private int previewWidth = 0;
    private int previewHeight = 0;
    private byte[][] yuvBytes;
    private int[] rgbBytes = null;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;

    private boolean computing = false;
    private boolean readyForNextImage = true;
    private Handler handler;

    private RecognitionScoreView scoreView;
    private BoundingBoxView boundingView;

    public void initialize(
            final AssetManager assetManager,
            final RecognitionScoreView scoreView,
            final BoundingBoxView boundingView,
            final Handler handler,
            final Integer sensorOrientation) {
        Assert.assertNotNull(sensorOrientation);
        tensorflow.initializeTensorFlow(
                assetManager, MODEL_FILE, LABEL_FILE, NUM_CLASSES, INPUT_SIZE, IMAGE_MEAN, IMAGE_STD,
                INPUT_NAME, OUTPUT_NAME);
        this.scoreView = scoreView;
        this.boundingView = boundingView;
        this.handler = handler;
        this.sensorOrientation = sensorOrientation;
    }

    private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {
        Assert.assertEquals(dst.getWidth(), dst.getHeight());
        final float minDim = Math.min(src.getWidth(), src.getHeight());

        final Matrix matrix = new Matrix();

        // We only want the center square out of the original rectangle.
        final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
        final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
        matrix.preTranslate(translateX, translateY);

        final float scaleFactor = dst.getHeight() / minDim;
        matrix.postScale(scaleFactor, scaleFactor);

        // Rotate around the center if necessary.
        // Nataniel: added rotation because image is rotated on my device (Pixel C tablet)
        // TODO: Find out if this is happenning in every device.
        sensorOrientation = 90;
        if (sensorOrientation != 0) {
            matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
            matrix.postRotate(sensorOrientation);
            matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
        }
        //LOGGER.i("sensorOrientationImageListener" + sensorOrientation.toString());

        final Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, matrix, null);
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            // No mutex needed as this method is not reentrant.
            if (computing || !readyForNextImage) {
                image.close();
                return;
            }
            readyForNextImage = true;
            computing = true;

            Trace.beginSection("imageAvailable");

            final Image.Plane[] planes = image.getPlanes();

            // Initialize the storage bitmaps once when the resolution is known.
            if (previewWidth != image.getWidth() || previewHeight != image.getHeight()) {
                previewWidth = image.getWidth();
                previewHeight = image.getHeight();

//                LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
                rgbBytes = new int[previewWidth * previewHeight];
                rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
                croppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);

                yuvBytes = new byte[planes.length][];
                for (int i = 0; i < planes.length; ++i) {
                    yuvBytes[i] = new byte[planes[i].getBuffer().capacity()];
                }
            }

            for (int i = 0; i < planes.length; ++i) {
                planes[i].getBuffer().get(yuvBytes[i]);
            }

            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0],
                    yuvBytes[1],
                    yuvBytes[2],
                    rgbBytes,
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    false);

            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
//            LOGGER.e(e, "Exception!");
            Trace.endSection();
            return;
        }

        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
        drawResizedBitmap(rgbFrameBitmap, croppedBitmap);

        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        handler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        final List<Classifier.Recognition> results = tensorflow.recognizeImage(croppedBitmap);

//                        LOGGER.v("%d results", results.size());
                        for (final Classifier.Recognition result : results) {
//                            LOGGER.v("Result: " + result.getTitle());
                        }
                        scoreView.setResults(results);
                        boundingView.setResults(results);

                        computing = false;
                    }
                });

        Trace.endSection();
    }

    public void takePic() {
        readyForNextImage = true;
//        LOGGER.v("Taking picture");
        return;
    }

    public static int getInputSize() {
        return INPUT_SIZE;
    }

}
