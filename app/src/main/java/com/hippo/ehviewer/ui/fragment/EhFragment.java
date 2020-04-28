/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.ui.fragment;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhTagDatabase;
import com.hippo.util.IoThreadPoolExecutor;

import java.lang.ref.WeakReference;

public class EhFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final String KEY_CHECK_FOR_TAG_TRANSLATION_UPDATE = "check_for_tag_translation_update";
    private static final String KEY_TAG_TRANSLATIONS_SOURCE = "tag_translations_source";

    private Preference checkForTagTranslationUpdate;
    private CheckTagTranslationUpdateTask checkTagTranslationUpdateTask;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.eh_settings);

        Preference theme = findPreference(Settings.KEY_THEME);
        Preference darkThemeVariant = findPreference(Settings.KEY_DARK_THEME_VARIANT);
        Preference applyNavBarThemeColor = findPreference(Settings.KEY_APPLY_NAV_BAR_THEME_COLOR);
        Preference gallerySite = findPreference(Settings.KEY_GALLERY_SITE);
        Preference listMode = findPreference(Settings.KEY_LIST_MODE);
        Preference detailSize = findPreference(Settings.KEY_DETAIL_SIZE);
        Preference thumbSize = findPreference(Settings.KEY_THUMB_SIZE);
        Preference showTagTranslations = findPreference(Settings.KEY_SHOW_TAG_TRANSLATIONS);
        checkForTagTranslationUpdate = findPreference(KEY_CHECK_FOR_TAG_TRANSLATION_UPDATE);
        Preference tagTranslationsSource = findPreference(KEY_TAG_TRANSLATIONS_SOURCE);

        theme.setOnPreferenceChangeListener(this);
        darkThemeVariant.setOnPreferenceChangeListener(this);
        applyNavBarThemeColor.setOnPreferenceChangeListener(this);
        gallerySite.setOnPreferenceChangeListener(this);
        listMode.setOnPreferenceChangeListener(this);
        detailSize.setOnPreferenceChangeListener(this);
        thumbSize.setOnPreferenceChangeListener(this);
        showTagTranslations.setOnPreferenceChangeListener(this);
        checkForTagTranslationUpdate.setOnPreferenceClickListener(this);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            getPreferenceScreen().removePreference(applyNavBarThemeColor);
        }

        if (!EhTagDatabase.isPossible(getActivity())) {
            getPreferenceScreen().removePreference(showTagTranslations);
            getPreferenceScreen().removePreference(checkForTagTranslationUpdate);
            getPreferenceScreen().removePreference(tagTranslationsSource);
        }

        String version = getString(R.string.settings_eh_tag_translations_version_unknown);
        EhTagDatabase db = EhTagDatabase.getInstance(getActivity());
        if (db != null) {
            version = db.getVersion();
        }

        checkForTagTranslationUpdate.setSummary(getString(R.string.settings_eh_tag_translations_version, version));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (Settings.KEY_THEME.equals(key)) {
            ((EhApplication) getActivity().getApplication()).recreate();
            return true;
        } else if (Settings.KEY_DARK_THEME_VARIANT.endsWith(key)) {
            ((EhApplication) getActivity().getApplication()).recreate();
            return true;
        } else if (Settings.KEY_APPLY_NAV_BAR_THEME_COLOR.equals(key)) {
            ((EhApplication) getActivity().getApplication()).recreate();
            return true;
        } else if (Settings.KEY_GALLERY_SITE.equals(key)) {
            getActivity().setResult(Activity.RESULT_OK);
            return true;
        } else if (Settings.KEY_LIST_MODE.equals(key)) {
            getActivity().setResult(Activity.RESULT_OK);
            return true;
        } else if (Settings.KEY_DETAIL_SIZE.equals(key)) {
            getActivity().setResult(Activity.RESULT_OK);
        } else if (Settings.KEY_THUMB_SIZE.equals(key)) {
            getActivity().setResult(Activity.RESULT_OK);
        } else if (Settings.KEY_SHOW_TAG_TRANSLATIONS.equals(key)) {
            if (Boolean.TRUE.equals(newValue)) {
                updateTagTranslations();
            }
        }
        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();

        if (KEY_CHECK_FOR_TAG_TRANSLATION_UPDATE.equals(key)) {
            updateTagTranslations();
        }

        return true;
    }

    private void updateTagTranslations() {
        checkForTagTranslationUpdate.setSummary(R.string.settings_eh_checking_for_tag_translations_update);

        checkTagTranslationUpdateTask = new CheckTagTranslationUpdateTask(checkForTagTranslationUpdate);
        checkTagTranslationUpdateTask.executeOnExecutor(IoThreadPoolExecutor.getInstance());
    }

    @Override
    public void onStop() {
        if (checkTagTranslationUpdateTask != null) {
            checkTagTranslationUpdateTask.cancel(true);
        }

        super.onStop();
    }

    private static class CheckTagTranslationUpdateTask extends AsyncTask<Void, Void, Void> {

        private WeakReference<Preference> preferenceReference;

        private CheckTagTranslationUpdateTask(Preference preference) {
            preferenceReference = new WeakReference<>(preference);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Preference preference = preferenceReference.get();
            if (preference != null) {
                EhTagDatabase.update(preference.getContext());
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Preference preference = preferenceReference.get();
            if (preference != null) {
                String version = preference.getContext().getString(R.string.settings_eh_tag_translations_version_unknown);
                EhTagDatabase db = EhTagDatabase.getInstance(preference.getContext());
                if (db != null) {
                    version = db.getVersion();
                }

                preference.setSummary(preference.getContext().getString(R.string.settings_eh_tag_translations_version, version));
            }
        }
    }
}
