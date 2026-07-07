package com.blogspot.androidinspain.tap2lock;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    // Specific for Pixel users!
    public static String[] defaultWhiteListPackagesNames = { "com.google.android.apps.nexuslauncher" };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Use ConstraintLayout with a fragment_container view
        setContentView(R.layout.activity_settings);

        // Inject new SettingsFragment in fragment_container
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private Preference permissionsWarningPref;
        private Preference permissionsOkPref;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            setupPermissionsWarningPref();
            setupDoubleTapIntervalPref();
            setupVersionPref();
            // Cargamos las apps instaladas en segundo plano para el selector
            new LoadAppsTask((MultiSelectListPreference) findPreference("whitelist_packages"),
                    requireContext().getPackageManager()).execute();
        }

        @Override
        public void onResume() {
            super.onResume();
            updatePermissionsWarningPrefVisibility();
        }

        private void setupPermissionsWarningPref() {
            permissionsWarningPref = findPreference("accessibility_not_granted_warning");
            permissionsOkPref = findPreference("accessibility_granted_message");

            // If user clicks on permissionsWarningPref, go to Accesibility Settings
            if (permissionsWarningPref != null) {
                permissionsWarningPref.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return true;
                });
            }

            updatePermissionsWarningPrefVisibility();
        }

        // Hide preference if permissions are already granted
        private void updatePermissionsWarningPrefVisibility() {
            if (permissionsWarningPref == null || permissionsOkPref == null) return;

            boolean isEnabled = isAccessibilityServiceEnabled(requireContext());

            if (!isEnabled) {
                permissionsWarningPref.setVisible(true);
                permissionsOkPref.setVisible(false);
            } else {
                permissionsWarningPref.setVisible(false);
                permissionsOkPref.setVisible(true);
            }
        }

        private void setupDoubleTapIntervalPref() {

            SeekBarPreference doubleTapIntervalPref = findPreference("double_tap_timeout");

            if (doubleTapIntervalPref != null) {
                doubleTapIntervalPref.setUpdatesContinuously(true);
                int doubleTapInterval = doubleTapIntervalPref.getValue();
                doubleTapIntervalPref.setSummary(getContext().getString(R.string.settings_doubletap_interval_summary, doubleTapInterval));

                // When the value changes, update the summary accordingly
                doubleTapIntervalPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        int updatedValue = (int) newValue;
                        preference.setSummary(getContext().getString(R.string.settings_doubletap_interval_summary, updatedValue));
                        return true;
                    }
                });
            }

        }

        private void setupVersionPref() {
            Preference versionPref = findPreference("app_version");

            if (versionPref != null) {
                Context context = requireContext();
                try {
                    String versionName;
                    PackageManager pm = context.getPackageManager();

                    // 2. Get APK version
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PackageInfo packageInfo = pm.getPackageInfo(
                                context.getPackageName(),
                                PackageManager.PackageInfoFlags.of(0)
                        );
                        versionName = packageInfo.versionName;
                    } else {
                        PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
                        versionName = packageInfo.versionName;
                    }

                    // Set summary (for example: "v1.0.4-debug")
                    versionPref.setSummary("v" + versionName);

                } catch (Exception e) {
                    e.printStackTrace();
                    versionPref.setSummary(getString(R.string.settings_version_not_available));
                }
            }

        }

        // Return boolean whether accesibility service permission is granted
        private boolean isAccessibilityServiceEnabled(Context context) {
            AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null) return false;

            List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);

            for (AccessibilityServiceInfo service : enabledServices) {
                if (service.getId().contains(context.getPackageName())) {
                    return true;
                }
            }
            return false;
        }
    }


    // Asynk task to list all apps in a MultiSelectListPreference
    private static class LoadAppsTask extends AsyncTask<Void, Void, List<AppInfoHolder>> {
        private final MultiSelectListPreference preference;
        private final PackageManager packageManager;

        LoadAppsTask(MultiSelectListPreference preference, PackageManager pm) {
            this.preference = preference;
            this.packageManager = pm;
        }

        @Override
        protected List<AppInfoHolder> doInBackground(Void... voids) {
            List<AppInfoHolder> apps = new ArrayList<>();
            // Get all installed apps
            List<ApplicationInfo> installedApps = packageManager.getInstalledApplications(PackageManager.MATCH_ALL);

            for (ApplicationInfo appInfo : installedApps) {
                String label = appInfo.loadLabel(packageManager).toString();
                apps.add(new AppInfoHolder(label, appInfo.packageName));
            }

            // Sort by name
            Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
            return apps;
        }

        @Override
        protected void onPostExecute(List<AppInfoHolder> apps) {
            if (preference == null || apps.isEmpty()) return;

            CharSequence[] entries = new CharSequence[apps.size()];
            CharSequence[] entryValues = new CharSequence[apps.size()];

            for (int i = 0; i < apps.size(); i++) {
                entries[i] = apps.get(i).label;
                entryValues[i] = apps.get(i).packageName;
            }

            // Inyectamos las apps disponibles dentro del componente nativo
            preference.setEntries(entries);
            preference.setEntryValues(entryValues);

            // 2. Definimos cuáles queremos marcar por defecto si el usuario nunca ha entrado aquí
            java.util.Set<String> currentValues = preference.getValues();

            // Si la lista actual está vacía (significa que es la primera vez que se abre la app)
            if (currentValues == null || currentValues.isEmpty()) {
                java.util.Set<String> defaultChecked = new java.util.HashSet<>();

                // La lista de paquetes que queremos pre-marcar si existen en el dispositivo
                java.util.List<String> targets = java.util.Arrays.asList(defaultWhiteListPackagesNames);

                // Comprobamos cuáles de nuestros targets están realmente instalados en este teléfono
                for (AppInfoHolder app : apps) {
                    if (targets.contains(app.packageName)) {
                        defaultChecked.add(app.packageName);
                    }
                }

                // Si encontramos alguno de los launchers conocidos, los dejamos marcados
                if (!defaultChecked.isEmpty()) {
                    preference.setValues(defaultChecked);
                }
            }
        }
    }

    // Clase contenedora simple para estructurar los datos de las apps
    private static class AppInfoHolder {
        String label;
        String packageName;

        AppInfoHolder(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }

}
