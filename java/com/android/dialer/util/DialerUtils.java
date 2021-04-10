/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.dialer.util;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.android.dialer.common.LogUtil;
import com.android.dialer.telecom.TelecomUtil;

import java.io.File;
import java.util.Iterator;
import java.util.Random;
import java.util.HashMap;

import android.telephony.SubscriptionInfo;

import java.util.List;
import java.util.ArrayList;

import android.Manifest.permission;
import android.telephony.SubscriptionManager;
import android.content.pm.PackageManager;
import android.app.Activity;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;

import com.android.dialer.callintent.CallInitiationType;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment;
import com.android.dialer.app.DialtactsActivity;
import com.android.dialer.callintent.CallIntentBuilder;
/** UNISOC: bug1110945 DUT shows abnormal behaviour with calllog items in mentioned scenario. @{*/
import com.android.dialer.util.R;
import com.android.incallui.call.CallList;
import android.net.Uri;
/** @}*/

import android.os.RemoteException;
import android.util.Log;
//import com.android.incallui.sprd.InCallUiUtils;
import com.android.ims.ImsManager;
import com.android.ims.internal.IImsRegisterListener;
import com.android.ims.internal.IImsServiceEx;
import com.android.ims.internal.ImsManagerEx;
import com.android.contacts.common.widget.SelectPhoneAccountDialogOptionsUtil;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;

/**
 * General purpose utility methods for the Dialer.
 */
public class DialerUtils {

    /**
     * Prefix on a dialed number that indicates that the call should be placed through the Wireless
     * Priority Service.
     */
    private static final String WPS_PREFIX = "*272";

    public static final String FILE_PROVIDER_CACHE_DIR = "my_cache";

    private static final Random RANDOM = new Random();


    public static final String SUB_ID_EXTRA =
            "com.android.phone.settings.SubscriptionInfoHelper.SubscriptionId";
    private static final String SELECT_FRAGMENT_TAG = "tag_select_acct_fragment";
    private static final String PHONE_PACKAGE_NAME = "com.android.phone";
    private static final String VOICE_MAIL_SETTINGS_ACTIVITY =
            "com.android.phone.settings.VoicemailSettingsActivity";


    /**
     * UNISOC: bug895150 Card one Unicom card two mobile, dial the number to enter, click on the
     * "initiate video call" button above, pop-up card box
     * Add for bug 771875. @{
     */
    public static final int SLOT_ID_ONE = 0;
    public static final int SLOT_ID_TWO = 1;
    public static final int SLOT_ID_ONE_TWO = 2;
    public static final int SLOT_ID_NONE = -1;
    // this variable to remember which only one slot sim has been registered ims for L+L mode.
    private static int mRegisteredImsSlot = -1;
    /** @} */

    private static Context mContext;

    /**
     * Attempts to start an activity and displays a toast with the default error message if the
     * activity is not found, instead of throwing an exception.
     *
     * @param context to start the activity with.
     * @param intent  to start the activity with.
     */
    public static void startActivityWithErrorToast(Context context, Intent intent) {
        /** UNISOC AndroidQ Feature Porting: bug1072659 Add feature of low battery for Reliance @{ */
        if (intent.getExtras() != null && VideoProfile.isBidirectional(intent.getExtras().getInt(
                TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE))
                && CallUtil.isBatteryLow(context)) {
            CallUtil.showLowBatteryDialDialog(context, intent, true);
        } else {
            startActivityWithErrorToast(context, intent,
                    R.string.activity_not_available);
        }
        /** @} */
    }

    /**
     * Attempts to start an activity and displays a toast with a provided error message if the
     * activity is not found, instead of throwing an exception.
     *
     * @param context to start the activity with.
     * @param intent  to start the activity with.
     * @param msgId   Resource ID of the string to display in an error message if the activity is not
     *                found.
     */
    public static void startActivityWithErrorToast(
            final Context context, final Intent intent, int msgId) {
        try {
            if ((Intent.ACTION_CALL.equals(intent.getAction()))) {
                /** UNISOC: add for bug 1091048 @{ */
                /** UNISOC: modify for bug 1153811 @{ */
                TelephonyManager telephonyManager = (TelephonyManager)
                        context.getSystemService(Context.TELEPHONY_SERVICE);
                int phoneCount = telephonyManager.getPhoneCount();
                boolean isImsRegistered;
                if (phoneCount > 1) {
                    isImsRegistered =  ImsManagerEx.isImsRegisteredForPhone(SLOT_ID_ONE) || ImsManagerEx.isImsRegisteredForPhone(SLOT_ID_TWO);
                } else {
                    isImsRegistered = TelephonyManager.from(context).isImsRegistered();
                }
                /* @} */
                final boolean isAirplaneModeOn = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.AIRPLANE_MODE_ON, 0) != 0;
                String phoneNumber = intent.getData().getSchemeSpecificPart();
                if((isAirplaneModeOn && !PhoneNumberUtils.isEmergencyNumber(phoneNumber) && !isImsRegistered)) {
                    Toast.makeText(context, R.string.dialog_make_call_airplane_mode_message,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                /* @} */
                // All dialer-initiated calls should pass the touch point to the InCallUI
                Point touchPoint = TouchPointManager.getInstance().getPoint();
                if (touchPoint.x != 0 || touchPoint.y != 0) {
                    Bundle extras;
                    // Make sure to not accidentally clobber any existing extras
                    if (intent.hasExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)) {
                        extras = intent.getParcelableExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS);
                    } else {
                        extras = new Bundle();
                    }
                    extras.putParcelable(TouchPointManager.TOUCH_POINT, touchPoint);
                    intent.putExtra(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);
                }

                if (shouldWarnForOutgoingWps(context, intent.getData().getSchemeSpecificPart())) {
                    LogUtil.i(
                            "DialUtils.startActivityWithErrorToast",
                            "showing outgoing WPS dialog before placing call");
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setMessage(R.string.outgoing_wps_warning);
                    builder.setPositiveButton(
                            R.string.dialog_continue,
                            new OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    placeCallOrMakeToast(context, intent);
                                }
                            });
                    builder.setNegativeButton(android.R.string.cancel, null);
                    builder.create().show();
                } else {
                    placeCallOrMakeToast(context, intent);
                }
            } else {
                context.startActivity(intent);
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, msgId, Toast.LENGTH_SHORT).show();
        /**UNISOC:modify for the bug 1131060 @{*/
        } catch (SecurityException e) {
            Toast.makeText(context, R.string.activity_not_security, Toast.LENGTH_SHORT).show();
        }
        /**@}*/
    }

    private static void placeCallOrMakeToast(Context context, Intent intent) {
        /** UNISOC: bug1110945 DUT shows abnormal behaviour with calllog items in mentioned scenario. @{*/
        Uri handle = intent.getData();
        boolean isEmergency = PhoneNumberUtils.isEmergencyNumber(handle == null ? "" : handle.getSchemeSpecificPart());
        if (!isEmergency && CallList.getInstance().getIncomingCall() != null) {
            Toast.makeText(context, R.string.call_cannot_be_sent, Toast.LENGTH_SHORT).show();
            return;
        }
        /** @}*/
        final boolean hasCallPermission = TelecomUtil.placeCall(context, intent);
        if (!hasCallPermission) {
            // TODO: Make calling activity show request permission dialog and handle
            // callback results appropriately.
            Toast.makeText(context, "Cannot place call without Phone permission", Toast.LENGTH_SHORT)
                    .show();
        }
    }

    /**
     * Returns whether the user should be warned about an outgoing WPS call. This checks if there is a
     * currently active call over LTE. Regardless of the country or carrier, the radio will drop an
     * active LTE call if a WPS number is dialed, so this warning is necessary.
     */
    @SuppressLint("MissingPermission")
    private static boolean shouldWarnForOutgoingWps(Context context, String number) {
        if (number != null && number.startsWith(WPS_PREFIX)) {
            TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
            boolean isOnVolte =
                    telephonyManager.getVoiceNetworkType() == TelephonyManager.NETWORK_TYPE_LTE;
            boolean hasCurrentActiveCall =
                    telephonyManager.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK;
            return isOnVolte && hasCurrentActiveCall;
        }
        return false;
    }

    /**
     * Closes an {@link AutoCloseable}, silently ignoring any checked exceptions. Does nothing if
     * null.
     *
     * @param closeable to close.
     */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Joins a list of {@link CharSequence} into a single {@link CharSequence} seperated by ", ".
     *
     * @param list List of char sequences to join.
     * @return Joined char sequences.
     */
    public static CharSequence join(Iterable<CharSequence> list) {
        StringBuilder sb = new StringBuilder();
        final BidiFormatter formatter = BidiFormatter.getInstance();
        final CharSequence separator = ", ";

        Iterator<CharSequence> itr = list.iterator();
        boolean firstTime = true;
        while (itr.hasNext()) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(separator);
            }
            // Unicode wrap the elements of the list to respect RTL for individual strings.
            sb.append(
                    formatter.unicodeWrap(itr.next().toString(), TextDirectionHeuristics.FIRSTSTRONG_LTR));
        }

        // Unicode wrap the joined value, to respect locale's RTL ordering for the whole list.
        return formatter.unicodeWrap(sb.toString());
    }

    /* UNISOC: Bug 1072694 androidq porting feature for FEATURE_SIM_CARD_IDENTIFICATION_IN_CALLLOG */
    private static HashMap<Integer, SubscriptionInfo> sSubInfoMap =
            new HashMap<Integer, SubscriptionInfo>();
    private static List<SubscriptionInfo> sSubInfos = new ArrayList<SubscriptionInfo>();
    private static boolean sPermissionFlag = false;

    public static synchronized SubscriptionInfo getActiveSubscriptionInfo(
            Context context, int slotId, boolean forceReload) {
        if ((forceReload || !sPermissionFlag)
                && context.checkSelfPermission(permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            sPermissionFlag = true;
            DialerUtils.sSubInfoMap.clear();
            final SubscriptionManager subscriptionManager = SubscriptionManager
                    .from(context);
            List<SubscriptionInfo> subInfos = subscriptionManager
                    .getActiveSubscriptionInfoList();
            sSubInfos = subInfos;
            if (subInfos != null) {
                for (SubscriptionInfo subInfo : subInfos) {
                    int phoneId = subInfo.getSimSlotIndex();
                    DialerUtils.sSubInfoMap.put(phoneId, subInfo);
                }
            }
        }
        return DialerUtils.sSubInfoMap.get(slotId);
    }

    public static List<SubscriptionInfo> getActiveSubscriptionInfoList(Context context) {
        if (!sPermissionFlag) {
            getActiveSubscriptionInfo(context, -1, true);
        }
        return sSubInfos;
    }
    /* @} */

    public static void showInputMethod(View view) {
        final InputMethodManager imm =
                (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, 0);
        }
    }

    public static void hideInputMethod(View view) {
        final InputMethodManager imm =
                (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Create a File in the cache directory that Dialer's FileProvider knows about so they can be
     * shared to other apps.
     */
    public static File createShareableFile(Context context) {
        long fileId = Math.abs(RANDOM.nextLong());
        File parentDir = new File(context.getCacheDir(), FILE_PROVIDER_CACHE_DIR);
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }
        return new File(parentDir, String.valueOf(fileId));
    }


    /**
     * UNISOC: show dialog for PhoneAccount Select Dialog for bug600436 @{
     */
    public static void showSelectPhoneAccountDialog(Context context,
                                                    List<PhoneAccountHandle> accountHandles, SelectPhoneAccountDialogFragment.SelectPhoneAccountListener callback) {
        DialogFragment dialogFragment = SelectPhoneAccountDialogFragment.newInstance(
                SelectPhoneAccountDialogOptionsUtil.builderWithAccounts(
                        accountHandles).build(), callback);
        dialogFragment.show(((Activity) context).getFragmentManager(), SELECT_FRAGMENT_TAG);
    }

    public static PhoneAccountHandle getCallingPhoneAccountHandle(Context context) {
        List<PhoneAccountHandle> phoneAccoutHandleList =
                TelecomUtil.getCallCapablePhoneAccounts(context);
        for (Iterator<PhoneAccountHandle> i = phoneAccoutHandleList.iterator(); i.hasNext(); ) {
            PhoneAccountHandle phoneAccountHandle = i.next();
            //TODO need incallUI importing this method
            int slotId = getPhoneIdByAccountHandle(context, phoneAccountHandle);

            if (TelephonyManager.getDefault().getCallStateForSlot(slotId)
                    != TelephonyManager.CALL_STATE_IDLE) {
                return phoneAccountHandle;
            }
        }
        return null;
    }

    public static void startVoiceMailSettingActivity(Context context,
                                                     PhoneAccountHandle phoneAccountHandle) {
        TelecomManager telecomManager = (TelecomManager) context
                .getSystemService(Context.TELECOM_SERVICE);
        PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);
        TelephonyManager tm = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);
        int subId = tm.getSubIdForPhoneAccount(phoneAccount);
        final Intent voiceMailSettingIntent = new Intent();
        voiceMailSettingIntent.putExtra(SUB_ID_EXTRA, subId);
        voiceMailSettingIntent.setAction(Intent.ACTION_MAIN);
        voiceMailSettingIntent.addCategory(Intent.CATEGORY_DEVELOPMENT_PREFERENCE);
        voiceMailSettingIntent.setComponent(new ComponentName(PHONE_PACKAGE_NAME,
                VOICE_MAIL_SETTINGS_ACTIVITY));
        DialerUtils.startActivityWithErrorToast(context, voiceMailSettingIntent);
        Toast.makeText(context, R.string.voicemail_status_configure_voicemail,
                Toast.LENGTH_SHORT).show();
    }

    public static boolean isPhoneInUse(Context context) {
        return TelecomUtil.isInCall(context);
    }
    /** @} */

    /**
     * UNISOC: FEATURE_IP_DIAL @{
     */
    public static void callVoicemail(Context context, PhoneAccountHandle phoneAccountHandle) {
        if (phoneAccountHandle == null) {
            DialerUtils.startActivityWithErrorToast(context,
                    new CallIntentBuilder(CallUtil.getVoicemailUri(), CallInitiationType.Type.DIALPAD)
                            .build());
        } else {
            if (TextUtils.isEmpty(TelecomUtil.getVoicemailNumber(context, phoneAccountHandle))) {
                DialerUtils.startVoiceMailSettingActivity(context, phoneAccountHandle);
            } else {
                Intent voiceMailIntent = new CallIntentBuilder(CallUtil.getVoicemailUri(),
                        CallInitiationType.Type.DIALPAD).build();
                voiceMailIntent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
                DialerUtils.startActivityWithErrorToast(context, voiceMailIntent);
            }
        }
    }
    /** @} */

    /**
     * UNISOC: bug895150 Card one Unicom card two mobile, dial the number to enter, click on the
     * "initiate video call" button above, pop-up card box
     * Add for bug 771875. @{
     */
    public static PhoneAccountHandle getPhoneAccountHandleBySlotId(Context context, int SlotIndx) {
        List<PhoneAccountHandle> phoneAccoutHandleList =
                TelecomUtil.getCallCapablePhoneAccounts(context);
        for (Iterator<PhoneAccountHandle> i = phoneAccoutHandleList.iterator(); i.hasNext(); ) {
            PhoneAccountHandle phoneAccountHandle = i.next();
            int slotId = getPhoneIdByAccountHandle(context, phoneAccountHandle);

            if (slotId == SlotIndx) {
                Log.d("DialerUtils", "You will get SlotIndx:" + SlotIndx + "'s PhoneAccountHandle");
                return phoneAccountHandle;
            }
        }
        return null;
    }
    /* @} */

    /* UNISOC: add for bug771875 @{ */
    public static int getRegisteredImsSlotForDualLteModem() {
        Log.d("DialerUtils", " SlotForDualLteModem=" + mRegisteredImsSlot);
        return mRegisteredImsSlot;
    }
    /* @} */

    private static IImsServiceEx mIImsServiceEx;
    private static boolean mIsVideoEnable = false;
    private static boolean mIsImsListenerRegistered;

    private static IImsRegisterListener.Stub
            mImsUtListenerExBinder = new IImsRegisterListener.Stub() {
        @Override
        public void imsRegisterStateChange(boolean isRegistered) {
            Log.d("DialerUtils", "imsRegisterStateChange. isRegistered: "
                    + isRegistered);
            /* UNISOC: add for bug771875 & 823081 & 1164018 @{ */
            mIsVideoEnable = ImsManagerEx.isImsRegisteredForPhone(SLOT_ID_ONE)
                    || ImsManagerEx.isImsRegisteredForPhone(SLOT_ID_TWO);
            /** UNISOC: bug905062 Dual mobile card, dialing always asks, closes the card to a video call function,
             * the call record calls out the video call, and the card selection interface pops up. .@{ */
            if (ImsManagerEx.isImsRegisteredForPhone(SLOT_ID_TWO)
                    && ImsManagerEx.isImsRegisteredForPhone(SLOT_ID_ONE)) {
                if (isVideoCallEnable(mContext,SLOT_ID_ONE)
                        && isVideoCallEnable(mContext,SLOT_ID_TWO)) {
                    mRegisteredImsSlot = SLOT_ID_ONE_TWO;
                } else if (isVideoCallEnable(mContext,SLOT_ID_ONE)
                        && !isVideoCallEnable(mContext,SLOT_ID_TWO)) {
                    mRegisteredImsSlot = SLOT_ID_ONE;
                } else if (!isVideoCallEnable(mContext,SLOT_ID_ONE)
                        && isVideoCallEnable(mContext,SLOT_ID_TWO)) {
                    mRegisteredImsSlot = SLOT_ID_TWO;
                } else {
                    mRegisteredImsSlot = SLOT_ID_NONE;
                }
            } else if (ImsManagerEx.isImsRegisteredForPhone(SLOT_ID_TWO)
                    && !ImsManagerEx.isImsRegisteredForPhone(SLOT_ID_ONE)){
                if (isVideoCallEnable(mContext,SLOT_ID_TWO)) {
                    mRegisteredImsSlot = SLOT_ID_TWO;
                } else {
                    mRegisteredImsSlot = SLOT_ID_NONE;
                }
            } else if (!ImsManagerEx.isImsRegisteredForPhone(SLOT_ID_TWO)
                    && ImsManagerEx.isImsRegisteredForPhone(SLOT_ID_ONE)){
                if (isVideoCallEnable(mContext,SLOT_ID_ONE)) {
                    mRegisteredImsSlot = SLOT_ID_ONE;
                } else {
                    mRegisteredImsSlot = SLOT_ID_NONE;
                }
            } else {
                mRegisteredImsSlot = SLOT_ID_NONE;
            }
            /** @} */
            Log.d("DialerUtils", "ImsManagerEx.isImsRegisteredForPhone: "
                    + ImsManagerEx.isImsRegisteredForPhone(SLOT_ID_ONE)+" "
                    + ImsManagerEx.isImsRegisteredForPhone(SLOT_ID_TWO));
            /* @} */
        }
    };

    /** UNISOC: bug905062 Dual mobile card, dialing always asks, closes the card to a video call function,
     * the call record calls out the video call, and the card selection interface pops up. .@{ */
    private static boolean isVideoCallEnable(Context context,int slot) {
        if (!PermissionsUtil.hasPermission(context, android.Manifest.permission.READ_PHONE_STATE)) {
            return false;
        }
        TelecomManager telecommMgr = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        if (telecommMgr == null) {
            return false;
        }
        List<PhoneAccountHandle> accountHandles = telecommMgr.getCallCapablePhoneAccounts();
        PhoneAccount account = telecommMgr.getPhoneAccount(accountHandles.get(slot));
        if (account != null) {
            if (account.hasCapabilities(PhoneAccount.CAPABILITY_VIDEO_CALLING)) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
    /** @} */

    public static void unRegisterImsListener(Context context) {
        // UNISOC: modify for bug740522
        if (PermissionsUtil.hasPhonePermissions(context)
                && context.checkSelfPermission(permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                if (mIsImsListenerRegistered) {
                    mIsImsListenerRegistered = false;
                    mIsVideoEnable = false;
                    mIImsServiceEx
                            .unregisterforImsRegisterStateChanged(mImsUtListenerExBinder);
                }
            } catch (RemoteException e) {
                Log.e("DialerUtils", "regiseterforImsException: " + e);
            } finally {
                mContext = null;
            }
        }
    }

    public static void tryRegisterImsListener(Context context) {
        mContext = context;
        // UNISOC: modify for bug740522
        if (PermissionsUtil.hasPhonePermissions(context)
                && context.checkSelfPermission(permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            mIImsServiceEx = ImsManagerEx.getIImsServiceEx();
            if (mIImsServiceEx != null) {
                try {
                    if (!mIsImsListenerRegistered) {
                        mIsImsListenerRegistered = true;
                        mIImsServiceEx
                                .registerforImsRegisterStateChanged(mImsUtListenerExBinder);
                    }
                } catch (RemoteException e) {
                    Log.e("DialerUtils", "regiseterforImsException: " + e);
                }
            }
        }
    }
    /** @} */

    /**
     * UNISOC: feature porting: bug1072689 Add method to get phone id by PhoneAccountHandle.
     */
    public static int getPhoneIdByAccountHandle(Context context,
                                                PhoneAccountHandle phoneAcountHandle) {
        if (phoneAcountHandle != null) {
            String iccId = phoneAcountHandle.getId();
            List<SubscriptionInfo> result = getActiveSubscriptionInfoList(context);
            if (result != null) {
                for (SubscriptionInfo subInfo : result) {
                    if (iccId != null && subInfo != null && iccId.equals(subInfo.getIccId())) {
                        return subInfo.getSimSlotIndex();
                    }
                }
            } else {
                LogUtil.i("InCallUiUtils.getPhoneIdByAccountHandle", "active subscription info list is null");
            }
        }
        return -1;
    }
    /** @} */
}
