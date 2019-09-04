package com.example.confetti;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Calendar;

public class Overlay extends AccessibilityService {

    private WindowManager mWindowManager;
    private View mOverlay;
    private TextView questionTV;
    public static TextView resultTV;
    public static boolean isMainDevice;

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

    private final String RANKING = "https://asia-east2-confetti-faca0.cloudfunctions.net/question/ranking";
    private final String OLD = "https://asia-east2-confetti-faca0.cloudfunctions.net/question/old";
    private String HOST;
    private final String CLONE_IS_SHOW_STARTED = "https://asia-east2-confetti-faca0.cloudfunctions.net/question/isShowStarted";
    private final String CLONE_RESULT = "https://asia-east2-confetti-faca0.cloudfunctions.net/question/result";

    public static boolean isShowStarted = false;
    public static boolean isNewResult = true;
    public static int TIMER = 5000;

    private boolean isDoingOCR = false;
    private boolean isOCREnabled = false;

    private int screenHeight;

    //OCR
    private TessOCR mTessOCR;

    private static final String SCREENCAP_NAME = "screencap";

    public static int posX = 100, posY = 0;

    @Override
    protected void onServiceConnected() {
        initRecorder();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        mOverlay = LayoutInflater.from(this).inflate(R.layout.layout_overlay, null);

        // Get android size
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);
        screenHeight = size.y;

        if (isMainDevice) {
            mTessOCR = new TessOCR(this, "vie");
        }

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

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = 100;

        questionTV = mOverlay.findViewById(R.id.question_and_answers_tv);
        resultTV = mOverlay.findViewById(R.id.result_tv);
        resultTV.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                dispatchClick(posX, posY);
            }
        });
        ImageView screenshotButton = (ImageView) mOverlay.findViewById(R.id.screenshot_btn);
        screenshotButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //enable do OCR
                HOST = RANKING;
                isOCREnabled = true;
            }
        });
        ImageView rankingButton = (ImageView) mOverlay.findViewById(R.id.ranking_btn);
        rankingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //enable do OCR
                HOST = OLD;
                isOCREnabled = true;
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
        if (isMainDevice) {
            new Thread() {
                @Override
                public void run() {
                    Looper.prepare();
                    mHandler = new Handler();
                    Looper.loop();
                }
            }.start();
        } else {
            mHandler = new Handler();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isShowStarted && isTimeForConfetti()) {
                        new CallAPIClone().execute(CLONE_IS_SHOW_STARTED);
                    } else if (isShowStarted && isTimeForConfetti()) {
                        new CallAPIClone().execute(CLONE_RESULT);
                    }
                    mHandler.postDelayed(this, TIMER);
                }
            }, TIMER);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isMainDevice) {
            initRecorder();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isMainDevice) {
            if (mOverlay != null) mWindowManager.removeView(mOverlay);
            stopRecorderAndScreen();
        }
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

                    if (isOCREnabled) {
                        doOCR(bitmap, width, mDisplayHeight);
                    }
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
        if (isDoingOCR) {
            return;
        }
        Log.d(TAG, "SIZE: " + screenHeight);
        final Bitmap question = Bitmap.createBitmap(bitmap, 0, (int) (screenHeight * 0.6), width, (int) (screenHeight * 0.1));
        final Bitmap answer = Bitmap.createBitmap(bitmap, 0, (int) (screenHeight * 0.7), width - 330, height - (int) (screenHeight * 0.75));
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            public void run() {
                isDoingOCR = true;
                final String questionText = mTessOCR.getOCRResult(question);
                final String answerText = mTessOCR.getOCRResult(answer);

                // TODO Auto-generated method stub
                if (questionText != null && !questionText.equals("") && answerText != null && !answerText.equals("") && questionText.contains("?")) {
                    String[] answers = answerText.split("\n");
                    if (answers.length == 3) {
                        String answerNo1 = answers[0].trim();
                        String answerNo2 = answers[1].trim();
                        String answerNo3 = answers[2].trim();
                        questionTV.setText("Question: " + questionText + "\n Answer1: " + answerNo1 + "  Answer2: " + answerNo2 + "  Answer3: " + answerNo3);
                        new CallAPI().execute(HOST, questionText, answerNo1, answerNo2, answerNo3);
                    }
                }
                isDoingOCR = false;
                isOCREnabled = false;
            }
        });
    }

    private GestureDescription createClick(float x, float y) {
        final int DURATION = 1;
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        GestureDescription.StrokeDescription clickStroke = new GestureDescription.StrokeDescription(clickPath, 0, DURATION);
        GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
        clickBuilder.addStroke(clickStroke);
        return clickBuilder.build();
    }

    public void dispatchClick(int x, int y) {
        dispatchGesture(createClick(x, y), null, null);
    }

    private boolean isTimeForConfetti() {
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); //Current hour
        int currentMinute = Calendar.getInstance().get(Calendar.MINUTE); //Current minute
        if (currentHour == 21 && currentMinute > 15 && currentMinute < 40) {
            return true;
        }
        return false;
    }
}