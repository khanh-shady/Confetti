package com.example.confetti;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class PushService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Log.d("AJSDIJSIAD", "" + message.getData());
        Overlay.questionNumber = message.getData().get("questionNo");
        String result = message.getData().get("result");
        if (result.contains("Đáp án A")) {
            Overlay.posY = MainActivity.posA;
        } else if (result.contains("Đáp án B")) {
            Overlay.posY = MainActivity.posB;
        } else if (result.contains("Đáp án C")){
            Overlay.posY = MainActivity.posC;
        }
        Overlay.isCloneGotResult = true;
        Overlay.cloneResult = result;
    }
}
