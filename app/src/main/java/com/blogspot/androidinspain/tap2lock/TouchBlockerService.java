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

    private boolean isForegroundAppWhiteListed(String packageToEvaluate) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // 1. Creamos nuestro conjunto por defecto (whitelist inicial)
        Set<String> defaultWhitelist = new HashSet<>(Arrays.asList(SettingsActivity.defaultWhiteListPackagesNames));

        // MultiSelectListPreference guarda los datos en un Set<String>
        Set<String> whitelist = prefs.getStringSet("whitelist_packages", defaultWhitelist);

        boolean isDoubleTapInLockscreenEnabled = prefs.getBoolean("whitelist_lockscreen", false);

        Log.d(TAG, "packageToEvaluate: " + packageToEvaluate);
        Log.d(TAG, "whiteListPackagesNames: " + whitelist);
        Log.d(TAG,"isDoubleTapInLockscreenEnabled: " + isDoubleTapInLockscreenEnabled);

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        Log.d(TAG, "isKeyguardLocked: " + km.isKeyguardLocked());

        if (km.isKeyguardLocked()) {
            return isDoubleTapInLockscreenEnabled;
        } else {
            return whitelist.contains(packageToEvaluate);
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

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

    private boolean isSystemUI(String packageName) {
        return "com.android.systemui".equals(packageName);
    }

    private boolean isSystemUI(AccessibilityEvent event) {
        return event.getPackageName() != null && isSystemUI(event.getPackageName().toString());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() == null) return;

            String newPackageName = event.getPackageName().toString();

            // ESCENARIO A: El evento actual es de SystemUI (Barra de notificaciones, volumen, etc.)
            if (isSystemUI(newPackageName)) {
                // Solo guardamos el paquete anterior si veníamos de una app REAL (no SystemUI) y esa app estaba en la whitelist
                if (currentPackageName != null && !isSystemUI(currentPackageName)) {
                    if (isForegroundAppWhiteListed(currentPackageName)) {
                        lastPackageNameDifferentFromSystemUI = currentPackageName;
                        Log.d(TAG, "SystemUI se ha colado delante. Guardamos la app real subyacente: " + lastPackageNameDifferentFromSystemUI);
                    }
                }
                // NOTA: No limpiamos 'lastPackageNameDifferentFromSystemUI' en el else de aquí
                // para que sucesivos eventos de SystemUI no borren la app que guardamos primero.
            }
            // ESCENARIO B: El usuario ha cambiado a otra aplicación real (Launcher, WhatsApp, etc.)
            else {
                // Como ya estamos en una app real, limpiamos el "salvavidas" de SystemUI
                lastPackageNameDifferentFromSystemUI = null;
                Log.d(TAG, "Nueva app real enfocada: " + newPackageName);
            }

            // Finalmente, actualizamos el paquete actual
            currentPackageName = newPackageName;

            Log.d(TAG, "Estado actual -> currentPackageName: " + currentPackageName
                    + " | lastRealApp: " + lastPackageNameDifferentFromSystemUI);
        }
    }

    @Override
    public void onInterrupt() {}

    private void lockScreen() {
        Log.d(TAG, "lockScreen");

        // Si la app en primer plano es SystemUI, miramos la app real que estaba justo debajo
        String foregroundApp = isSystemUI(currentPackageName) ? lastPackageNameDifferentFromSystemUI : currentPackageName;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (isForegroundAppWhiteListed(foregroundApp)) {
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
