package com.example.confetti;

import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class CallAPI extends AsyncTask<String, String, String> {
    public CallAPI(){
        //set context variables if required
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected String doInBackground(String... params) {
        String urlString = params[0]; // URL to call
        String question = params[1]; //data to post
        String standardizedQuestion = params[2]; //data to post
        String answer1 = params[3]; //data to post
        String answer2 = params[4]; //data to post
        String answer3 = params[5]; //data to post
        String questionNumber = params[6]; //data to post
        String result = "";

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(getPostDataString(question, standardizedQuestion, answer1, answer2, answer3, questionNumber));
            writer.flush();
            writer.close();
            os.close();
            conn.connect();

            int responseCode = conn.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK){
                String line;
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                while ((line=br.readLine()) != null) {
                    result += line;
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return result;
    }

    protected void onPostExecute(String result) {
        if (result.contains("Đáp án A")) {
            Overlay.posY = MainActivity.posA;
        } else if (result.contains("Đáp án B")) {
            Overlay.posY = MainActivity.posB;
        } else if (result.contains("Đáp án C")) {
            Overlay.posY = MainActivity.posC;
        }
        Overlay.resultTV.setText("Câu " + Overlay.questionNumber + ": " + result);
    }

    private String getPostDataString(String question, String standardizedQuestion, String answer1, String answer2, String answer3, String questionNumber) throws UnsupportedEncodingException {
        HashMap<String, String> params = new HashMap<>();
        params.put("question", question);
        params.put("standardizedQuestion", standardizedQuestion);
        params.put("answer1", answer1);
        params.put("answer2", answer2);
        params.put("answer3", answer3);
        params.put("questionNumber", questionNumber);
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for(Map.Entry<String, String> entry : params.entrySet()){
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return result.toString();
    }
}
