package com.example.gesturelib;

import android.graphics.Bitmap;
import android.hardware.camera2.CameraDevice;
import android.util.Size;

public interface Camera2Listener {
    void method(Bitmap bitmap);
}