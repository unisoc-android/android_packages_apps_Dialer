package com.android.incallui.sprd.plugin.conferenceState;

import com.android.incallui.Log;
import com.android.dialer.R;

import android.content.Context;

/**
 * This class is used to manager InCallUI CMCC Plugin Helper.
 */
public class ConferenceStateHelper {


    private static final String TAG = "ConferenceStateHelper";
    static ConferenceStateHelper sInstance;

    public static ConferenceStateHelper getInstance(Context context) {
        if (sInstance == null) {
            if(context.getResources().getBoolean(R.bool.config_is_show_conference_state_feature)){
                sInstance = new ConferenceStatePlugin();
            } else {
                sInstance = new ConferenceStateHelper();
            }
            Log.i(TAG, "getInstance [" + sInstance + "]");
        }
        return sInstance;
    }

    public ConferenceStateHelper() {
    }

    /* UNISOC: add for bug692155 */
    public boolean shouldShowParticipantState () {
        return false;
    }

    public boolean isOnlyDislpayActiveOrHoldCall () {
        return false;
    }
    /* @} */
}


