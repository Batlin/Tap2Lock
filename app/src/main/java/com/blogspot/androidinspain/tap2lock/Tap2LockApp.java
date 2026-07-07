package com.blogspot.androidinspain.tap2lock;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public class Tap2LockApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Apply DynamicColors globally
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
