package com.blogspot.androidinspain.tap2lock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;

public class PermissionActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        showAccesibilityPermissionsDialog();
    }

    private void showAccesibilityPermissionsDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.alertdialog_title))
                .setMessage(getString(R.string.alertdialog_message))
                .setPositiveButton(getString(R.string.alertdialog_positive_button), (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(getString(R.string.alertdialog_negative_button), (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

}