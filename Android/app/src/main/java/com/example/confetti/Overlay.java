package com.example.confetti;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
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

import java.util.Calendar;

public class Overlay extends AccessibilityService {

    private Handler mHandler;
    private WindowManager mWindowManager;
    private View mOverlay;
    private TextView questionTV;
    public static TextView resultTV;

    private static final String TAG = Overlay.class.getName();

    private final String RANKING = "https://asia-east2-confetti-faca0.cloudfunctions.net/question/ranking";
    private final String OLD = "https://asia-east2-confetti-faca0.cloudfunctions.net/question/old";
    private String HOST = RANKING;
    private final String CLONE_IS_SHOW_STARTED = "https://asia-east2-confetti-faca0.cloudfunctions.net/question/isShowStarted";
    private final String CLONE_RESULT = "https://asia-east2-confetti-faca0.cloudfunctions.net/question/result";

    public static boolean isShowStarted = false;
    public static int TIMER = 5000;

    public static int posX = 100, posY = 0;

    public static String lastQuestionNumber = "", questionNumber = "";
    private String question = "", answer1 = "", answer2 = "", answer3 = "";
    private boolean isNextQuestion = false, isNextAnswer1 = false, isNextAnswer2 = false, isNextAnswer3 = false;

    @Override
    protected void onServiceConnected() {
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo mNodeInfo = event.getSource();
        AccessibilityNodeInfo parentNode = getParentNode(mNodeInfo);
        if (parentNode != null) {
            for (int i = 0; i < parentNode.getChildCount(); i++) {
                if (parentNode.getChild(i).getText() != null) {
                    String text = parentNode.getChild(i).getText().toString();
                    if (text.matches("Question [0-9]+ of 10")) {
                        if (!lastQuestionNumber.equals(text.split(" ")[1])) {
                            questionNumber = text.split(" ")[1];
                            lastQuestionNumber = questionNumber;
                            isNextQuestion = true;
                        }
                    } else if (isNextQuestion) {
                        question = text;
                        isNextQuestion = false;
                        isNextAnswer1 = true;
                    }
                } else if (parentNode.getChild(i).getChildCount() == 1) {
                    if (isNextAnswer1) {
                        answer1 = parentNode.getChild(i).getChild(0).getText().toString();
                        isNextAnswer1 = false;
                        isNextAnswer2 = true;
                    } else if (isNextAnswer2) {
                        answer2 = parentNode.getChild(i).getChild(0).getText().toString();
                        isNextAnswer2 = false;
                        isNextAnswer3 = true;
                    } else if (isNextAnswer3) {
                        answer3 = parentNode.getChild(i).getChild(0).getText().toString();
                        isNextAnswer3 = false;
                        if (questionTV != null)
                            questionTV.setText(questionNumber + ": " + question + "\nAnswer1: " + answer1 + "\nAnswer2: " + answer2 + "\nAnswer3: " + answer3);
                        if (MainActivity.isMainDevice) {
                            new CallAPI().execute(HOST, question, answer1, answer2, answer3, questionNumber);
                        }
                    }
                }
            }
        }
    }

    private AccessibilityNodeInfo getParentNode(AccessibilityNodeInfo mNodeInfo) {
        if (mNodeInfo != null && mNodeInfo.getText() != null && mNodeInfo.getText().toString().matches("Question [0-9]+ of 10")) {
            return mNodeInfo.getParent();
        }
        return null;
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
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
                //enable do OCR
                HOST = RANKING;
            }
        });
        ImageView rankingButton = (ImageView) mOverlay.findViewById(R.id.ranking_btn);
        rankingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //enable do OCR
                HOST = OLD;
            }
        });
        //Add the view to the window
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mWindowManager.addView(mOverlay, params);

        Log.d(TAG, "IS MAIN DEVICE: " + MainActivity.isMainDevice);
        if (!MainActivity.isMainDevice) {
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

    private boolean isTimeForConfetti() {
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); //Current hour
        int currentMinute = Calendar.getInstance().get(Calendar.MINUTE); //Current minute
        if (currentHour == 21 && currentMinute > 15 && currentMinute < 40) {
            return true;
        }
        return false;
    }
}