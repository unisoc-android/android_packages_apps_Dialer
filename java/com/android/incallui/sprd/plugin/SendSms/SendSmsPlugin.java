package com.android.incallui.sprd.plugin.SendSms;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.android.incallui.Log;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.sprd.plugin.SendSms.SendSmsHelper;

/**
 * Support send sms in incallui feature.
 */
public class SendSmsPlugin extends SendSmsHelper{
    private static final String TAG = "SendSmsPlugin";

    public SendSmsPlugin() {
    }

    public void sendSms(Context context, DialerCall call, CallList callList) {
        String multicallnNumberList = "";
        //UNISOC : add for bug1195143
        StringBuilder stringBuilder= new StringBuilder();
        Uri uri = null;
        String[] numberArray = CallList.getInstance().getConferenceCallNumberArray();
        // UNISOC: modify for bug1250196
        if (!callList.hasValidGroupCall() || callList.getAllConferenceCall() == null || call.getChildCallIds().isEmpty()) {
            uri = Uri.parse("smsto:" + call.getNumber());
            log("Send sms.");
        } else {
            for (int i = 0; i < numberArray.length; i++) {
                stringBuilder.append(numberArray[i]).append(",");
            }
            multicallnNumberList = stringBuilder.toString();
            uri = Uri.parse("smsto:" + multicallnNumberList);
            log("Send sms when multi call.");
        }

        Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    public boolean isSupportSendSms() {
        return true;
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}

