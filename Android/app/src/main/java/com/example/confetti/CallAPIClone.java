package com.example.confetti;

import android.os.AsyncTask;
import android.util.Log;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class CallAPIClone extends AsyncTask<String, String, String> {

    public CallAPIClone(){
        //set context variables if required
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected String doInBackground(String... params) {
        String urlString = params[0]; // URL to call
        Log.d("QJ@()JE)( J()J #()", urlString);
        String result = "";

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            InputStream in = conn.getInputStream();
            InputStreamReader isw = new InputStreamReader(in);

            int data = isw.read();
            while (data != -1) {
                char current = (char) data;
                data = isw.read();
                result += current;
            }
        } catch (Exception e) {
            Log.d("jiiajsidojasiodjoi3r89u3r", e.getMessage());
        }
        return result;
    }

    protected void onPostExecute(String result) {
        if (result.contains("true")) {
            Overlay.isShowStarted = true;
            Overlay.TIMER = 1000;
            return;
        } else if (result.contains("false")) {
            Overlay.TIMER = 5000;
            return;
        }
        if (result.contains("Đáp án A")) {
            Overlay.posY = MainActivity.posA;
        } else if (result.contains("Đáp án B")) {
            Overlay.posY = MainActivity.posB;
        } else if (result.contains("Đáp án C")){
            Overlay.posY = MainActivity.posC;
        }
        if (!Overlay.clickedQuestionNumber.equals(Overlay.questionNumber)) {
            Overlay.resultTV.setText("Câu " + Overlay.questionNumber + ": " + result);
        }
    }
}
