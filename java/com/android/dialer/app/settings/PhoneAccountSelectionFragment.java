/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.dialer.app.settings;

import android.app.Fragment;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.annotation.VisibleForTesting;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import java.util.List;
import com.android.dialer.util.PermissionsUtil;
import static android.Manifest.permission.READ_PHONE_STATE;

/**
 * Preference screen that lists SIM phone accounts to select from, and forwards the selected account
 * to {@link #PARAM_TARGET_FRAGMENT}. Can only be used in a {@link PreferenceActivity}
 */
public class PhoneAccountSelectionFragment extends PreferenceFragment {

  /** The {@link PreferenceFragment} to launch after the account is selected. */
  public static final String PARAM_TARGET_FRAGMENT = "target_fragment";

  /**
   * The arguments bundle to pass to the {@link #PARAM_TARGET_FRAGMENT}
   *
   * @see Fragment#getArguments()
   */
  public static final String PARAM_ARGUMENTS = "arguments";

  /**
   * The key to insert the selected {@link PhoneAccountHandle} to bundle in {@link #PARAM_ARGUMENTS}
   */
  public static final String PARAM_PHONE_ACCOUNT_HANDLE_KEY = "phone_account_handle_key";

  /**
   * The title of the {@link #PARAM_TARGET_FRAGMENT} once it is launched with {@link
   * PreferenceActivity#startWithFragment(String, Bundle, Fragment, int)}, as a string resource ID.
   */
  public static final String PARAM_TARGET_TITLE_RES = "target_title_res";

  private String targetFragment;
  private Bundle arguments;
  private String phoneAccountHandleKey;
  private int titleRes;
  // UNISOC: add for bug1162990(bug719145)
  private SubscriptionManager mSubscriptionManager;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    targetFragment = getArguments().getString(PARAM_TARGET_FRAGMENT);
    arguments = new Bundle();
    arguments.putAll(getArguments().getBundle(PARAM_ARGUMENTS));
    phoneAccountHandleKey = getArguments().getString(PARAM_PHONE_ACCOUNT_HANDLE_KEY);
    titleRes = getArguments().getInt(PARAM_TARGET_TITLE_RES, 0);
    // UNISOC: add for bug1162990(bug719145)
    mSubscriptionManager = SubscriptionManager.from(getActivity());
  }

  final class AccountPreference extends Preference {
    private final PhoneAccountHandle phoneAccountHandle;

    public AccountPreference(
        Context context, PhoneAccountHandle phoneAccountHandle, PhoneAccount phoneAccount) {
      super(context);
      this.phoneAccountHandle = phoneAccountHandle;
      setTitle(phoneAccount.getLabel());
      setSummary(phoneAccount.getShortDescription());
      Icon icon = phoneAccount.getIcon();
      if (icon != null) {
        setIcon(icon.loadDrawable(context));
      }
    }

    @VisibleForTesting
    void click() {
      onClick();
    }

    @Override
    protected void onClick() {
      super.onClick();
      PreferenceActivity preferenceActivity = (PreferenceActivity) getActivity();
      arguments.putParcelable(phoneAccountHandleKey, phoneAccountHandle);
      preferenceActivity.startWithFragment(targetFragment, arguments, null, 0, titleRes, 0);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    /* UNISOC: add for bug1162990(bug738400) @{ */
    if (!PermissionsUtil.hasPermission(getContext(), READ_PHONE_STATE)) {
      return;
    }
    /* @} */
    setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
    /* UNISOC: add for bug1162990(bug719145) @{ */
    mSubscriptionManager.addOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
    refreshAccount();
    /* @} */
  }

  /* UNISOC: add for bug1162990(bug719145) @{ */
  private void refreshAccount() {
    PreferenceScreen screen = getPreferenceScreen();
    if (screen != null) {
        /*UNISOC:modify for bug1162990(bug1036614) & bug1167589 @{*/
        List<SubscriptionInfo> subscriptionInfos = mSubscriptionManager.getActiveSubscriptionInfoList();
        if (subscriptionInfos == null || subscriptionInfos.isEmpty()) {
          if (getActivity() != null) {
            getActivity().onBackPressed();
          }
          return;
        }
        /* @} */
        screen.removeAll();
        TelecomManager telecomManager = getContext().getSystemService(TelecomManager.class);

        List<PhoneAccountHandle> accountHandles = telecomManager.getCallCapablePhoneAccounts();

        Context context = getActivity();
        for (PhoneAccountHandle handle : accountHandles) {
          PhoneAccount account = telecomManager.getPhoneAccount(handle);
          if (account != null) {
            final boolean isSimAccount =
                0 != (account.getCapabilities() & PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION);
            if (isSimAccount) {
              screen.addPreference(new AccountPreference(context, handle, account));
            }
          }
        }
    }
  }

  @Override
  public void onPause() {
    mSubscriptionManager.removeOnSubscriptionsChangedListener(mOnSubscriptionsChangeListener);
    super.onPause();
  }

  private final SubscriptionManager.OnSubscriptionsChangedListener mOnSubscriptionsChangeListener
          = new SubscriptionManager.OnSubscriptionsChangedListener() {
    @Override
    public void onSubscriptionsChanged() {
      refreshAccount();
    }
  };
  /* @} */
}
