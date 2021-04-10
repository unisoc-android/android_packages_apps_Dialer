/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.incallui.sprd;

import android.os.Bundle;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;
import com.android.dialer.app.R;
//import com.android.dialer.common.LogUtil;
//import com.android.dialer.util.PermissionsUtil;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.InCallPresenter;

//import java.util.ArrayList;
//import java.util.List;
//
//import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;  //write external-storage
//import static android.Manifest.permission.READ_EXTERNAL_STORAGE;   //eard external-storage
//import static android.Manifest.permission.RECORD_AUDIO;
//import static android.Manifest.permission.READ_PHONE_STATE;

/**
 * Activity that request the permissions for auto record.
 */
public class RequestPermissionsActivity extends AppCompatActivity implements CallList.Listener {

  public static final String ARGS_CALL_ID = "call_id";
  public static final String ARGS_REQUEST_PERMISSIONS = "request_permissions";
  private static final int MICROPHONE_AND_STORAGE_PERMISSION_REQUEST_CODE = 101;

  private String callId;
  private String[] requestPermissions;
//  private String[] mPermissions = {WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE,
//            RECORD_AUDIO, READ_PHONE_STATE};

  @Override
  protected void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);

    callId = getIntent().getStringExtra(ARGS_CALL_ID);
    String requestPermissionsStr = getIntent().getStringExtra(ARGS_REQUEST_PERMISSIONS);
    if (callId == null) {
      finish();
      return;
    }
    requestPermissions = requestPermissionsStr.split(",");
    CallList.getInstance().addListener(this);
//    List<String> requestPermissionsList = new ArrayList<>();
//    for (int i = 0; i < mPermissions.length; i++) {
//      if (!PermissionsUtil.hasPermission(this, mPermissions[i])) {
//        requestPermissionsList.add(mPermissions[i]);
//      }
//    }
//    String[] requestPermissions = requestPermissionsList.toArray(
//            new String[requestPermissionsList.size()]);
    if (requestPermissions.length == 0) {
      InCallPresenter.getInstance().toggleRecorder();
      finish();
    } else {
      requestPermissions(requestPermissions, MICROPHONE_AND_STORAGE_PERMISSION_REQUEST_CODE);
    }
  }

  protected void onResume() {
    super.onResume();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == MICROPHONE_AND_STORAGE_PERMISSION_REQUEST_CODE) {
      // toggleRecorder since we were missing the permission before this.
      boolean isPermissionGranted = false;
      // Only if all requested permissions granted, can we toggleRecorder.
      if (grantResults.length > 0) {
        // grantResults's length greater than 0 means user has make a decision
        isPermissionGranted = true;
      }
      for (int i = 0; i < grantResults.length; i++) {
        isPermissionGranted = isPermissionGranted && grantResults[i] == PackageManager.PERMISSION_GRANTED;
      }
      if (isPermissionGranted) {
        InCallPresenter.getInstance().toggleRecorder();
      } else {
        // UNISOC: add for bug1173436
        if (grantResults.length > 0) {
          Toast.makeText(this, R.string.permission_no_record, Toast.LENGTH_LONG).show();
        }
      }
      finish();
    }
  }
  @Override
  protected void onDestroy() {
    super.onDestroy();
    CallList.getInstance().removeListener(this);
  }

  @Override
  public void onDisconnect(DialerCall call) {
    if (callId.equals(call.getId())) {
      finish();
    }
  }

  @Override
  public void onIncomingCall(DialerCall call) {}

  @Override
  public void onUpgradeToVideo(DialerCall call) {}

  @Override
  public void onUpgradeToRtt(DialerCall call, int rttRequestId) {}

  @Override
  public void onSessionModificationStateChange(DialerCall call) {}

  @Override
  public void onCallListChange(CallList callList) {}

  @Override
  public void onWiFiToLteHandover(DialerCall call) {}

  @Override
  public void onHandoverToWifiFailed(DialerCall call) {}

  @Override
  public void onInternationalCallOnWifi(@NonNull DialerCall call) {}
}
