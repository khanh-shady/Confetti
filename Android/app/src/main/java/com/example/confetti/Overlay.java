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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private String lastText1 = "", lastText2 = "", lastText3 = "", lastText4 = "", questionTVText = "";

    private String deviceIMEI;
    private final String[] IMEI_MAIN_DEVICES = {"354556102461723", "354652107360810"};
    private final String[] WORDS_TO_AVOID = {"Share", "Comment", "Like", "Playing for Fun", "Friends", "Get Free Lives", "Rewards", "Correct",
    "In the Running", "Free Lives", "You", "All Players in the Running", "FOLLOWING", "LIVE", "Recorded Live", "Previously Recorded",
    "The prize is no longer available.", "Time's up!", "Play 5 games to earn a free life"};

    private Hashtable<String, String> hash = new Hashtable<>();
    private String[] stopWords = {"ai","alô","amen","bao","bèn","béng","bên","bông","bấy","bển","bệt","bị","bỏ","bỗng","bộ","bớ","bởi",
            "bức","chiếc","choa","chung","chuyển","chuyện","chuẩn","chăng","chơi","chưa","chậc","chắc","chỉ","chỉn","chịu","chốc","chớ",
            "chợt","chủn","chứ","cuộc","càng","các","cách","cái","còn","có","cùng","cũng","cơn","cả","của","cứ","do","duy","dành","dù",
            "dùng","dạ","dẫn","dẫu","gây","gì","ha","hãy","khi","khiến","khoảng","khá","kể","loại","là","làm","lâu","lô","lúc","lượng",
            "lại","lần","lên","mà","mình","mọi","mối","mỗi","mở","nay","ngay","nghen","nghỉm","ngoài","ngoải","ngươi","nhau","nhé","nhiêu",
            "nhóm","như","nhưng","nhất","nhận","nhằm","nhỉ","nhờ","những","nào","này","nên","nó","nóc","nơi","nấy","nếu","nỗi","nọ","nớ",
            "nữa","oái","pho","phè","phía","phóc","phót","phần","phắt","phốc","phụt","phứt","qua","quá","ra","riệt","rày","ráo","rén","rích",
            "rõ","rất","rằng","rồi","rứa","sa sả","sang","so","suýt","sì","sất","sắp","sẽ","sự","thanh","theo","thoạt","thoắt","thuần","thuộc",
            "thà","thêm","thì","thôi","thường","thẩy","thậm","thế","thếch","thỏm","thốc","thốt","thộc","thửa","toẹt","trong","tránh","trả",
            "trển","trệt","trỏng","tuy","tênh","tính","tăng","tại","tạo","tấm","tắp","tọt","tột","tới","từ","từng","tự","veo","việc","và",
            "vài","vào","vâng","vèo","vì","vùng","vượt","vẫn","vậy","về","với","vụt","vừa","xin","xoét","xoẳn","xoẹt","xuể","xuống","xệp",
            "à","ào","á","ái","áng","ít","úi","đang","điểm","đáng","đâu","đây","đã","đó","đưa","đạt","đầy","đặt","đến","đều","để","đủ","ơ",
            "ơi","ấy","ắt","ồ","ổng","ớ","ờ","ở","ủa","ừ","ử"};

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
            writeToFile(log + mNodeInfo.getText().toString() + "\n", this);
        }
        if (mNodeInfo.getText() != null && !mNodeInfo.getText().toString().matches("[-0-9]+:[-0-9]+")
                && !Arrays.asList(WORDS_TO_AVOID).contains(mNodeInfo.getText().toString())) {
            String text = mNodeInfo.getText().toString();

            // Fallback method to get texts
            if (lastText1 == "") lastText1 = text;
            else if (lastText2 == "") lastText2 = text;
            else if (lastText3 == "") lastText3 = text;
            else if (lastText4 == "") lastText4 = text;
            else {
                lastText1 = lastText2;
                lastText2 = lastText3;
                lastText3 = lastText4;
                lastText4 = text;
            }

            if (text.matches("Question [0-9]+ of 10")) {
                if (Integer.parseInt(lastQuestionNumber) < Integer.parseInt(text.split(" ")[1]) &&
                    lastText4.contains("?") && !lastText4.equals(lastText4.toUpperCase()) &&
                    !Arrays.asList(WORDS_TO_AVOID).contains(lastText1) && !Arrays.asList(WORDS_TO_AVOID).contains(lastText2) &&
                    !Arrays.asList(WORDS_TO_AVOID).contains(lastText3)) {
                    if (questionTV != null)
                        questionTV.setText(questionNumber + ": " + question + "\nAnswer1: " + answer1 + "\nAnswer2: " + answer2 + "\nAnswer3: " + answer3);
                    if (MainActivity.isMainDevice && questionTV.getText() != questionTVText) {
                        questionTVText = questionTV.getText().toString();
                        new CallAPI().execute(HOST, question, answer1, answer2, answer3, questionNumber);
                    }
                } else if (Integer.parseInt(lastQuestionNumber) < Integer.parseInt(text.split(" ")[1])) {
                    questionNumber = text.split(" ")[1];
                    lastQuestionNumber = questionNumber;
                    isNextQuestion = true;
                }
            } else if (isNextQuestion && text.contains("?") && !text.equals(text.toUpperCase())) {
                question = text;
                isNextQuestion = false;
                isNextAnswer1 = true;
            } else if (isNextAnswer1 && !text.equals("10") && !text.equals(question) && !text.equals("9")
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
                if (MainActivity.isMainDevice && questionTV.getText() != questionTVText) {
                    questionTVText = questionTV.getText().toString();
                    new CallAPI().execute(HOST, standardizeQuestion(question), answer1, answer2, answer3, questionNumber);
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

        String a = "Đâu là tên một loài chim?";
        Log.d("QUESTION AFTED STANDARDIZED: ", standardizeQuestion(a));

        deviceIMEI = getDeviceIMEI(this);
        if (Arrays.asList(IMEI_MAIN_DEVICES).contains(deviceIMEI)) {
            MainActivity.isMainDevice = true;
            initDictionary();
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

    private void initDictionary() {
        try {
            File path = this.getExternalFilesDir(null);
            File file = new File(path, "dictionary.txt");
            Log.d("AJSDJQJDIOQJDIOQWD", "CÓ VẺ CÓ");
            FileInputStream inputStreamReader = new FileInputStream(file);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStreamReader));
            String receiveString = "";

            while ((receiveString = bufferedReader.readLine()) != null) {
                hash.put(receiveString, receiveString);
            }
            bufferedReader.close();
        }
        catch (FileNotFoundException e) {
            Log.e("AJSDJQJDIOQJDIOQWD", "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e("AJSDJQJDIOQJDIOQWD", "Can not read file: " + e.toString());
        }
    }

    private String standardizeQuestion(String question) {
        String q = "";
        Pattern pattern = Pattern.compile("([^\"]+|\"[^\"]+\")+(?=\\?)");
        Matcher matcher = pattern.matcher(question);
        List<String> s = new ArrayList<>();
        while (matcher.find()) {
            String temp = matcher.group(0).trim();
            if (temp.length() == 0 || temp.equals("?")) {
                continue;
            }
            s.add(temp);
        }
        for (int i = 0; i < s.size(); i++) {
            if (s.get(i).charAt(0) == '"' || (s.get(i).charAt(0) == '?' && s.get(i).length() == 1)) {
                q += s.get(i) + " ";
                continue;
            }
            if (hash.get(s.get(i).toLowerCase()) != null) {
                q += s.get(i) + " ";
                continue;
            }
            String[] temp = s.get(i).split(" ");
            System.out.println(Arrays.asList(temp));
            int tempIndex = 0;
            while (tempIndex < temp.length - 1) {
                for (int j = temp.length - 1; j > tempIndex; j--) {
                    int index = s.get(i).lastIndexOf(temp[j]);
                    if (index > 0) {
                        String word = s.get(i).substring(0, index).trim();
                        if (hash.get(word.toLowerCase()) != null) {
                            tempIndex = j;
                            q += word + " ";
                            break;
                        }
                    }
                    if (j == tempIndex) {
                        tempIndex++;
                        if ((!Arrays.asList(stopWords).contains(temp[j].toLowerCase()) && i == 0 && j == 0)
                            || !Arrays.asList(stopWords).contains(temp[j])) {
                            q += temp[j] + " ";
                        }
                    }
                }
            }
        }
        Log.d("QUESTION AFTER STANDARDIZED: ", q);
        return q.trim();
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