package com.android.incallui.sprd.plugin.hdaudio;

import com.android.dialer.R;
import com.android.incallui.contactgrid.BottomRow;
import com.android.incallui.contactgrid.BottomRow.Info;
import com.android.incallui.sprd.InCallUiUtils;

import android.telecom.Call.Details;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;

public class InCallUIHdAudioHelper {

    private static final String TAG = "[InCallUIHdAudioHelper]";
    static InCallUIHdAudioHelper sInstance;

    public static InCallUIHdAudioHelper getInstance(Context context) {
        log("getInstance()");
        if (sInstance == null) {
            //UNISOC:add for bug1243924
            if (InCallUiUtils.isSupportHdAudio(context)) {
                sInstance = new InCallUIHdAudioPlugin();
            } else {
                sInstance = new InCallUIHdAudioHelper();
            }
            log("getInstance ["+sInstance+"]");
        }
        return sInstance;
    }

    /* UNISOC: add for bug1173199 @{*/
    protected InCallUIHdAudioHelper() {

    }

    public interface HDStatusListener {
        void onHDstatusUpdated(boolean hdVoiceState);
    }

    public void setListener(HDStatusListener hDStatusListener) {
        //do nothing
    }

    public void registerHdStatusChangedEvent(Context context) {
        //do nothing
    }
    public void unRegisterHdStatusChangedEvent(Context context) {
        //do nothing
    }
    /*@}*/
    
    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
