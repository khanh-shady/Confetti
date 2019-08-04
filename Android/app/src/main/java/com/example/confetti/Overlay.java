package com.example.confetti;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static java.lang.Thread.sleep;

public class Overlay extends Service {

    private WindowManager mWindowManager;
    private View mOverlay;

    private static final String TAG = Overlay.class.getName();
    private static String STORE_DIRECTORY;
    private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    public static MediaProjectionManager mediaProjectionManager;
    private static int IMAGES_PRODUCED;

    private ImageReader mImageReader;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private Handler mHandler;
    private MediaProjectionCallback mediaProjectionCallback;
    private int displayWidth;
    private int mDisplayHeight;
    private int mDensityDpi;
    private WindowManager windowManager;

    //OCR
    private TessOCR mTessOCR;

    private static final String SCREENCAP_NAME = "screencap";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public Overlay() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //Inflate the chat head layout we created
        mOverlay = LayoutInflater.from(this).inflate(R.layout.layout_overlay, null);

        mTessOCR = new TessOCR (this, "vie");
        //Add the view to the window.
        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        //Specify the chat head position
        params.gravity = Gravity.TOP | Gravity.LEFT;        //Initially view will be added to top-left corner
        params.x = 0;
        params.y = 100;

        //Set the close button.
        ImageView closeButton = (ImageView) mOverlay.findViewById(R.id.close_btn);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //close the service and remove the chat head from the window
                stopSelf();
            }
        });
        //Add the view to the window
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mOverlay, params);

        //Handle screen capture
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        mDensityDpi = metrics.densityDpi;
        displayWidth = metrics.widthPixels;
        mDisplayHeight = metrics.heightPixels;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // start capture handling thread
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mHandler = new Handler();
                Looper.loop();
            }
        }.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initRecorder();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOverlay != null) mWindowManager.removeView(mOverlay);
        stopRecorderAndScreen();
    }

    private void initRecorder() {
        try {
            if (mediaProjectionCallback == null){
                mediaProjectionCallback = new MediaProjectionCallback();
            }
            mediaProjection = MainActivity.mediaProjection;
            if (mediaProjection == null){
                return;
            }
            File externalFilesDir = getExternalFilesDir(null);
            System.out.println(externalFilesDir);
            if (externalFilesDir != null) {
                STORE_DIRECTORY = externalFilesDir.getAbsolutePath() + "/screenshots/";
                File storeDirectory = new File(STORE_DIRECTORY);
                if (!storeDirectory.exists()) {
                    boolean success = storeDirectory.mkdirs();
                    if (!success) {
                        return;
                    }
                }
            } else {
                return;
            }

            // create virtual display depending on device width / height
            createVirtualDisplay();

            mediaProjection.registerCallback(mediaProjectionCallback, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void destroyMediaProjection() {
        if (mediaProjection != null) {
            mediaProjection.unregisterCallback(mediaProjectionCallback);
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private void stopRecorderAndScreen() {
        destroyMediaProjection();
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            stopRecorderAndScreen();
        }
    }

    private void createVirtualDisplay() {
        // start capture reader
        mImageReader = ImageReader.newInstance(displayWidth, mDisplayHeight, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(SCREENCAP_NAME, displayWidth, mDisplayHeight, mDensityDpi, VIRTUAL_DISPLAY_FLAGS, mImageReader.getSurface(), null, mHandler);
        mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
    }

    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            Bitmap bitmap = null;

            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * displayWidth;
                    int width = displayWidth + rowPadding / pixelStride;

                    // create bitmap
                    bitmap = Bitmap.createBitmap(width, mDisplayHeight, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    IMAGES_PRODUCED++;

                    doOCR(bitmap, width, mDisplayHeight);
                    sleep(5000);
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (bitmap != null) {
                    bitmap.recycle();
                }

                if (image != null) {
                    image.close();
                }
            }
        }
    }

    private void doOCR (final Bitmap bitmap, int width, int height) {
        final Bitmap question = Bitmap.createBitmap(bitmap, 0, 1400, width, 250);
        final Bitmap answer = Bitmap.createBitmap(bitmap, 0, 1600, width - 300, height - 1600);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                final String questionText = mTessOCR.getOCRResult(question);
                final String answerText = mTessOCR.getOCRResult(answer);

                // TODO Auto-generated method stub
                if (questionText != null && !questionText.equals("") && answerText != null && !answerText.equals("") && questionText.contains("?")) {
                    Log.d(TAG, "Confetti question: " + questionText.trim());
                    Log.d(TAG, "Answers: " + answerText.trim());
                }
            }
        });
    }
}