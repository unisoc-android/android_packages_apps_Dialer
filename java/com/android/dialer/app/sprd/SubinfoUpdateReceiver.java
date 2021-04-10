package com.android.dialer.app.sprd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.android.dialer.util.DialerUtils;

/* UNISOC: Bug 1072694 androidq porting feature for FEATURE_SIM_CARD_IDENTIFICATION_IN_CALLLOG */
public class SubinfoUpdateReceiver extends BroadcastReceiver{

    @Override
    public void onReceive(Context context, Intent intent) {
        DialerUtils.getActiveSubscriptionInfo(context, -1, true);
    }
}
