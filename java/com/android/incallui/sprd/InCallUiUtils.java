package com.android.incallui.sprd;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telecom.PhoneAccountHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.CarrierConfigManagerEx;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.contacts.ContactsComponent;
import com.android.incallui.call.CallList;
import com.android.incallui.ContactInfoCache;//UNISOC:add for bug940943

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.android.dialer.location.GeoUtil;
import com.android.dialer.util.DialerUtils;
import com.android.incallui.call.DialerCall;
import com.android.dialer.app.R;
import com.android.i18n.phonenumbers.PhoneNumberUtil;

import android.content.SharedPreferences;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import com.android.incallui.call.state.DialerCallState;
import android.widget.TextView;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.sprd.plugin.CallerAddress.CallerAddressHelper;

import android.telephony.TelephonyManagerEx;
import android.telecom.TelecomManager;
import android.telecom.PhoneAccount;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SprdSensor;
import android.telecom.Call.Details;

/**
 * General purpose utility methods for the InCallUI.
 */
public class InCallUiUtils {
   /* UNISOC Feature Porting: Vibrate when call connected or disconnected feature. @{ */
    public static final String VIBRATION_FEEDBACK_FOR_DISCONNECT_PREFERENCES_NAME =
            "call_disconnection_prompt_key";
    public static final String VIBRATION_FEEDBACK_FOR_CONNECT_PREFERENCES_NAME =
            "call_connection_prompt_key";
    public static final int VIBRATE_DURATION = 100; // vibrate for 100ms.@
    /* @} */

    private static HashMap<Integer, SubscriptionInfo> sSubInfoMap =
            new HashMap<Integer, SubscriptionInfo>();
    private static List<SubscriptionInfo> sSubInfos = new ArrayList<SubscriptionInfo>();
    private static boolean sPermissionFlag = false;

    // UNISOC: add for bug930591
    private static final char PREFIX_PLUS = '+';
    private static final String PREFIX_DOUBLE_ZERO = "00";
    private static final char PAUSE = ',';
    private static final char WAIT = ';';
   /**
    *UNISOC Feature Porting: Fade in ringer volume when incoming calls
    */
    private static final String ACTION_FADE_IN_RINGER = "android.telecom.action.FADE_IN_RINGER";
    public static final int FLAG_FADE_IN = 1;
    /**
     * UNISOC: Flip to silence from incoming calls.
     */
    private static final String ACTION_SILENT_CALL_BY_FILPING =
            "android.telecom.action.SILENT_CALL_BY_FILPING";
    private static final String TELECOM_PACKAGE = "com.android.server.telecom";

    public static final int FLAG_SILENT_FLIPING = 0;

    public static boolean isSupportMp3ForCallRecord(Context context) {
        if (context.getResources().getBoolean(R.bool.config_support_mp3_for_call_recorder)) {
            return true;
        }
        return false;
    }

     /**
     * Add method to get phone id by PhoneAccountHandle.
     */
    public static int getPhoneIdByAccountHandle(Context context,
        PhoneAccountHandle phoneAcountHandle) {
        if (phoneAcountHandle != null) {
            String iccId = phoneAcountHandle.getId();
            List<SubscriptionInfo> result = DialerUtils.getActiveSubscriptionInfoList(context);

            if (result != null) {
                for (SubscriptionInfo subInfo : result) {
                    if (iccId != null && subInfo != null && iccId.equals(subInfo.getIccId())) {
                        return subInfo.getSimSlotIndex();
                    }
                }
            // UNISOC: add for bug931964
            } else {
                LogUtil.i("InCallUiUtils.getPhoneIdByAccountHandle", "active subscription info list is null");
            }
        }
        return -1;
    }

    /**
     * UNISOC: Add switch for automatic record feature.
     */
    public static boolean isSupportAutomaticCallRecord(Context context) {
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        if (configManager.getConfigForDefaultPhone() != null) {
            return configManager.getConfigForDefaultPhone().getBoolean(
                   CarrierConfigManagerEx.KEY_FEATURE_AUTOMATIC_CALL_RECORD_ENABLED_BOOL);
        }
        return false;
    }
    /* @} */

    // UNISOC: add for bug930591
    public static String removeNonNumericForNumber(String number) {
        if (number == null || number.length() == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int len = number.length();
        int i = 0;
        if (number.charAt(i) == PREFIX_PLUS) {
            sb.append(PREFIX_PLUS);
            i++;
        }
        for (; i < len; i++) {
            char c = number.charAt(i);
            if ((c >= '0' && c <= '9') || c == PAUSE || c == WAIT) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    /* UNISOC: add for feature support reject with message on incoming call notification {@*/
    public static boolean shouldAddRejectMessageButton(Context context) {
        return context.getResources().getBoolean(R.bool.incallui_notification_add_reject_message_button);
    }
    /* @} */
    /**
     * UNISOC: Add method to get SubId from PhoneAccountHandle.
     */
    public static int getSubIdForPhoneAccountHandle(Context context, PhoneAccountHandle handle) {
        List<SubscriptionInfo> result = DialerUtils.getActiveSubscriptionInfoList(context); //UNISOC:modify for bug1130914

        if (result != null && handle != null) {//UNISOC:add for bug1174935
            String iccId = handle.getId();
            for (SubscriptionInfo subInfo : result) {
                if (iccId != null && subInfo != null && iccId.equals(subInfo.getIccId())) {
                    return subInfo.getSubscriptionId();
                }
            }
        }

        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }
    /* @} */

    /* UNISOC Feature Porting: FL0108160005 Hangup all calls for orange case. */
    public static boolean isSupportHangupAll(Context context) {
        return context.getResources().getBoolean(R.bool.config_is_support_hangup_all_feature);
    }
    /* @} */

    /*UNISOC: add for feature FL1000062299 {@*/
    public static boolean shouldUpdateConferenceUIWithOneParticipant( Context context) {
        return context.getResources().getBoolean(R.bool.update_incallui_to_usual_call);
    }
    // UNISOC:add for bug940943
    /**
     * Get CallerInfo for Conference Child Call.
     * */
    public static ContactInfoCache.ContactCacheEntry getCallerInfo(Context context) {
        CallList callList = CallList.getInstance();
        if (context == null || callList == null || callList.getAllConferenceCall() == null
                || callList.getAllConferenceCall().getChildCallIds() == null) {
            return null;
        }
        String[] callerIds = null;
        callerIds = (String[]) callList.getAllConferenceCall().getChildCallIds().toArray(new String[0]);
        final ContactInfoCache.ContactCacheEntry contactCache = ContactInfoCache.getInstance(context).
                getInfo(callerIds[0]);
        if (contactCache != null) {
            return contactCache;
        }
        return null;
    }

    /**
     * Get the name to display for a call.
     */
    public static String getNameForCall(ContactInfoCache.ContactCacheEntry contactInfo,
                                        Context context) {
        String preferredName =
                ContactsComponent.get(context)
            .contactDisplayPreferences()
            .getDisplayName(contactInfo.namePrimary, contactInfo.nameAlternative);
        if (TextUtils.isEmpty(preferredName)) {
            return contactInfo.number;
        }
        return preferredName;
    }
    /* @} */

    /* UNISOC Feature Porting: Hide recorder feature . @{ */
    public static boolean isRecorderEnabled(Context context) {
        return context.getResources().getBoolean(R.bool.config_is_recorder_enabled_feature);
    }
    /* @} */

    /* UNISOC: add for feature FL1000060357 */
    public static boolean shouldShowConferenceWithOneParticipant(Context context) {
        return context.getResources().getBoolean(R.bool.config_is_show_conference_manager);
    }
    /* @} */

    /**
     * Add method to get phone id with current calls.
     */
    public static int getCurrentPhoneId(Context context) {
        DialerCall call = CallList.getInstance().getFirstCall();

        if (call != null && call.getAccountHandle() != null) {
            String iccId = call.getAccountHandle().getId();
            List<SubscriptionInfo> result = DialerUtils
                    .getActiveSubscriptionInfoList(context);

            if (result != null) {
                for (SubscriptionInfo subInfo : result) {
                    if (iccId != null && subInfo != null && iccId.equals(subInfo.getIccId())) {
                        return subInfo.getSimSlotIndex();
                    }
                }
            }
        }
        return -1;
    }

    /*UNISOC Featrure Porting: add for feature FL1000060387 1155897 @{*/
    public static boolean isSupportRingTone(Context context, DialerCall call) {
        if (call == null) {
            call = CallList.getInstance().getFirstCall();
        }
        if (call == null || context == null) {
            LogUtil.i("InCallUtils", "isSupportRingTone call is null");
            return false;
        }
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        // UNISOC: modify for bug1155350
        PhoneAccountHandle accountHandle = call.getAccountHandle();
        int currentSubId = getSubIdForPhoneAccountHandle(context,accountHandle);
        if (-1 == currentSubId) {
            LogUtil.i("InCallUtils", "isSupportRingTone currentSubId = -1");
        } else if (configManager.getConfigForSubId(currentSubId) != null) {
            return configManager.getConfigForSubId(currentSubId).getBoolean(
                    CarrierConfigManagerEx.KEY_CARRIER_SUPPORTS_VIDEO_RING_TONE);
        } else {
            LogUtil.d("InCallUtils", "isSupportRingTone getConfigForDefaultPhone = null");
        }
        return false;
    }/*@}*/

   /*UNISOC CMCC featrure porting: FL0108130015 not show hold button when in the videocall @{*/
    public static boolean showHoldOnButton(Context context, boolean isVideoCall, DialerCall call) {
        if (isVideoCall) {
            CarrierConfigManager cm = (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            // UNISOC: modify for bug1176470
            PhoneAccountHandle accountHandle = call.getAccountHandle();
            int currentSubId = getSubIdForPhoneAccountHandle(context,accountHandle);
            if (-1 == currentSubId) {
                LogUtil.i("InCallUtils", "isSupportRingTone currentSubId = -1");
            } else if (cm.getConfigForSubId(currentSubId) != null) {
                return cm.getConfigForSubId(currentSubId).getBoolean(CarrierConfigManagerEx.KEY_CARRIER_SHOW_HOLD_BUTTON);
            } else{
                LogUtil.d("InCallUtils","showHoldOnButton getConfigForDefaultPhone = null");
            }
        }
        return true;
    }/*@}*/

    /* UNISOC Feature Porting: Vibrate when call connected or disconnected feature. @{ */
    /**
     * @return Whether support call connected or disconnected feature.
     */
    public static boolean isSupportVibrateForCallConnectionFeature(Context context) {
        //Due to compile report not find symbol error for CarrierConfigManagerEx, the default is true during debug.
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        if (configManager.getConfigForDefaultPhone() != null) {
            return configManager.getConfigForDefaultPhone().getBoolean(
                    CarrierConfigManagerEx.KEY_FEATURE_VIBRATE_FOR_CALL_CONNECTION_BOOL);
        }
        return false;
    }
    /**
     * Add method for Vibration feedback
     */
    public static void vibrateForCallStateChange(Context context, DialerCall call, String preferenceName) {
        // UNISOC: modify for bug1111081
        Boolean vibrate = false;
        try {
            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            vibrate = sp.getBoolean(preferenceName, false);
        } catch (Exception e) {
            LogUtil.e("InCallUiUtils.vibrateForCallStateChange", "Exception:" + e.getMessage());
        }
        if (call == null || !vibrate) {
            return;
        }
        boolean shouldVibrate = false;
        if (TextUtils.equals(VIBRATION_FEEDBACK_FOR_DISCONNECT_PREFERENCES_NAME, preferenceName)) {
            if (call.getState() == DialerCallState.DISCONNECTED && !call.isConferenceCall()) {
                LogUtil.d("InCallUiUtils.vibrateForCallStateChange",
                        "vibrate for call state changed to disconnected. call: " + call);
                shouldVibrate = true;
            }
        }else{
            if (call.getState() == DialerCallState.ACTIVE && !call.isConferenceCall()) {
                LogUtil.d("InCallUiUtils.vibrateForCallStateChange",
                        "vibrate for call state changed to active. call : " + call);
                shouldVibrate = true;
            }
        }
        if (shouldVibrate) {
            Vibrator vibrator = (Vibrator) context
                    .getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }
    /*@}*/
    /* UNISOC Feature Porting: Display caller address for phone number feature. @{ */
    public static void setCallerAddress(Context context, PrimaryInfo primaryInfo,
                                        TextView geocodeView) {
        if (geocodeView != null) {
            String newDescription = "";
            String oldDescription = geocodeView.getText().toString();
            if (primaryInfo.nameIsNumber()) {
                newDescription = CallerAddressHelper.getsInstance(context)
                        .getCallerAddress(context, primaryInfo.name());
            } else {
                newDescription = CallerAddressHelper.getsInstance(context)
                        .getCallerAddress(context, primaryInfo.number());
            }
            if (newDescription != null && !oldDescription.equals(newDescription)) {
                LogUtil.d("InCallFragment.setCallerAddress", "newDescription:" + newDescription);
                geocodeView.setText(newDescription);
            }
        }
    }
    /* @} */
   /* UNISOC Feature Porting: Show call elapsed time feature. @{ */
    public static boolean isShowCallElapsedTime(Context context) {
        return context.getResources().getBoolean(R.bool.config_is_show_call_elapsed_time_feature);
    }
    /* @} */

   /**
     * Add method to get slot info by PhoneAccountHandle.
     */
    public static String getSlotInfoByPhoneAccountHandle(
            Context context, PhoneAccountHandle accountHandle) {
        if (accountHandle == null) {
            return "";
        }
        return InCallUiUtils.getSlotInfoBySubId(context,
                getSubIdForPhoneAccountHandle(context, accountHandle));
    }
    /**
     * Add method to get phone account label by call.
     */
    public static String getPhoneAccountLabel(DialerCall call, Context context) {
        String label = "";
        if (call == null) {
            return label;
        }
        PhoneAccountHandle accountHandle = call.getAccountHandle();
        if (accountHandle == null) {
            return null;
        }
        PhoneAccount account = context.getSystemService(TelecomManager.class)
                .getPhoneAccount(accountHandle);

        if (account != null && !TextUtils.isEmpty(account.getLabel())) {
            label = account.getLabel().toString();
        }
        return label;
    }
   /* UNISOC Feature Porting: Flip to silence from incoming calls. @{ */
    public static boolean isFlipToSilentCallEnabled(Context context) {
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        if (configManager.getConfigForDefaultPhone() != null) {
            return configManager.getConfigForDefaultPhone().getBoolean(
                    CarrierConfigManagerEx.KEY_FEATURE_FLIP_SILENT_INCOMING_CALL_ENABLED_BOOL);
        }
        return false;
    }

    public static boolean isSupportFlipToMute(Context context) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(
                Context.SENSOR_SERVICE);
        Sensor sensor = sensorManager.getDefaultSensor(SprdSensor.TYPE_SPRDHUB_FLIP);
        if (sensor == null) {
            return false;
        }
        return true;
    }

    public static Intent getIntentForStartingActivity(int flag) {
        Intent intent = null;
        if (flag == FLAG_SILENT_FLIPING) {
            intent = new Intent(ACTION_SILENT_CALL_BY_FILPING);
            intent.addFlags(FLAG_SILENT_FLIPING);
        }else{
            intent = new Intent(ACTION_FADE_IN_RINGER);
            intent.addFlags(FLAG_FADE_IN);
        }

        intent.setPackage(TELECOM_PACKAGE);
        return intent;
    }

    /**
     * Add method to get slot info by call.
     */
    public static String getSlotInfoByCall(Context context, DialerCall call) {
        if (call == null) {
            return "";
        }
        int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (call != null && call.getAccountHandle() != null) {
            subId = InCallUiUtils.getSubIdForPhoneAccountHandle(context, call.getAccountHandle());
        }
       return getSlotInfoBySubId(context, subId);
    }

        /* @} */
    /**
     * Add method to get slot info by subId.
     */
    public static String getSlotInfoBySubId(Context context, int subId) {
        //UNISOC:modify for bug1147838
        String card_string;
        if(context.getResources().getBoolean(R.bool.config_is_show_main_vice_card_feature)){
            int defaultDataSubId = SubscriptionManager.from(context).getDefaultDataSubscriptionId();
            Boolean isPrimaryCard = (defaultDataSubId == subId);
            LogUtil.i("InCallUtils"," defaultDataSubId:"+defaultDataSubId+" subId:"+subId+" isPrimaryCard:"+isPrimaryCard);
            if (isPrimaryCard) {
                card_string = context.getResources().getString(R.string.main_card_slot);
            } else {
                card_string = context.getResources().getString(R.string.vice_card_slot);
            }
        } else {
            int phoneId = SubscriptionManager.from(context).getPhoneId(subId);
            if (phoneId==0) {
                card_string = context.getResources().getString(R.string.xliff_string1)+ " ";
            } else {
                LogUtil.i("InCallUtils","getSlotInfoBySubId  phoneId:"+phoneId+"subId:"+subId);
                card_string = context.getResources().getString(R.string.xliff_string2)+ " ";
            }
        }
        return card_string;
    }
    /* UNISOC Feature Porting: Fade in ringer volume when incoming calls. @{ */
    public static boolean isFadeInRingerEnabled(Context context) {
        CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        if (configManager.getConfigForDefaultPhone() != null) {
            return configManager.getConfigForDefaultPhone().getBoolean(
                    CarrierConfigManagerEx.KEY_FEATURE_FADE_IN_ENABLED_BOOL);
        }
        return false;
    }

    public static final int[] SENSORHUB_LIST = new int[]{
            SprdSensor.TYPE_SPRDHUB_HAND_UP,
            SprdSensor.TYPE_SPRDHUB_SHAKE,
            Sensor.TYPE_PICK_UP_GESTURE,
            SprdSensor.TYPE_SPRDHUB_FLIP,
            SprdSensor.TYPE_SPRDHUB_TAP,
            Sensor.TYPE_WAKE_GESTURE,
            SprdSensor.TYPE_SPRDHUB_POCKET_MODE
    };

    public static boolean isSupportSensorHub(Context context) {
        if (context == null) {
            return false;
        }
        SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Boolean isSupportSmartControl = false;
        if (sensorManager != null) {
            for (int i = 0; i < SENSORHUB_LIST.length; i++) {
                isSupportSmartControl |= sensorManager.getDefaultSensor(SENSORHUB_LIST[i]) != null;
            }
            return isSupportSmartControl;
        }
        return false;
    }

    /**
     * UNISOC:add for bug1123955.Add method to get SlotId from PhoneAccountHandle.
     */
    public static int getSlotIdForPhoneAccountHandle(Context context, PhoneAccountHandle handle) {
        if (handle != null) {
            String iccId = handle.getId();

            SubscriptionInfo subInfo = SubscriptionManager.from(
                    context).getActiveSubscriptionInfoForIccIndex(iccId);
            if (subInfo != null) {
                return subInfo.getSimSlotIndex();
            }
        }
        return SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    }

    /* UNISOC: add for bug1142555 (812381 & 812244) @{*/
    public static String getPhoneNumberWithoutCountryCode(String phoneNumber, Context context){
        if (phoneNumber == null) {
            return null;
        }
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        String countryIso = GeoUtil.getCurrentCountryIso(context);
        int countryCode = phoneUtil.getCountryCodeForRegion(countryIso);
        LogUtil.i("InCallUtils.getPhoneNumberWithoutCountryCode",
                "countryCode: " + countryCode);
        if (countryCode > 0) {
            String code = "";
            if (phoneNumber.startsWith(String.valueOf(PREFIX_PLUS))) {
                code = String.valueOf(PREFIX_PLUS) + countryCode;
            } else if(phoneNumber.startsWith(PREFIX_DOUBLE_ZERO)) {
                code = PREFIX_DOUBLE_ZERO + countryCode;
            }
            try {
                phoneNumber = phoneNumber.substring(code.length());
            } catch (StringIndexOutOfBoundsException exception) {
                LogUtil.e("InCallUtils.getPhoneNumberWithoutCountryCode", "Exception: " + exception);
            }
            LogUtil.d("InCallUtils.getPhoneNumberWithoutCountryCode", "code: " + code +
                    " Phone Number: " + phoneNumber);
        }
        return phoneNumber;
    }
    /*@}*/

    /**
     * Add method to get sub id with current calls.
     */
    public static int getCurrentSubId(Context context) {  //add for bug990129
        DialerCall call = CallList.getInstance().getFirstCall();
        // UNISOC: add for bug1149410
        if (call == null) {
            call = CallList.getInstance().getBackgroundCall();
        }
        if (call != null && call.getAccountHandle() != null) {
            String iccId = call.getAccountHandle().getId();
            List<SubscriptionInfo> result = DialerUtils.getActiveSubscriptionInfoList(context);

            if (result != null) {
                for (SubscriptionInfo subInfo : result) {
                    if (iccId != null && subInfo != null && iccId.equals(subInfo.getIccId())) {
                        return subInfo.getSubscriptionId();
                    }
                }
            }
        }
        LogUtil.i("InCallUtils.getCurrentSubId", "get subId is -1");
        return -1;
    }

    //UNISOC:add for bug1138221
    public static boolean ShowInviteButton(Context context,DialerCall call) {
        if (call !=null && context != null) {
            CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            PhoneAccountHandle accountHandle = call.getAccountHandle();
            int currentSubid = getSubIdForPhoneAccountHandle(context,accountHandle);
            LogUtil.d("InCallUtils.ShowInviteButton","currentSubid =" + currentSubid + ",accountHandle.ID =" + (accountHandle == null ? "null" : accountHandle.getId()));
            if(-1 == currentSubid) {
                LogUtil.d("InCallUtils.ShowInviteButton","getcurrentSubid failed");
                return  true;
            }
            if(configManager!=null&&configManager.getConfigForSubId(currentSubid) != null){
                LogUtil.d("InCallUtils.ShowInviteButton","configValue =" + configManager.getConfigForSubId(currentSubid).getBoolean(CarrierConfigManagerEx.KEY_INVITE_BUTTON_SHOULD_BE_SHOW));
                return configManager.getConfigForSubId(currentSubid).getBoolean(CarrierConfigManagerEx.KEY_INVITE_BUTTON_SHOULD_BE_SHOW);
            }else {
                LogUtil.e("InCallUtils","ShowInviteButton getConfigForSubId = null");
            }
        }
        return true;
    }

    //UNISOC:add for bug1166735
    public static boolean ShowShowNumberAndName(Context context,DialerCall call) {
        if (call !=null && context != null) {
            CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
            PhoneAccountHandle accountHandle = call.getAccountHandle();
            int currentSubid = getSubIdForPhoneAccountHandle(context,accountHandle);
            LogUtil.d("InCallUtils.ShowShowNumberAndName","currentSubid =" + currentSubid + ",accountHandle.ID =" + (accountHandle == null ? "null" : accountHandle.getId()));
            if(-1 == currentSubid) {
                LogUtil.d("InCallUtils.ShowShowNumberAndName","getcurrentSubid failed");
                return  false;
            }
            if(configManager!=null&&configManager.getConfigForSubId(currentSubid) != null){
                LogUtil.i("InCallUtils.ShowShowNumberAndName","configValue =" + configManager.getConfigForSubId(currentSubid).getBoolean(CarrierConfigManagerEx.KEY_SHOW_NUMBER_AND_NAME));
                return configManager.getConfigForSubId(currentSubid).getBoolean(CarrierConfigManagerEx.KEY_SHOW_NUMBER_AND_NAME);
            }else {
                LogUtil.e("InCallUtils","ShowShowNumberAndName getConfigForSubId = null");
            }
        }
        return false;
    }

    /* UNISOC:add for bug1174104 */
    public static boolean isShouldshowVowifiIcon(Context context, DialerCall call) {
        Boolean isSupportshowVowifiIcon = context.getResources().getBoolean(R.bool.config_is_support_vowifi_icon_feature);
        Boolean isinVowifiState = call != null ? call.hasProperty(Details.PROPERTY_WIFI) :false;
        return isSupportshowVowifiIcon && isinVowifiState;
    }
    /* @} */

    //UNISOC: add for bug1223743
    public static boolean isSupportSingleVideoCall(Context context, DialerCall call) {
        if (call != null && context != null) {
            CarrierConfigManager configManager = (CarrierConfigManager) context
                    .getSystemService(Context.CARRIER_CONFIG_SERVICE);
            PhoneAccountHandle accountHandle = call.getAccountHandle();
            int currentSubid = getSubIdForPhoneAccountHandle(context, accountHandle);
            LogUtil.d("InCallUtils.isSupportSingleVideoCall","currentSubid =" + currentSubid
                    + ",accountHandle.ID =" + (accountHandle == null ? "null" : accountHandle.getId()));
            if (-1 == currentSubid) {
                LogUtil.d("InCallUtils.isSupportSingleVideoCall","getcurrentSubid failed");
                return  false;
            }
            if (configManager != null && configManager.getConfigForSubId(currentSubid) != null) {
                boolean configValue = configManager.getConfigForSubId(currentSubid)
                        .getBoolean(CarrierConfigManagerEx.KEY_CARRIER_SUPPORT_MULTI_VIDEO_CALL);
                LogUtil.i("InCallUtils.isSupportSingleVideoCall","configValue =" + configValue);
                return !configValue && call.isVideoCall();
            } else {
                LogUtil.e("InCallUtils","isSupportSingleVideoCall getConfigForSubId = null");
            }
        }
        return false;
    }
    //UNISOC: add for bug1245581
    public static boolean HideHDVoiceIcon(Context context, DialerCall call) {
        if (call != null && context != null) {
            CarrierConfigManager configManager = (CarrierConfigManager) context
                    .getSystemService(Context.CARRIER_CONFIG_SERVICE);
            PhoneAccountHandle accountHandle = call.getAccountHandle();
            int currentSubid = getSubIdForPhoneAccountHandle(context, accountHandle);
            LogUtil.i("InCallUtils.HideHDVoiceIcon","currentPhoneId =" + currentSubid
                    + ",accountHandle.ID =" + (accountHandle == null ? "null" : accountHandle.getId()));

            if (currentSubid != -1 && configManager != null && configManager.getConfigForSubId(currentSubid) != null) {
                boolean configValue = configManager.getConfigForSubId(currentSubid)
                        .getBoolean(CarrierConfigManagerEx.KEY_HD_VOICE_ICON_SHOULD_BE_REMOVED);
                LogUtil.i("InCallUtils.HideHDVoiceIcon","configValue =" + configValue);
                return configValue;
            } else {
                LogUtil.e("InCallUtils","HideHDVoiceIcon getConfigForDefaultPhone = null");
            }
        }
        return false;
    }
    //UNISOC: add for bug1243924
    public static boolean isSupportHdAudio(Context context) {
        if (context.getResources().getBoolean(R.bool.config_is_support_hd_audio_feature)) {
            return true;
        }
        DialerCall call = CallList.getInstance().getFirstCall();
        if (call != null) {
            CarrierConfigManager configManager = (CarrierConfigManager) context
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
            PhoneAccountHandle accountHandle = call.getAccountHandle();
            int currentSubid = getSubIdForPhoneAccountHandle(context, accountHandle);
            LogUtil.d("InCallUtils.isSupportHdAudio","currentSubid =" + currentSubid
                    + ",accountHandle.ID =" + (accountHandle == null ? "null" : accountHandle.getId()));
            if (currentSubid != -1 && configManager != null
                && configManager.getConfigForSubId(currentSubid) != null) {
                boolean configValue = configManager.getConfigForSubId(currentSubid)
                        .getBoolean(CarrierConfigManagerEx.KEY_FEATURE_HD_AUDIO);
                LogUtil.i("InCallUtils.isSupportHdAudio","configValue =" + configValue);
                return configValue;
            } else {
                LogUtil.e("InCallUtils","isSupportHdAudio getConfigForSubId = null");
            }
        }
        return false;
    }
}

