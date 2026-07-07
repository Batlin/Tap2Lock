package com.blogspot.androidinspain.tap2lock;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

public class TouchTileService extends TileService {

    private String TAG = getClass().getName();

    @Override
    public void onStartListening() {
        super.onStartListening();
        actualizarIcono();
    }

    private void actualizarIcono() {
        Tile tile = getQsTile();
        if (tile == null) return;

        // Comprobamos si el bloqueo está activo realmente.
        // Para esto, puedes usar una variable estática en el Servicio
        // o comprobar si el servicio de accesibilidad está corriendo.
        // Comprobamos el estado real del servicio de accesibilidad
        boolean isActive = isAccessibilityEnabled();

        if (isActive) {
            tile.setState(Tile.STATE_ACTIVE); // Color encendido del sistema
            tile.setLabel("Doble Tap: Activo");
            // Opcional: puedes cambiar el icono si quieres uno para activo
            // tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile_on));
        } else {
            tile.setState(Tile.STATE_INACTIVE); // Color apagado (grisáceo)
            tile.setLabel("Doble Tap: Inactivo");
            // tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile_off));
        }

        // Obligatorio para que los cambios se reflejen en la pantalla del usuario
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();

        PendingIntent pendingIntent = null;
        Intent intent = null;

        if (!isAccessibilityEnabled()) {
            Log.d(TAG, "Accesibility is disabled. Start PermissionActivity");

            // 1. Crear el Intent hacia tu actividad de permisos
            intent = new Intent(this, PermissionActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        } else {

            intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            // 2. Envolverlo en un PendingIntent (Obligatorio en API 34+)
            // Usamos FLAG_IMMUTABLE por seguridad y FLAG_UPDATE_CURRENT para refrescar el contenido
            pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;

        // Obtenemos la lista de servicios que están actualmente habilitados
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);

        for (AccessibilityServiceInfo service : enabledServices) {
            // Comparamos el ID del servicio (formato: paquete/clase)
            if (service.getId().contains(getPackageName())) {
                return true;
            }
        }
        return false;
    }

}