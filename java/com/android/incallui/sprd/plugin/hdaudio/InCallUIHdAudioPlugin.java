package com.android.incallui.sprd.plugin.hdaudio;

import android.os.AsyncResult;
import android.os.Looper;
import android.content.Intent;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.incallui.call.DialerCall;
import com.android.sprd.telephony.RadioInteractor;
import com.android.sprd.telephony.RadioInteractorCallbackListener;

/**
 * Various utilities for dealing with phone number strings.
 */
public class InCallUIHdAudioPlugin extends InCallUIHdAudioHelper {
    private static final String TAG = "[InCallUIHdAudioPlugin]";

    private boolean hdVoiceState = false;
    private boolean hasListenedHdStatus = false;
    private TelephonyManager telephonyManager;
    private RadioInteractor radioInteractor;
    private RadioInteractorCallbackListener[] callbackListeners;

    private HDStatusListener hDStatusListener;

    protected InCallUIHdAudioPlugin() {
    }

    /* UNISOC: add for bug1173199 1190740 @{*/
    public void setListener(HDStatusListener hDStatusListener) {
        log("setListener");
        this.hDStatusListener = hDStatusListener;
        if (hDStatusListener != null && hdVoiceState) {
            hDStatusListener.onHDstatusUpdated(hdVoiceState);
        }
    }

    public void registerHdStatusChangedEvent(Context context) {
        if (hasListenedHdStatus) {
            if (hDStatusListener != null) {
                hDStatusListener.onHDstatusUpdated(hdVoiceState);
            }
            return;
        }
        Log.i(TAG,"registerHdStatusChangedEvent  hasListenedHdStatus = " + hasListenedHdStatus);
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        hasListenedHdStatus = true;
        int phoneCount = telephonyManager.getPhoneCount();
        callbackListeners = new RadioInteractorCallbackListener[phoneCount];
        radioInteractor = new RadioInteractor(context);
        for (int i = 0; i < phoneCount; i++) {
            callbackListeners[i] = new RadioInteractorCallbackListener(i, Looper.getMainLooper()) {
                @Override
                public void onHdStatusChangedEvent(Object data) {
                    if (data != null) {
                        AsyncResult ar;
                        ar = (AsyncResult) data;
                        int hdStatus = (int) ar.result;
                        Log.i(TAG,"RadioInteractorCallbackListener onHdStatusChangedEvent. hdStatus:" + hdStatus + ", hDStatusListener:" + hDStatusListener);
                        hdVoiceState = hdStatus == 1;
                        if (hDStatusListener != null) {
                            hDStatusListener.onHDstatusUpdated(hdVoiceState);
                        }
                        Intent intent = new Intent("android.intent.action.HIGH_DEF_AUDIO_SUPPORT"/*TelephonyIntentsEx.ACTION_HIGH_DEF_AUDIO_SUPPORT*/);
                        //UNISOC: add for bug 973929
                        intent.putExtra("isHdVoiceSupport"/*TelephonyIntentsEx.EXTRA_HIGH_DEF_AUDIO*/, hdVoiceState);
                        context.sendBroadcast(intent);
                    }
                }
            };
            if (radioInteractor != null) {
                radioInteractor.listen(callbackListeners[i],
                        RadioInteractorCallbackListener.LISTEN_HD_STATUS_CHANGED_EVENT,
                        true);
            }
        }
    }

    public void unRegisterHdStatusChangedEvent(Context context) {
        log("unRegisterHdStatusChangedEvent");
        if (!hasListenedHdStatus) {
            return;
        }
        hasListenedHdStatus = false;
        hdVoiceState = false;
        hDStatusListener = null;
        int phoneCount = telephonyManager.getPhoneCount();
        for (int i = 0; i < phoneCount; i++) {
            if (radioInteractor != null && callbackListeners != null && callbackListeners[i] != null) {
                radioInteractor.listen(callbackListeners[i],
                        RadioInteractorCallbackListener.LISTEN_NONE);
            }
        }

        Intent intent = new Intent( "android.intent.action.HIGH_DEF_AUDIO_SUPPORT"/*TelephonyIntentsEx.ACTION_HIGH_DEF_AUDIO_SUPPORT*/);
        intent.putExtra("isHdVoiceSupport", hdVoiceState);
        context.sendBroadcast(intent);
        if (hDStatusListener != null) {
            hDStatusListener.onHDstatusUpdated(hdVoiceState);
            hDStatusListener = null;
        }
    }
    /*@}*/

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
