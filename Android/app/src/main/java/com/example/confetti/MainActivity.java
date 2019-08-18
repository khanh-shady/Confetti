package com.example.confetti;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import static com.example.confetti.Overlay.mediaProjectionManager;

public class MainActivity extends AppCompatActivity {

    private final String M20_IMEI_MAIN_DEVICE = "354556102461723";

    private final int REQUEST_CODE_IMEI = 1;
    private final int REQUEST_CODE_OVERLAY = 2;

    private final int REQUEST_CODE = 1;
    public static MediaProjection mediaProjection;

    public static int posA, posB, posC;

    private String deviceIMEI = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        calculateAnswerPositions();
        deviceIMEI = getDeviceIMEI(this);
        if (deviceIMEI.equals(M20_IMEI_MAIN_DEVICE)) {
            startMainDeviceFunction();
        }
        startCloneDevicesFunction();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (canDrawOverlayViews(this)) {
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
        } else {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void startMainDeviceFunction() {
        mediaProjectionManager = (MediaProjectionManager) getApplicationContext().getSystemService(MEDIA_PROJECTION_SERVICE);
        if (!canDrawOverlayViews(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void startCloneDevicesFunction() {

    }

    public static boolean canDrawOverlayViews(Context con){
        try {
            return Settings.canDrawOverlays(con);
        }
        catch(NoSuchMethodError e){
            e.printStackTrace();
            return false;
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            String uri = data.toUri(0);
            MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Activity.MEDIA_PROJECTION_SERVICE);
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        }
    }

    private String getDeviceIMEI(Context context) {
        try {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_PHONE_STATE}, REQUEST_CODE_IMEI);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_IMEI: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    deviceIMEI = getDeviceIMEI(this);
                    if (deviceIMEI.equals(M20_IMEI_MAIN_DEVICE)) {
                        startMainDeviceFunction();
                    }
                }
                break;
            }
        }
    }

    private void calculateAnswerPositions() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);
        int height = size.y;
        posA = (int) (height * 0.76);
        posB =  (int) (height * 0.85);
        posC = (int) (height * 0.9);
    }
}
