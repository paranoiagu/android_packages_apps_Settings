/*
 * Copyright (C) 2016 ParanoiaGu CM
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.pd;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemProperties;
import android.net.TrafficStats;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.preference.PreferenceScreen;
import android.provider.SearchIndexableResource;
import android.provider.Settings;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.search.Indexable;
import com.android.internal.util.pd.PDUtils;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import android.preference.Preference.OnPreferenceChangeListener;

import java.util.List;
import java.util.ArrayList;

import com.android.internal.logging.MetricsLogger;

public class PdSettings extends SettingsPreferenceFragment  implements OnPreferenceChangeListener, Indexable {

    private static final String TAG = "PdSettings";
  
    private static final String SHOW_FOURG = "show_fourg";
    private static final String SHOW_THREEG = "show_threeg";
    private SwitchPreference mShowFourG;
    private SwitchPreference mShowThreeG;

    private static final String NETWORK_TRAFFIC_STATE = "network_traffic_state";
    private static final String NETWORK_TRAFFIC_AUTOHIDE = "network_traffic_autohide";
    private int mNetTrafficVal;
    private int MASK_UP;
    private int MASK_DOWN;

    private ListPreference mNetTrafficState;
    private SwitchPreference mNetTrafficAutohide;

    private static final String KEY_CAMERA_SOUNDS = "camera_sounds";
    private static final String PROP_CAMERA_SOUND = "persist.sys.camera-sound";
    private SwitchPreference mCameraSounds;
    private static final int DLG_CAMERA_SOUND = 1;

    //Lockscreen Torch
    private static final String KEYGUARD_TOGGLE_TORCH = "keyguard_toggle_torch";
    private SwitchPreference mKeyguardTorch;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pd_settings);

        PreferenceScreen prefSet = getPreferenceScreen();
        ContentResolver resolver = getActivity().getContentResolver(); 

        mShowFourG = (SwitchPreference) findPreference(SHOW_FOURG);
        if (PDUtils.isWifiOnly(getActivity())) {
            prefSet.removePreference(mShowFourG);
        } else {
            mShowFourG.setChecked((Settings.System.getInt(resolver,
                Settings.System.SHOW_FOURG, 0) == 1));
        }

        mShowThreeG = (SwitchPreference) findPreference(SHOW_THREEG);
        if (PDUtils.isWifiOnly(getActivity())) {
            prefSet.removePreference(mShowThreeG);
        } else {
            mShowThreeG.setChecked((Settings.System.getInt(resolver,
                Settings.System.SHOW_THREEG, 0) == 1));
        }

        loadResources();
        mNetTrafficState = (ListPreference) prefSet.findPreference(NETWORK_TRAFFIC_STATE);
        mNetTrafficAutohide = (SwitchPreference) prefSet.findPreference(NETWORK_TRAFFIC_AUTOHIDE);

        // TrafficStats will return UNSUPPORTED if the device does not support it.
        if (TrafficStats.getTotalTxBytes() != TrafficStats.UNSUPPORTED &&
                TrafficStats.getTotalRxBytes() != TrafficStats.UNSUPPORTED) {
            mNetTrafficVal = Settings.System.getInt(resolver, Settings.System.NETWORK_TRAFFIC_STATE, 0);
            int intIndex = mNetTrafficVal & (MASK_UP + MASK_DOWN);
            intIndex = mNetTrafficState.findIndexOfValue(String.valueOf(intIndex));
            if (intIndex <= 0) {
                mNetTrafficAutohide.setEnabled(false);
            }
            mNetTrafficState.setValueIndex(intIndex >= 0 ? intIndex : 0);
            mNetTrafficState.setSummary(mNetTrafficState.getEntry());
            mNetTrafficState.setOnPreferenceChangeListener(this);
        } else {
            prefSet.removePreference(findPreference(NETWORK_TRAFFIC_STATE));
            prefSet.removePreference(findPreference(NETWORK_TRAFFIC_AUTOHIDE));
        }

        mCameraSounds = (SwitchPreference) findPreference(KEY_CAMERA_SOUNDS);
        mCameraSounds.setChecked(SystemProperties.getBoolean(PROP_CAMERA_SOUND, true));
        mCameraSounds.setOnPreferenceChangeListener(this);
    }

    private void loadResources() {
        Resources resources = getActivity().getResources();
        MASK_UP = resources.getInteger(R.integer.maskUp);
        MASK_DOWN = resources.getInteger(R.integer.maskDown);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();
        if (KEY_CAMERA_SOUNDS.equals(key)) {
            if ((Boolean) newValue) {
                SystemProperties.set(PROP_CAMERA_SOUND, "1");
            } else {
                showDialogInner(DLG_CAMERA_SOUND);
            }
            return true;
        } else if (NETWORK_TRAFFIC_STATE.equals(key)) {
            int intState = Integer.valueOf((String)newValue);
            mNetTrafficVal = setBit(mNetTrafficVal, MASK_UP, getBit(intState, MASK_UP));
            mNetTrafficVal = setBit(mNetTrafficVal, MASK_DOWN, getBit(intState, MASK_DOWN));
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_STATE, mNetTrafficVal);
            int index = mNetTrafficState.findIndexOfValue((String) newValue);
            mNetTrafficState.setSummary(mNetTrafficState.getEntries()[index]);
            updateNetworkTrafficState(index);
            return true;
        } else if (NETWORK_TRAFFIC_AUTOHIDE.equals(key)) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.NETWORK_TRAFFIC_AUTOHIDE, value ? 1 : 0);
            return true;
        }
        return false;
    }

    private void updateNetworkTrafficState(int mIndex) {
        if (mIndex <= 0) {
            mNetTrafficAutohide.setEnabled(false);
        } else {
            mNetTrafficAutohide.setEnabled(true);
        }
    }

    // intMask should only have the desired bit(s) set
    private int setBit(int intNumber, int intMask, boolean blnState) {
        if (blnState) {
            return (intNumber | intMask);
        }
        return (intNumber & ~intMask);
    }

    private boolean getBit(int intNumber, int intMask) {
        return (intNumber & intMask) == intMask;
    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if  (preference == mShowFourG) {
            boolean checked = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.SHOW_FOURG, checked ? 1:0);
            return true;
        } else if  (preference == mShowThreeG) {
            boolean checked = ((SwitchPreference)preference).isChecked();
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.SHOW_THREEG, checked ? 1:0);
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    protected int getMetricsCategory()
    {
        return MetricsLogger.APPLICATION;
    }

    @Override
    public void onResume() {
        super.onResume();
        Configuration config = getResources().getConfiguration(); 
    }


    private void showDialogInner(int id) {
        DialogFragment newFragment = MyAlertDialogFragment.newInstance(id);
        newFragment.setTargetFragment(this, 0);
        newFragment.show(getFragmentManager(), "dialog " + id);
    }

    public static class MyAlertDialogFragment extends DialogFragment {
        public static MyAlertDialogFragment newInstance(int id) {
            MyAlertDialogFragment frag = new MyAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt("id", id);
            frag.setArguments(args);
            return frag;
        }

        PdSettings getOwner() {
            return (PdSettings) getTargetFragment();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int id = getArguments().getInt("id");
            switch (id) {
                case DLG_CAMERA_SOUND:
                    return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.attention)
                    .setMessage(R.string.camera_sound_warning_dialog_text)
                    .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            SystemProperties.set(PROP_CAMERA_SOUND, "0");
                        }
                    })
                    .setNegativeButton(R.string.dlg_cancel,
                        new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    })
                    .create();
            }
            throw new IllegalArgumentException("unknown id " + id);
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            int id = getArguments().getInt("id");
            switch (id) {
                case DLG_CAMERA_SOUND:
                    getOwner().mCameraSounds.setChecked(true);
                    break;
            }
        }
    }

    public static final Indexable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(Context context,
                                                                            boolean enabled) {
                    ArrayList<SearchIndexableResource> result =
                            new ArrayList<SearchIndexableResource>();

                    SearchIndexableResource sir = new SearchIndexableResource(context);
                   sir.xmlResId = R.xml.pd_settings;
                    result.add(sir);

                    return result;
                }

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    final List<String> keys = new ArrayList<String>();
                    return keys;
                }
        };
}
