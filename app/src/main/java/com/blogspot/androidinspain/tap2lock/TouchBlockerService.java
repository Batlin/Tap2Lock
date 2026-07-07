package com.blogspot.androidinspain.tap2lock;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import androidx.preference.PreferenceManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TouchBlockerService extends AccessibilityService {

    private String TAG = getClass().getName();
    private long lastClickTime = 0;

    private WindowManager windowManager;
    private View overlayView;
    private GestureDetector gestureDetector;
    private String currentPackageName = null;
    private String lastPackageNameDifferentFromSystemUI = null;

    private long getCustomDoubleTapTimeout() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // 200 es el valor por defecto si no existe registro previo
        return prefs.getInt("double_tap_timeout", 200);
    }

    private boolean isCurrentPackageWhiteListed(String currentPackage) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // 1. Creamos nuestro conjunto por defecto (whitelist inicial)
        Set<String> defaultWhitelist = new HashSet<>(Arrays.asList(SettingsActivity.defaultWhiteListPackagesNames));

        // MultiSelectListPreference guarda los datos en un Set<String>
        Set<String> whitelist = prefs.getStringSet("whitelist_packages", defaultWhitelist);

        boolean isDoubleTapInLockscreenEnabled = prefs.getBoolean("whitelist_lockscreen", false);

        Log.d(TAG, "currentPackageName: " + currentPackageName);
        Log.d(TAG, "whiteListPackagesNames: " + whitelist);
        Log.d(TAG,"isDoubleTapInLockscreenEnabled: " + isDoubleTapInLockscreenEnabled);

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        Log.d(TAG, "isKeyguardLocked: " + km.isKeyguardLocked());

        if (km.isKeyguardLocked()) {
            return isDoubleTapInLockscreenEnabled;
        } else {
            return whitelist.contains(currentPackage);
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Inicializamos el detector de gestos nativo de Android
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                Log.d(TAG, "onDoubleTap");

                // Al detectar el doble toque, disparamos la acción global de bloqueo
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
                }
                return true;
            }
        });

        // Creamos una vista vacía (un contenedor transparente)
        overlayView = new View(this);

        // Configuramos los parámetros de la ventana flotante del sistema
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                // FLAG_NOT_TOUCHABLE: Hace que la ventana sea 100% invisible al tacto (los clics pasan abajo)
                // FLAG_WATCH_OUTSIDE_TOUCH: Nos avisa cuando el usuario toca "fuera" (como la ventana es gigante, todo es fuera)
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.LEFT;

        // Escuchamos los eventos táctiles en la ventana invisible
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                //Log.d(TAG, "onTouch");
                // 1. El detector analiza si es un doble tap en segundo plano
                gestureDetector.onTouchEvent(event);

                long currentTime = System.currentTimeMillis();

                if (currentTime - lastClickTime < getCustomDoubleTapTimeout()) {
                    lockScreen();
                }

                lastClickTime = currentTime;

                // 2. IMPORTANTE: Forzamos a retornar FALSE.
                // Esto hace que el toque "atraviese" la ventana flotante transparente
                // y la app que está abajo reciba el click con normalidad.
                return false;
            }
        });

        // Añadimos la vista invisible a la pantalla global
        windowManager.addView(overlayView, params);
        Log.d(TAG, "onServiceConnected");
    }

    private boolean isSystemUI(AccessibilityEvent event) {
        return event.getPackageName().toString().equals("com.android.systemui");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        //Log.d(TAG, "onAccessibilityEvent " + event);

        // TYPE_WINDOW_STATE_CHANGED se dispara cada vez que el usuario abre otra app o vuelve al inicio
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() != null) {
                // Guardamos el nombre del paquete anterior (que esté whitelisted) y que se le ponga delante com.android.systemui
                // O sucesivos com.android.systemui
                if(currentPackageName != null && (isCurrentPackageWhiteListed(currentPackageName) || isSystemUI(event)) && isSystemUI(event) ) {
                    if(isCurrentPackageWhiteListed(currentPackageName))
                        lastPackageNameDifferentFromSystemUI = currentPackageName;

                    Log.d(TAG, "Guardamos lastPackageNameDifferentFromSystemUI: " + lastPackageNameDifferentFromSystemUI);
                } else {
                    lastPackageNameDifferentFromSystemUI = null;
                }
                // Guardamos el nombre del paquete actual (ej: "com.android.launcher3", "com.whatsapp", etc.)
                currentPackageName = event.getPackageName().toString();
                Log.d(TAG, "currentPackageName: " + currentPackageName + ", className: " + event.getClassName());
                Log.d(TAG, "lastPackageNameDifferentFromSystemUI: "+ lastPackageNameDifferentFromSystemUI);
            }
        }
    }

    @Override
    public void onInterrupt() {}

    private void lockScreen() {
        Log.d(TAG, "lockScreen");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (isCurrentPackageWhiteListed(currentPackageName) || isCurrentPackageWhiteListed(lastPackageNameDifferentFromSystemUI)) {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Al apagar el servicio, destruimos la ventana flotante para no dejar basura en memoria
        if (windowManager != null && overlayView != null) {
            windowManager.removeView(overlayView);
        }
    }
}
