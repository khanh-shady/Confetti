package com.example.confetti;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class Overlay extends AccessibilityService {

    private WindowManager mWindowManager;
    private View mOverlay;
    private TextView questionTV;
    public static TextView resultTV;

    private static final String TAG = Overlay.class.getName();

    private final String RANKING = "https://asia-east2-confetti-faca0.cloudfunctions.net/question/ranking";
    private final String OLD = "https://asia-east2-confetti-faca0.cloudfunctions.net/question/old";
    private String HOST = RANKING;
    public static int posX = 100, posY = 0;

    private int mDebugDepth = 0;
    private boolean isNodeLogTurnedOn = false;
    public static boolean isCloneGotResult = false;
    public static String cloneResult = "";
    public static String lastQuestionNumber = "0", questionNumber = "";
    private String question = "", answer1 = "", answer2 = "", answer3 = "";
    private boolean isNextQuestion = false, isNextAnswer1 = false, isNextAnswer2 = false, isNextAnswer3 = false;

    private String deviceIMEI;
    private final String[] IMEI_MAIN_DEVICES = {"354556102461723", "354652107360810"};
    private final String[] WORDS_TO_AVOID = {"Share", "Comment", "Like", "Playing for Fun", "Friends", "Get Free Lives", "Rewards", "Correct",
    "In the Running", "Free Lives", "You", "All Players in the Running"};

    @Override
    protected void onServiceConnected() {
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!MainActivity.isMainDevice && isCloneGotResult) {
            isCloneGotResult = false;
            resultTV.setText(cloneResult);
        }
        AccessibilityNodeInfo mNodeInfo = event.getSource();
        getQuestion(mNodeInfo);
    }

    private void writeToFile(String data, Context context) {
        File path = context.getExternalFilesDir(null);
        Log.d(TAG, path.getAbsolutePath());
        File file = new File(path, "log.txt");
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(file, true);
            stream.write(data.getBytes());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void getQuestion(AccessibilityNodeInfo mNodeInfo) {
        if (mNodeInfo == null) return;
        String log ="";
        for (int i = 0; i < mDebugDepth; i++) {
            log += ".";
        }
        if (isNodeLogTurnedOn && mNodeInfo.getText() != null) {
            writeToFile(mNodeInfo.getText().toString(), this);
        }
        if (mNodeInfo.getText() != null) {
            String text = mNodeInfo.getText().toString();
            if (text.matches("Question [0-9]+ of 10")) {
                if (Integer.parseInt(lastQuestionNumber) < Integer.parseInt(text.split(" ")[1])) {
                    questionNumber = text.split(" ")[1];
                    lastQuestionNumber = questionNumber;
                    isNextQuestion = true;
                }
            } else if (isNextQuestion && text.contains("?") && !text.equals(text.toUpperCase())) {
                question = text;
                isNextQuestion = false;
                isNextAnswer1 = true;
            } else if (isNextAnswer1 && !text.equals(text.toUpperCase()) && !text.equals("10") && !text.equals(question) && !text.equals("9")
                    && !Arrays.asList(WORDS_TO_AVOID).contains(text) && !text.contains("to earn a free life") && !text.equals("8")
                    && !text.contains("Players in the Running") && !text.contains("of 10") && !text.contains("TODAY'S PRIZE:")) {
                answer1 = text;
                isNextAnswer1 = false;
                isNextAnswer2 = true;
            } else if (isNextAnswer2) {
                answer2 = text;
                isNextAnswer2 = false;
                isNextAnswer3 = true;
            } else if (isNextAnswer3) {
                answer3 = text;
                isNextAnswer3 = false;
                if (questionTV != null)
                    questionTV.setText(questionNumber + ": " + question + "\nAnswer1: " + answer1 + "\nAnswer2: " + answer2 + "\nAnswer3: " + answer3);
                if (MainActivity.isMainDevice) {
                    new CallAPI().execute(HOST, question, answer1, answer2, answer3, questionNumber);
                }
            }
        }
        if (mNodeInfo.getChildCount() < 1) return;
        mDebugDepth++;

        for (int i = 0; i < mNodeInfo.getChildCount(); i++) {
            getQuestion(mNodeInfo.getChild(i));
        }
        mDebugDepth--;
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public void onCreate() {
        super.onCreate();

        questionNumber = "0";
        lastQuestionNumber = "0";
        deviceIMEI = getDeviceIMEI(this);
        if (Arrays.asList(IMEI_MAIN_DEVICES).contains(deviceIMEI)) {
            MainActivity.isMainDevice = true;
        } else {
            MainActivity.isMainDevice = false;
        }
        mOverlay = LayoutInflater.from(this).inflate(R.layout.layout_overlay, null);

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
                HOST = RANKING;
            }
        });
        ImageView rankingButton = (ImageView) mOverlay.findViewById(R.id.ranking_btn);
        rankingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HOST = OLD;
            }
        });
        final ImageView logButton = (ImageView) mOverlay.findViewById(R.id.log_btn);
        logButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isNodeLogTurnedOn = !isNodeLogTurnedOn;
                if (isNodeLogTurnedOn) {
                    logButton.setBackgroundColor(Color.WHITE);
                } else {
                    logButton.setBackgroundColor(Color.BLACK);
                }
            }
        });
        //Add the view to the window
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mOverlay, params);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mOverlay != null) mWindowManager.removeView(mOverlay);
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

    private String getDeviceIMEI(Context context) {
        try {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            String imei = telephonyManager.getDeviceId();
            if (imei != null && !imei.isEmpty()) {
                return imei;
            } else {
                return android.os.Build.SERIAL;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}