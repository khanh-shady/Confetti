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
    public static String questionNumber = "";
    private String question = "", answer1 = "", answer2 = "", answer3 = "";
    private String lastText1 = "", lastText2 = "", lastText3 = "", lastText4 = "", lastText5 = "", questionTVText = "";

    private String deviceIMEI;
    private final String[] IMEI_MAIN_DEVICES = {"354556102461723", "354652107360810"};

    private Hashtable<String, String> hash = new Hashtable<>();
    private String[] stopWords = {"ai","alô","amen","bao","bèn","béng","bên","bông","bấy","bển","bệt","bị","bỏ","bỗng","bộ","bớ","bởi",
            "bức","cần","chiếc","choa","chung","chuyển","chuyện","chuẩn","chăng","chơi","chưa","chậc","chắc","chỉ","chỉn","chịu","chốc","chớ",
            "chợt","chủn","chứ","cuộc","càng","các","cách","cái","còn","có","cùng","cũng","cơn","cả","của","cứ","do","duy","dành","dù",
            "dùng","dạ","dẫn","dẫu","gây","gì","ha","hãy","khi","khiến","khoảng","khá","kể","loại","là","làm","lâu","lô","lúc","lượng",
            "lại","lần","lên","mà","mình","mọi","mối","mỗi","mở","nay","ngay","nghen","nghỉm","ngoài","ngoải","ngươi","nhau","nhé","nhiêu",
            "nhóm","như","nhưng","nhằm","nhỉ","nhờ","những","nào","này","nên","nó","nóc","nơi","nấy","nếu","nỗi","nọ","nớ",
            "nữa","oái","pho","phè","phía","phóc","phót","phần","phắt","phốc","phụt","phứt","qua","quá","ra","riệt","rày","ráo","rén","rích",
            "rõ","rất","rằng","rồi","rứa","sa sả","sang","so","suýt","sì","sất","sắp","sẽ","sự","thanh","theo","thoạt","thoắt","thuần","thuộc",
            "thà","thêm","thì","thôi","thường","thẩy","thậm","thế","thếch","thỏm","thốc","thốt","thộc","thửa","toẹt","trong","tránh","trả",
            "trển","trệt","trỏng","tuy","tênh","tính","tăng","tại","tạo","tấm","tắp","tọt","tột","tới","từ","từng","tự","veo","việc","và",
            "vài","vào","vâng","vèo","vì","vùng","vượt","vẫn","vậy","về","với","vụt","vừa","xin","xoét","xoẳn","xoẹt","xuể","xuống","xệp",
            "à","ào","á","ái","áng","ít","úi","đang","điểm","đáng","đâu","đây","đã","đó","đưa","đạt","đầy","đặt","đến","đều","để","đủ","ơ",
            "ơi","ấy","ắt","ồ","ổng","ớ","ờ","ở","ủa","ừ","ử","bao nhiêu","sau đây","dưới đây","như sau","là vì","vì sao"};

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
        if (mNodeInfo.getText() != null) {
            String text = mNodeInfo.getText().toString();

            if (lastText1.equals("")) lastText1 = text;
            else if (lastText2.equals("")) lastText2 = text;
            else if (lastText3.equals("")) lastText3 = text;
            else if (lastText4.equals("")) lastText4 = text;
            else if (lastText5.equals("")) lastText5 = text;
            else {
                lastText1 = lastText2;
                lastText2 = lastText3;
                lastText3 = lastText4;
                lastText4 = lastText5;
                lastText5 = text;
            }

            if (text.matches("Question [0-9]+ of 10")) {
                if (Integer.parseInt(questionNumber) < Integer.parseInt(text.split(" ")[1])) {
                    questionNumber = text.split(" ")[1];
                    answer3 = lastText1;
                    answer2 = lastText2;
                    answer1 = lastText3;
                    question = lastText4;
                    lastText1 = lastText2 = lastText3 = lastText4 = lastText5 = "";
                    if (questionTV != null)
                        questionTV.setText(questionNumber + ": " + question + "\nAnswer1: " + answer1 + "\nAnswer2: " + answer2 + "\nAnswer3: " + answer3);
                    if (MainActivity.isMainDevice && questionTV.getText() != questionTVText) {
                        questionTVText = questionTV.getText().toString();
                        new CallAPI().execute(HOST, question, standardizeQuestion(question), answer1, answer2, answer3, questionNumber);

                    }
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
        List<String> tokens = new ArrayList<>();
        Pattern pattern = Pattern.compile("[^\"]+|\"[^\"]+\"");
        Matcher matcher = pattern.matcher(question);
        List<String> s = new ArrayList<>();
        while (matcher.find()) {
            String match = matcher.group(0).trim();
            if (match.length() == 0 || match.equals("?")) {
                continue;
            }
            if (match.charAt(match.length() - 1) == '?' && match.charAt(0) != '"') {
                match = match.substring(0, match.length() - 1);
            }
            s.add(match);
        }
        for (int i = 0; i < s.size(); i++) {
            Log.d("MATCH", s.get(i));
            if (s.get(i).charAt(0) == '"' || (s.get(i).charAt(0) == '?' && s.get(i).length() == 1)) {
                tokens.add(s.get(i));
                continue;
            }
            if (hash.get(s.get(i).toLowerCase()) != null) {
                tokens.add(s.get(i));
                continue;
            }
            String[] temp = s.get(i).split(" ");
            while (temp.length > 0 && !temp[0].trim().equals("")) {
                int oldLength = temp.length;
                for (int j = temp.length; j >= 0; j--) {
                    int index;
                    if (j == temp.length) index = s.get(i).length();
                    else index = s.get(i).lastIndexOf(temp[j]);
                    if (index > 0) {
                        String word = s.get(i).substring(0, index).trim();
                        if (hash.get(word.toLowerCase()) != null) {
                            String replaceWord = s.get(i).replaceFirst(word, "").trim();
                            s.set(i, replaceWord);
                            temp = s.get(i).split(" ");
                            j = temp.length;
                            tokens.add(word);
                            break;
                        }
                    }
                }
                // Fallback for words not in dictionary
                if (oldLength == temp.length && !temp[0].trim().equals("")) {
                    tokens.add(temp[0].trim());
                    s.set(i, s.get(i).substring(temp[0].trim().length()).trim());
                    temp = s.get(i).split(" ");
                }
            }
        }
        Log.d("TOKENS", "" + tokens);
        List<String> listStopWords = Arrays.asList(stopWords);
        for (int i = 0; i < tokens.size(); i++) {
            if ((!listStopWords.contains(tokens.get(i).toLowerCase())
                    || (listStopWords.contains(tokens.get(i).toLowerCase()) && i != 0)
                    || tokens.get(i).charAt(0) == '"') && !listStopWords.contains(tokens.get(i))) {
                q += tokens.get(i) + " ";
            }
        }
        Log.d("QUESTION AFTER STANDARDIZED", q.trim());
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