package com.android.incallui.sprd.plugin.conferenceState;

import android.content.Context;
import com.android.incallui.Log;
import com.android.incallui.sprd.plugin.conferenceState.ConferenceStateHelper;

/**
 * This class is used to manager InCallUI CMCC Plugin
 */
public class ConferenceStatePlugin extends ConferenceStateHelper{

    private static final String TAG = "ConferenceStatePlugin";

    public ConferenceStatePlugin() {
    }

    /* UNISOC: add for bug692155 */
    public boolean shouldShowParticipantState () {
        return true;
    }

    public boolean isOnlyDislpayActiveOrHoldCall () {
        return true;
    }
    /* @} */
}

