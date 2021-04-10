/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.incallui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.Trace;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v4.app.Fragment;
import android.support.v4.os.UserManagerCompat;
import android.telecom.CallAudioState;
import android.telecom.PhoneAccountHandle;
import android.telephony.CarrierConfigManagerEx;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.android.contacts.common.compat.CallCompat;
import com.android.dialer.binary.common.DialerApplication;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.DialerImpression.Type;
import com.android.dialer.logging.Logger;
import com.android.dialer.telecom.TelecomUtil;
import com.android.ims.ImsManager;
import com.android.ims.internal.ImsManagerEx;
import com.android.ims.internal.IImsServiceEx;
import com.android.ims.internal.IImsRegisterListener;
import com.android.incallui.InCallCameraManager.Listener;
import com.android.incallui.InCallPresenter.CanAddCallListener;
import com.android.incallui.InCallPresenter.InCallDetailsListener;
import com.android.incallui.InCallPresenter.InCallState;
import com.android.incallui.InCallPresenter.InCallStateListener;
import com.android.incallui.InCallPresenter.IncomingCallListener;
import com.android.incallui.audiomode.AudioModeProvider;
import com.android.incallui.audiomode.AudioModeProvider.AudioModeListener;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.call.DialerCall.CameraDirection;
import com.android.incallui.call.TelecomAdapter;
import com.android.incallui.call.state.DialerCallState;
import com.android.incallui.incall.protocol.InCallButtonIds;
import com.android.incallui.incall.protocol.InCallButtonUi;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.multisim.SwapSimWorker;
import com.android.incallui.sprd.InCallUiUtils;
import com.android.incallui.sprd.plugin.ExplicitCallTransfer.ExplicitCallTransferHelper;
import com.android.incallui.sprd.plugin.SendSms.SendSmsHelper;
import com.android.incallui.videotech.utils.VideoUtils;
import com.android.incallui.videotech.utils.SessionModificationState;
import com.android.incallui.sprd.plugin.ConferenceNumLimit.ConferenceNumLimitHelper;
import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.READ_PHONE_STATE;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.widget.Toast;
import com.android.dialer.util.CallUtil;
import com.android.ims.internal.ImsManagerEx;
import android.telephony.CarrierConfigManager;
import com.android.incallui.sprd.PhoneRecorderHelper;
/** Logic for call buttons. */
public class CallButtonPresenter
    implements InCallStateListener,
        AudioModeListener,
        IncomingCallListener,
        InCallDetailsListener,
        CanAddCallListener,
        Listener,
        InCallButtonUiDelegate {

  private static final String KEY_AUTOMATICALLY_MUTED_BY_ADD_CALL =
      "incall_key_automatically_muted_by_add_call";
  private static final String KEY_PREVIOUS_MUTE_STATE = "incall_key_previous_mute_state";

  private final Context context;
  private InCallButtonUi inCallButtonUi;
  private DialerCall call;
  private boolean automaticallyMutedByAddCall = false;
  private boolean previousMuteState = false;
  private boolean isInCallButtonUiReady;
  private PhoneAccountHandle otherAccount;
  //UNISOC: add for Bug 905754
  boolean mIsSupportTxRxVideo = false;

  /* UNISOC Feature Porting: Add for call invite feature. @{ */
  private boolean mIsImsListenerRegistered;
  private IImsServiceEx mIImsServiceEx;
  private static final String MULTI_PICK_CONTACTS_ACTION = "com.android.contacts.action.MULTI_TAB_PICK";
  private static final String ADD_MULTI_CALL_AGAIN = "addMultiCallAgain";
  private static final int MAX_GROUP_CALL_NUMBER = 5;
  private static final int MIN_CONTACTS_NUMBER = 1;
  private boolean mStayVolte;
  /* @}*/

  public CallButtonPresenter(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void onInCallButtonUiReady(InCallButtonUi ui) {
    /* UNISOC : InCallUI Layout Refactor
     * @orig
    Assert.checkState(!isInCallButtonUiReady);
    */
    inCallButtonUi = ui;
    AudioModeProvider.getInstance().addListener(this);

    // register for call state changes last
    final InCallPresenter inCallPresenter = InCallPresenter.getInstance();
    inCallPresenter.addListener(this);
    inCallPresenter.addIncomingCallListener(this);
    inCallPresenter.addDetailsListener(this);
    inCallPresenter.addCanAddCallListener(this);
    inCallPresenter.getInCallCameraManager().addCameraSelectionListener(this);

    // Update the buttons state immediately for the current call
    onStateChange(InCallState.NO_CALLS, inCallPresenter.getInCallState(), CallList.getInstance());
    /* UNISOC Feature Porting: Add for call invite feature.{@*/
    tryRegisterImsListener();
    refreshMuteState(); // UNISOC: add for bug1156261
    /* @} */
    // UNISOC: add for bug1175393
    registerPresentReceiver();

    /* UNISOC Feature Porting: Automatic record 1180945 1185043. @{ */
    DialerApplication dialerApplication = (DialerApplication) context;  //mContext
    boolean mIsAutomaticRecordingStart = dialerApplication.getIsAutomaticRecordingStart();
    if (mIsAutomaticRecordingStart && inCallButtonUi != null
            && PhoneRecorderHelper.getInstance(context).getState().isActive()) {
      inCallPresenter.updateRecordTime();
      inCallButtonUi.setRecord(true);  //mInCallButtonUi
    } else if (inCallButtonUi != null && PhoneRecorderHelper.getInstance(context).getState().isIdle()){  //UNISOC:add for bug1188087
      inCallButtonUi.setRecord(false);
    }
    /* @} */

    isInCallButtonUiReady = true;
  }

  @Override
  public void onInCallButtonUiUnready() {
    /* UNISOC : InCallUI Layout Refactor
     * @orig
    Assert.checkState(isInCallButtonUiReady);
    */
    inCallButtonUi = null;
    InCallPresenter.getInstance().removeListener(this);
    AudioModeProvider.getInstance().removeListener(this);
    InCallPresenter.getInstance().removeIncomingCallListener(this);
    InCallPresenter.getInstance().removeDetailsListener(this);
    InCallPresenter.getInstance().getInCallCameraManager().removeCameraSelectionListener(this);
    InCallPresenter.getInstance().removeCanAddCallListener(this);
    /* UNISOC Feature Porting: Add for call invite feature.{@*/
    unTryRegisterImsListener();
    /* @} */
    // UNISOC: add for bug1175393
    unRegisterPresentReceiver();
    isInCallButtonUiReady = false;
  }

  @Override
  public void onStateChange(InCallState oldState, InCallState newState, CallList callList) {
    Trace.beginSection("CallButtonPresenter.onStateChange");

    if (newState == InCallState.OUTGOING) {
      call = callList.getOutgoingCall();
    } else if (newState == InCallState.INCALL) {
      call = callList.getActiveOrBackgroundCall();

      // When connected to voice mail, automatically shows the dialpad.
      // (On previous releases we showed it when in-call shows up, before waiting for
      // OUTGOING.  We may want to do that once we start showing "Voice mail" label on
      // the dialpad too.)
      if (oldState == InCallState.OUTGOING && call != null) {
        if (call.isVoiceMailNumber() && getActivity() != null) {
          getActivity().showDialpadFragment(true /* show */, true /* animate */);
        }
      }
    } else if (newState == InCallState.INCOMING) {
      if (getActivity() != null) {
        getActivity().showDialpadFragment(false /* show */, true /* animate */);
      }
      call = callList.getIncomingCall();
    } else {
      call = null;
    }
    updateUi(newState, call);

    Trace.endSection();
  }

  /**
   * Updates the user interface in response to a change in the details of a call. Currently handles
   * changes to the call buttons in response to a change in the details for a call. This is
   * important to ensure changes to the active call are reflected in the available buttons.
   *
   * @param call The active call.
   * @param details The call details.
   */
  @Override
  public void onDetailsChanged(DialerCall call, android.telecom.Call.Details details) {
    // Only update if the changes are for the currently active call
    if (inCallButtonUi != null && call != null && call.equals(this.call)) {
      updateButtonsState(call);
    }
  }

  @Override
  public void onIncomingCall(InCallState oldState, InCallState newState, DialerCall call) {
    onStateChange(oldState, newState, CallList.getInstance());
  }

  @Override
  public void onCanAddCallChanged(boolean canAddCall) {
    if (inCallButtonUi != null && call != null) {
      updateButtonsState(call);
    }
  }

  @Override
  public void onAudioStateChanged(CallAudioState audioState) {
    if (inCallButtonUi != null) {
      inCallButtonUi.setAudioState(audioState);
    }
  }

  @Override
  public CallAudioState getCurrentAudioState() {
    return AudioModeProvider.getInstance().getAudioState();
  }

  @Override
  public void setAudioRoute(int route) {
    LogUtil.i(
        "CallButtonPresenter.setAudioRoute",
        "sending new audio route: " + CallAudioState.audioRouteToString(route));
    TelecomAdapter.getInstance().setAudioRoute(route);
  }

  /** Function assumes that bluetooth is not supported. */
  @Override
  public void toggleSpeakerphone() {
    // This function should not be called if bluetooth is available.
    CallAudioState audioState = getCurrentAudioState();
    if (0 != (CallAudioState.ROUTE_BLUETOOTH & audioState.getSupportedRouteMask())) {
      // It's clear the UI is wrong, so update the supported mode once again.
      LogUtil.e(
          "CallButtonPresenter", "toggling speakerphone not allowed when bluetooth supported.");
      inCallButtonUi.setAudioState(audioState);
      return;
    }

    int newRoute;
    if (audioState.getRoute() == CallAudioState.ROUTE_SPEAKER) {
      newRoute = CallAudioState.ROUTE_WIRED_OR_EARPIECE;
      Logger.get(context)
          .logCallImpression(
              DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_WIRED_OR_EARPIECE,
              call.getUniqueCallId(),
              call.getTimeAddedMs());
    } else {
      newRoute = CallAudioState.ROUTE_SPEAKER;
      Logger.get(context)
          .logCallImpression(
              DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_SPEAKERPHONE,
              call.getUniqueCallId(),
              call.getTimeAddedMs());
    }

    setAudioRoute(newRoute);
  }

  @Override
  public void muteClicked(boolean checked, boolean clickedByUser) {
    LogUtil.i(
        "CallButtonPresenter", "turning on mute: %s, clicked by user: %s", checked, clickedByUser);
    if (clickedByUser) {
      InCallPresenter.getInstance().setPreviousMuteState(checked);//add for bug1151816
      Logger.get(context)
          .logCallImpression(
              checked
                  ? DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_MUTE
                  : DialerImpression.Type.IN_CALL_SCREEN_TURN_OFF_MUTE,
              call.getUniqueCallId(),
              call.getTimeAddedMs());
    }
    TelecomAdapter.getInstance().mute(checked);
  }

  @Override
  public void holdClicked(boolean checked) {
    if (call == null) {
      return;
    }
    if (checked) {
      LogUtil.i("CallButtonPresenter", "putting the call on hold: " + call);
      call.hold();
    } else {
      LogUtil.i("CallButtonPresenter", "removing the call from hold: " + call);
      call.unhold();
    }
  }

  @Override
  public void swapClicked() {
    if (call == null) {
      return;
    }

    LogUtil.i("CallButtonPresenter", "swapping the call: " + call);
    TelecomAdapter.getInstance().swap(call.getId());
  }

  @Override
  public void mergeClicked() {
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.IN_CALL_MERGE_BUTTON_PRESSED,
            call.getUniqueCallId(),
            call.getTimeAddedMs());
    TelecomAdapter.getInstance().merge(call.getId());
  }

  @Override
  public void addCallClicked() {
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.IN_CALL_ADD_CALL_BUTTON_PRESSED,
            call.getUniqueCallId(),
            call.getTimeAddedMs());
    if (automaticallyMutedByAddCall) {
      // Since clicking add call button brings user to MainActivity and coming back refreshes mute
      // state, add call button should only be clicked once during InCallActivity shows. Otherwise,
      // we set previousMuteState wrong.
      return;
    }

    if(!isInLockTaskMode()) {//UNISOC:add for bug1156168
      // Automatically mute the current call
      automaticallyMutedByAddCall = true;
      previousMuteState = AudioModeProvider.getInstance().getAudioState().isMuted();
      // UNISOC: add for bug1138291
      InCallPresenter.getInstance().setAutomaticallyMutedByAddCall(automaticallyMutedByAddCall);
      // UNISOC: add for bug1110240
      InCallPresenter.getInstance().setPreviousMuteState(previousMuteState);
      // Simulate a click on the mute button
      muteClicked(true /* checked */, false /* clickedByUser */);
    }
    TelecomAdapter.getInstance().addCall();
  }

  @Override
  public void showDialpadClicked(boolean checked) {
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.IN_CALL_SHOW_DIALPAD_BUTTON_PRESSED,
            call.getUniqueCallId(),
            call.getTimeAddedMs());
    LogUtil.v("CallButtonPresenter", "show dialpad " + String.valueOf(checked));
    if(getActivity() != null){ //add for bug1141881
      getActivity().showDialpadFragment(checked /* show */, true /* animate */);
    }else{
      LogUtil.i("CallButtonPresenter", "showDialpadClicked activity is null");
    }
  }

  @Override
  public void changeToVideoClicked() {
    LogUtil.enterBlock("CallButtonPresenter.changeToVideoClicked");
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.VIDEO_CALL_UPGRADE_REQUESTED,
            call.getUniqueCallId(),
            call.getTimeAddedMs());
    if (isSupportTxRxVideo(call)) {
      displayModifyCallOptions(call, getActivity()); //mCall
      return;
    }

    // call.getVideoTech().upgradeToVideo(context);
    /* UNISOC FL0108020020: Add feature of low battery for Reliance@{ */
    if (CallUtil.isBatteryLow(context)) {
      CallUtil.showLowBatteryChangeToVideoDialog(context, call.getTelecomCall());
    }else{
      call.getVideoTech().upgradeToVideo(context);
    }/*@}*/
  }

  /* UNISOC: Add video call option menu@{ */
  @Override
  public void changeToVoiceClicked() {
    LogUtil.i("CallButtonPresenter.changeToVoiceClicked","");
    call.getVideoTech().degradeToVoice();  //mCall
  }
  /*@}*/

  @Override
  public void changeToRttClicked() {
    LogUtil.enterBlock("CallButtonPresenter.changeToRttClicked");
    call.sendRttUpgradeRequest();
  }

  @Override
  public void onEndCallClicked() {
    LogUtil.i("CallButtonPresenter.onEndCallClicked", "call: " + call);
    if (call != null) {
      call.disconnect();
    }
  }

  @Override
  public void showAudioRouteSelector() {
    inCallButtonUi.showAudioRouteSelector();
  }

  @Override
  public void swapSimClicked() {
    LogUtil.enterBlock("CallButtonPresenter.swapSimClicked");
    Logger.get(getContext()).logImpression(Type.DUAL_SIM_CHANGE_SIM_PRESSED);
    call.setSwapSimActionFlag(true); /*UNISOC: add for bug1221260 */
    SwapSimWorker worker =
        new SwapSimWorker(
            getContext(),
            call,
            InCallPresenter.getInstance().getCallList(),
            otherAccount,
            InCallPresenter.getInstance().acquireInCallUiLock("swapSim"));
    /*UNISOC: add for bug1221260 */
    InCallPresenter.getInstance().getCallList().setWorker(worker);
    DialerExecutorComponent.get(getContext())
        .dialerExecutorFactory()
        .createNonUiTaskBuilder(worker)
        .build()
        .executeParallel(null);
  }

  /**
   * Switches the camera between the front-facing and back-facing camera.
   *
   * @param useFrontFacingCamera True if we should switch to using the front-facing camera, or false
   *     if we should switch to using the back-facing camera.
   */
  @Override
  public void switchCameraClicked(boolean useFrontFacingCamera) {
    updateCamera(useFrontFacingCamera);
  }

  @Override
  public void toggleCameraClicked() {
    LogUtil.i("CallButtonPresenter.toggleCameraClicked", "");
    if (call == null) {
      return;
    }
    Logger.get(context)
        .logCallImpression(
            DialerImpression.Type.IN_CALL_SCREEN_SWAP_CAMERA,
            call.getUniqueCallId(),
            call.getTimeAddedMs());
    switchCameraClicked(
        !InCallPresenter.getInstance().getInCallCameraManager().isUsingFrontFacingCamera());
  }

  /**
   * Stop or start client's video transmission.
   *
   * @param pause True if pausing the local user's video, or false if starting the local user's
   *     video.
   */
  @Override
  public void pauseVideoClicked(boolean pause) {
    LogUtil.i("CallButtonPresenter.pauseVideoClicked", "%s", pause ? "pause" : "unpause");

    Logger.get(context)
        .logCallImpression(
            pause
                ? DialerImpression.Type.IN_CALL_SCREEN_TURN_OFF_VIDEO
                : DialerImpression.Type.IN_CALL_SCREEN_TURN_ON_VIDEO,
            call.getUniqueCallId(),
            call.getTimeAddedMs());

    if (pause) {
      call.getVideoTech().setCamera(null);
      call.getVideoTech().stopTransmission();
    } else {
      updateCamera(
          InCallPresenter.getInstance().getInCallCameraManager().isUsingFrontFacingCamera());
      call.getVideoTech().resumeTransmission(context);
    }

    inCallButtonUi.setVideoPaused(pause);
    inCallButtonUi.enableButton(InCallButtonIds.BUTTON_PAUSE_VIDEO, false);
  }

  private void updateCamera(boolean useFrontFacingCamera) {
    InCallCameraManager cameraManager = InCallPresenter.getInstance().getInCallCameraManager();
    cameraManager.setUseFrontFacingCamera(useFrontFacingCamera);

    String cameraId = cameraManager.getActiveCameraId();
    if (cameraId != null) {
      final int cameraDir =
          cameraManager.isUsingFrontFacingCamera()
              ? CameraDirection.CAMERA_DIRECTION_FRONT_FACING
              : CameraDirection.CAMERA_DIRECTION_BACK_FACING;
      call.setCameraDir(cameraDir);
      call.getVideoTech().setCamera(cameraId);
    }
  }

  private void updateUi(InCallState state, DialerCall call) {
    LogUtil.v("CallButtonPresenter", "updating call UI for call: %s", call);

    if (inCallButtonUi == null) {
      return;
    }

    if (call != null) {
      inCallButtonUi.updateInCallButtonUiColors(
          InCallPresenter.getInstance().getThemeColorManager().getSecondaryColor());
    }

    final boolean isEnabled =
        state.isConnectingOrConnected() && !state.isIncoming() && call != null;
    inCallButtonUi.setEnabled(isEnabled);
    LogUtil.i("CallButtonPresenter", "updateUi isEnabled:"+isEnabled);

    if (call == null) {
      return;
    }

    updateButtonsState(call);
  }

  /**
   * Updates the buttons applicable for the UI.
   *
   * @param call The active call.
   */
  @SuppressWarnings(value = {"MissingPermission"})
  private void updateButtonsState(DialerCall call) {
    LogUtil.v("CallButtonPresenter.updateButtonsState", "");
    final boolean isVideo = call.isVideoCall();

    // Common functionality (audio, hold, etc).
    // Show either HOLD or SWAP, but not both. If neither HOLD or SWAP is available:
    //     (1) If the device normally can hold, show HOLD in a disabled state.
    //     (2) If the device doesn't have the concept of hold/swap, remove the button.
    final boolean showSwap = call.can(android.telecom.Call.Details.CAPABILITY_SWAP_CONFERENCE);
    final boolean showHold =
        !showSwap
            //UNISOC modified for bug 1145387 start
            && call.getState() != DialerCallState.DIALING
            //end
            && call.can(android.telecom.Call.Details.CAPABILITY_SUPPORT_HOLD)
            && call.can(android.telecom.Call.Details.CAPABILITY_HOLD)
            && InCallUiUtils.showHoldOnButton(context,isVideo, call) // UNISOC: modify for bug1176470
                //UNISOC:add for bug1137562
            && (call.getVideoTech().getSessionModificationState() != SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE);
    final boolean isCallOnHold = call.getState() == DialerCallState.ONHOLD;

    final boolean showAddCall =
        TelecomAdapter.getInstance().canAddCall() && UserManagerCompat.isUserUnlocked(context)
        //UNISOC:add for bug1137558
        && (call.getVideoTech().getSessionModificationState() != SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE)
        && (call.isCdmaCallAnswered() || !call.phoneisCdma())
        && !InCallUiUtils.isSupportSingleVideoCall(context, call);//UNISOC: add for bug1223743

     /* UNISOC Feature Porting: Toast information when the number of conference call is over limit
       for cmcc case @{
     * @orig
     // There can only be two calls so don't show the ability to merge when one of them
    // is a speak easy call.
    final boolean showMerge =
        InCallPresenter.getInstance()
                .getCallList()
                .getAllCalls()
                .stream()
                .noneMatch(c -> c != null && c.isSpeakEasyCall())
            && call.can(android.telecom.Call.Details.CAPABILITY_MERGE_CONFERENCE);*/
    final boolean showMerge = ConferenceNumLimitHelper.getInstance(context)
            .showMergeButton(call)
            // UNISOC: add for bug1151290
            && (call.getVideoTech().getSessionModificationState() != SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE)
            //UNISOC: add for bug1182427
            && !(CallList.getInstance().isConferenceParticipant());
    /* @} */

    final boolean showUpgradeToVideo = !isVideo && (hasVideoCallCapabilities(call)) && !isCallOnHold//UNISOC:modify for bug1118012
             && isSupportUpAndDownGrade(call) // UNISOC: add for Bug 1146551
             && !call.isRemotelyHeld();// UNISOC: add for bug1146557

    //UNISOC:add for bug958670
    final boolean isSupportTxRxVideo = isSupportTxRxVideo(call);
    //UNISOC:add for bug1168957
    final boolean isConferenceCall = call.isConferenceCall();
    final int callState = call.getState();

    final boolean showDowngradeToAudio = isVideo && isDowngradeToAudioSupported(call)
            /* UNISOC: Added for video call conference @{ */
            && callState == DialerCallState.ACTIVE
            && !call.usedToBeConference() // UNISOC: add for bug1214394 1246728
            && !call.isRemotelyHeld() // UNISOC: add for bug1149848
            && !isSupportTxRxVideo;// Add for change video type feature
            /* @} */
   /* UNISOC: Add for change video type feature @{ */
    final boolean showChangeVideoType =
            isSupportTxRxVideo
                    && isVideo && isDowngradeToAudioSupported(call)
                    && hasVideoCallCapabilities(call)
                    && !call.usedToBeConference() // UNISOC: add for bug1214394 1246728
                    && callState == DialerCallState.ACTIVE;
    /* @} */
    final boolean showMute = call.can(android.telecom.Call.Details.CAPABILITY_MUTE);

    // UNISOC: modify for bug1179501
    final boolean hasCameraPermission = VideoUtils.hasCameraPermissionAndShownPrivacyToast(context);
    // Disabling local video doesn't seem to work when dialing. See a bug.
    final boolean showPauseVideo =
          !isSupportTxRxVideo// Add for change video type feature
            && isVideo
            && callState != DialerCallState.DIALING
            && callState != DialerCallState.CONNECTING;
    // UNISOC Feature Porting: Enable send sms in incallui feature.
    final boolean isCallActive = callState == DialerCallState.ACTIVE;

    otherAccount = TelecomUtil.getOtherAccount(getContext(), call.getAccountHandle());
    boolean showSwapSim =
        !call.isEmergencyCall()
            && otherAccount != null
            && !call.isVoiceMailNumber()
            && DialerCallState.isDialing(callState)
            // Most devices cannot make calls on 2 SIMs at the same time.
            && InCallPresenter.getInstance().getCallList().getAllCalls().size() == 1;

    /* UNISOC Feature Porting: Add for call invite feature.{@*/
    int conferenceSize = 0;
    if (isConferenceCall && call.getChildCallIds() != null) {
        conferenceSize = call.getChildCallIds().size();
    }
    //UNISOC: add for bug1121421 1152167
    final boolean isHiddenNumber = call.isHiddenNumber()
            && (conferenceSize == 0 || !isConferenceCall);
    // UNISOC: add for bug920540
    boolean isVolteCall = false;
    if (ImsManagerEx.isImsRegisteredForPhone(InCallUiUtils.getPhoneIdByAccountHandle(context, call.getAccountHandle()))) {
      isVolteCall = true;
    }
    final boolean canInvite = isConferenceCall
            // UNISOC: add for bug 895929
            && call.getChildCallIds().size() >= 1
            && (conferenceSize < 5)
            && mStayVolte && (callState == DialerCallState.ACTIVE)
            // UNISOC: add for bug920540
            && !isVideo && isVolteCall
            // add for bug966900
            && !call.hasProperty(android.telecom.Call.Details.PROPERTY_WIFI)
            // modify for bug 1138221
            &&InCallUiUtils.ShowInviteButton(context,call);
    /* @} */
    LogUtil.i("CallButtonPresenter.updateButtonsState", " iscon=" + isConferenceCall
            + " conferenceSize=" + conferenceSize + " mStayVolte=" + mStayVolte + " callstate="
            + callState + " isCallOnHold=" + isCallOnHold + ", isVolteCall=" + isVolteCall);

    boolean showUpgradeToRtt = call.canUpgradeToRttCall();
    boolean enableUpgradeToRtt = showUpgradeToRtt && callState == DialerCallState.ACTIVE;

    inCallButtonUi.showButton(InCallButtonIds.BUTTON_AUDIO, true);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_SWAP, showSwap);
    /* UNISOC:add for bug1136438 @{ */
    DialerCall activeCall = CallList.getInstance().getActiveCall();
    DialerCall backgroundCall = CallList.getInstance().getBackgroundCall();
    boolean showSwitchToSec = false;
    if (activeCall != null && backgroundCall != null) {
      showSwitchToSec = call.getVideoTech().getSessionModificationState() != SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE
                        && (call.getVideoTech().getSessionModificationState() != SessionModificationState.UPGRADE_TO_VIDEO_REQUEST_FAILED);  //UNISOC add for bug1203190
      inCallButtonUi.showButton(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY, showSwitchToSec);
      inCallButtonUi.enableButton(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY, showSwitchToSec);
    } else {
      inCallButtonUi.showButton(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY, false);
      inCallButtonUi.enableButton(InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY, false);
    }/*end*/
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_HOLD, showHold && !showSwitchToSec);
    // UNISOC: add for bug1100824
    if (showHold && !showSwitchToSec) {
      //add for bug1092562
      inCallButtonUi.enableButton(InCallButtonIds.BUTTON_HOLD,isEnableHold(call));
    }
    inCallButtonUi.setHold(isCallOnHold);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_MUTE, showMute);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_SWAP_SIM, showSwapSim);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_ADD_CALL, true);
    inCallButtonUi.enableButton(InCallButtonIds.BUTTON_ADD_CALL, showAddCall);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_UPGRADE_TO_VIDEO, showUpgradeToVideo);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_UPGRADE_TO_RTT, showUpgradeToRtt);
    inCallButtonUi.enableButton(InCallButtonIds.BUTTON_UPGRADE_TO_RTT, enableUpgradeToRtt);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_DOWNGRADE_TO_AUDIO, showDowngradeToAudio);
    // UNISOC:add for bug1137837 1137838
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_CHANGE_VIDEO_TYPE, showChangeVideoType);
    // UNISOC: modify for bug1179501
    inCallButtonUi.showButton(
        InCallButtonIds.BUTTON_SWITCH_CAMERA,
            hasCameraPermission && ((isVideo && call.getVideoTech().isTransmitting())
                    || call.getVideoTech().getSessionModificationState()
                    == SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE));
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_PAUSE_VIDEO, showPauseVideo);
    if (isVideo) {
      inCallButtonUi.setVideoPaused(!call.getVideoTech().isTransmitting() || !hasCameraPermission);
    }
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_DIALPAD, true);
    // UNISOC: add for bug 1139429
    inCallButtonUi.enableButton(InCallButtonIds.BUTTON_DIALPAD,!isCallOnHold);
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_MERGE, showMerge);

    /* UNISOC Feature Porting: Add for call recorder feature.
    && UNISOC Feature Porting: Hide recorder feature for telstra case. @{ */
    if (InCallUiUtils.isRecorderEnabled(context)) {  //mContext
      //UNISOC:add for bug1214757
      PhoneRecorderHelper recorderHelper = PhoneRecorderHelper.getInstance(context);
      if(recorderHelper != null) {
        inCallButtonUi.setRecord(recorderHelper.getState().isActive());
      }
      if (isVideo) {
        if (callState == DialerCallState.ACTIVE) {
          inCallButtonUi.showButton(InCallButtonIds.BUTTON_RECORD, true);
        } else {
          inCallButtonUi.showButton(InCallButtonIds.BUTTON_RECORD, false);
        }
      } else {
        inCallButtonUi.showButton(InCallButtonIds.BUTTON_RECORD, true);
      }
      inCallButtonUi.enableButton(InCallButtonIds.BUTTON_RECORD, isEnableRecorder(call)
              // UNISOC: add for bug1195086
              && UserManagerCompat.isUserUnlocked(context));
    } else {
      inCallButtonUi.showButton(InCallButtonIds.BUTTON_RECORD, false);
    }
    /* @} */

    /* UNISOC Feature Porting: Enable send sms in incallui feature. @{ */
    if ((isCallActive || isCallOnHold) && SendSmsHelper.getInstance(context).isSupportSendSms() && !isHiddenNumber) {  //UNISOC:add for bug1121421 1206661
      inCallButtonUi.showButton(InCallButtonIds.BUTTON_SEND_MESSAGE, true);  //mInCallButtonUi
      // UNISOC: Add for bug1206661
      if (call.isConferenceCall() && isCallOnHold) {
        inCallButtonUi.enableButton(InCallButtonIds.BUTTON_SEND_MESSAGE, false);
      } else {
        inCallButtonUi.enableButton(InCallButtonIds.BUTTON_SEND_MESSAGE, true);
      }
    } else {
      inCallButtonUi.showButton(InCallButtonIds.BUTTON_SEND_MESSAGE, false);  //mInCallButtonUi
    }
    /* @} */
    // UNISOC Feature Porting: FL0108160005 Hangup all calls for orange case.
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_HANGUP_ALL, isShowHangupAll());

    // UNISOC Feature Porting: Explicit Call Transfer.
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_ECT, enableTransferButton() && !isVideo);

    /* UNISOC Feature Porting: Add for call invite feature. {@*/
    inCallButtonUi.showButton(InCallButtonIds.BUTTON_INVITE, canInvite);
    /* @} */
    inCallButtonUi.updateButtonStates();
  }

  private boolean hasVideoCallCapabilities(DialerCall call) {
    return call.getVideoTech().isAvailable(context, call.getAccountHandle());
  }

  /**
   * Determines if downgrading from a video call to an audio-only call is supported. In order to
   * support downgrade to audio, the SDK version must be >= N and the call should NOT have the
   * {@link android.telecom.Call.Details#CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO}.
   *
   * @param call The call.
   * @return {@code true} if downgrading to an audio-only call from a video call is supported.
   */
  private boolean isDowngradeToAudioSupported(DialerCall call) {
    // TODO(a bug): If there is an RCS video share session, return true here
    return !call.can(CallCompat.Details.CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO);
  }

  @Override
  public void refreshMuteState() {
    //UNISOC: add for bug1110240
    previousMuteState = InCallPresenter.getInstance().isPreviousMuteState();
    // UNISOC: add for bug1138291
    automaticallyMutedByAddCall = InCallPresenter.getInstance().isAutomaticallyMutedByAddCall();
    // Restore the previous mute state
    if (automaticallyMutedByAddCall
        && AudioModeProvider.getInstance().getAudioState().isMuted() != previousMuteState) {
      if (inCallButtonUi == null) {
        return;
      }
      muteClicked(previousMuteState, false /* clickedByUser */);
    }
    automaticallyMutedByAddCall = false;
    // UNISOC: add for bug1138291
    InCallPresenter.getInstance().setAutomaticallyMutedByAddCall(automaticallyMutedByAddCall);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    outState.putBoolean(KEY_AUTOMATICALLY_MUTED_BY_ADD_CALL, automaticallyMutedByAddCall);
    outState.putBoolean(KEY_PREVIOUS_MUTE_STATE, previousMuteState);
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    automaticallyMutedByAddCall =
        savedInstanceState.getBoolean(
            KEY_AUTOMATICALLY_MUTED_BY_ADD_CALL, automaticallyMutedByAddCall);
    previousMuteState = savedInstanceState.getBoolean(KEY_PREVIOUS_MUTE_STATE, previousMuteState);
  }

  @Override
  public void onCameraPermissionGranted() {
    if (call != null) {
      updateButtonsState(call);
    }
  }

  @Override
  public void onActiveCameraSelectionChanged(boolean isUsingFrontFacingCamera) {
    if (inCallButtonUi == null) {
      return;
    }
    inCallButtonUi.setCameraSwitched(!isUsingFrontFacingCamera);
  }

  @Override
  public Context getContext() {
    return context;
  }

  private InCallActivity getActivity() {
    if (inCallButtonUi != null) {
      Fragment fragment = inCallButtonUi.getInCallButtonUiFragment();
      if (fragment != null) {
        return (InCallActivity) fragment.getActivity();
      }
    }
    return null;
  }

  /* UNISOC Feature Porting: Add for call recorder feature. @{ */
  public boolean isEnableRecorder(DialerCall call) {
    int state = call.getState();
    // UNISOC Added for Bug1145755,1178729
    if(call.phoneisCdma()&&!call.isIncoming()) {
      return (call.isCdmaCallAnswered() || (call.getChildCallIds()!=null && call.getChildCallIds().size()>1) || state == DialerCallState.ONHOLD
              || state == DialerCallState.CONFERENCED);
    }
    return (state == DialerCallState.ACTIVE || state == DialerCallState.ONHOLD
            || state == DialerCallState.CONFERENCED);
  }

  /* UNISOC add for bug1145755 @{ */
  public boolean isEnableHold(DialerCall call) {
    if(call.phoneisCdma() && !call.isIncoming()) {
      return call.isCdmaCallAnswered();
    }
    //add for Bug1100045
    if(call.getVideoTech().getSessionModificationState() == SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE){
      return false;
    }
    return true;
  }
  /* @} */

  @Override
  public void recordClick(boolean isChecked) {
    LogUtil.i("CallButtonPresenter.recordClick"," isChecked = " + isChecked);
    if (getActivity() != null) {
      getActivity().recordClick();
    }
  //add for bug1146372
    if (inCallButtonUi != null) {
      inCallButtonUi.setRecord(isChecked);  //mInCallButtonUi
    }
  }
  /* @} */

  /* UNISOC Feature Porting: Enable send sms in incallui feature. @{ */
  public void sendSMSClicked() {
    LogUtil.d("CallButtonPresenter.sendSMSClicked", "");
    if (call != null) {  //mCall
      CallList callList = InCallPresenter.getInstance().getCallList();
      SendSmsHelper.getInstance(context).sendSms(  //mContext
              context, call, callList);
    } else {
      LogUtil.d("CallButtonPresenter.sendSMSClicked", "The call is null, can't send message.");
    }
  }
  /* @} */

  /* UNISOC Feature Porting: FL0108160005 Hangup all calls for orange case. */
  @Override
  public void hangupAllClicked() {
    LogUtil.i("CallButtonPresenter.hangupAllClicked", "");
    DialerCall fgCall = CallList.getInstance().getActiveCall();
    DialerCall bgCall = CallList.getInstance().getBackgroundCall();

    if (fgCall != null) {
      fgCall.disconnect();
    }
    if (bgCall != null) {
      bgCall.disconnect();
    }
  }

  private boolean isShowHangupAll() {
    boolean enable = InCallUiUtils.isSupportHangupAll(getContext());
    CallList calllist = CallList.getInstance();

    return enable && calllist != null && calllist.getActiveCall() != null
            && calllist.getBackgroundCall() != null;
  }
  /* @} */

  /**
   * UNISOC Feature Porting: Explicit Call Transfer
   */
  public boolean enableTransferButton() {
    return ExplicitCallTransferHelper.getInstance(context)
            .shouldEnableTransferButton();
  }

  /**
   * UNISOC Feature Porting: Explicit Call Transfer
   */
  public void transferCall() {
    if (call != null && context != null) {
      ExplicitCallTransferHelper.getInstance(context)
              .explicitCallTransfer(context);
    }
  }

  /* UNISOC Feature Porting: Add for call invite feature.{@*/
  @Override
  public void inviteClicked() {
    LogUtil.i("CallButtonPresenter.inviteClick","");
    String [] numberArray = CallList.getInstance().getActiveConferenceCallNumberArray();
    Intent intentPick = new Intent(MULTI_PICK_CONTACTS_ACTION).
            putExtra("checked_limit_count",MAX_GROUP_CALL_NUMBER - CallList.getInstance().getConferenceCallSize()).
            putExtra("checked_min_limit_count", MIN_CONTACTS_NUMBER).
            putExtra("cascading",new Intent(MULTI_PICK_CONTACTS_ACTION).setType(Phone.CONTENT_ITEM_TYPE)).
            putExtra("multi",ADD_MULTI_CALL_AGAIN).
            putExtra("number",numberArray);
    try { //add for bug968873
      intentPick.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(intentPick);
    } catch (android.content.ActivityNotFoundException e){
      LogUtil.e("CallButtonPresenter.inviteClicked", "Exception:" + e.getMessage());
    }
  }
  /* UNISOC: add for bug 1175393 @{ */
  private boolean hasRegisterPresentReceiver = false;

  private final BroadcastReceiver presentReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
        if (call == null || inCallButtonUi == null || UserManagerCompat.isUserUnlocked(context)) {
          return;
        }
        final boolean showAddCall =
                TelecomAdapter.getInstance().canAddCall()
                        && (call.getVideoTech().getSessionModificationState() != SessionModificationState.WAITING_FOR_UPGRADE_TO_VIDEO_RESPONSE)
                        && (call.isCdmaCallAnswered() || !call.phoneisCdma());
        inCallButtonUi.enableButton(InCallButtonIds.BUTTON_ADD_CALL, showAddCall);
        // UNISOC: add for bug1195086
        if (InCallUiUtils.isRecorderEnabled(context)) {
          inCallButtonUi.showButton(InCallButtonIds.BUTTON_RECORD,
                  !call.isVideoCall() || call.getState() == DialerCallState.ACTIVE);
          inCallButtonUi.enableButton(InCallButtonIds.BUTTON_RECORD, isEnableRecorder(call));
        } else {
          inCallButtonUi.showButton(InCallButtonIds.BUTTON_RECORD, false);
        }
        inCallButtonUi.updateButtonStates();
      }
    }
  };

  private synchronized void registerPresentReceiver() {
    if (!UserManagerCompat.isUserUnlocked(context)) {
      hasRegisterPresentReceiver = true;
      IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
      context.registerReceiver(presentReceiver, filter);
    }
  }
  private synchronized void unRegisterPresentReceiver() {
    if (hasRegisterPresentReceiver) {
      context.unregisterReceiver(presentReceiver);
      // UNISOC: add for bug1188922
      hasRegisterPresentReceiver = false;
    }
  }
  /* @} */
  private synchronized void tryRegisterImsListener(){
      if(context != null && context.checkSelfPermission(READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
              && ImsManager.isVolteEnabledByPlatform(context)){
          mIImsServiceEx = ImsManagerEx.getIImsServiceEx();
          if(mIImsServiceEx != null){
              try{
                  if(!mIsImsListenerRegistered){
                      mIsImsListenerRegistered = true;
                      mIImsServiceEx.registerforImsRegisterStateChanged(mImsUtListenerExBinder);
                  }
              }catch(RemoteException e){
                  LogUtil.e("CallButtonPresenter tryRegisterImsListener", " e = " + e);
              }
          }
      }
  }

  private final IImsRegisterListener.Stub mImsUtListenerExBinder = new IImsRegisterListener.Stub(){
      @Override
      public void imsRegisterStateChange(boolean isRegistered){
        LogUtil.i("CallButtonPresenter imsRegisterStateChange", " isRegistered: " + isRegistered);
          if(mStayVolte != isRegistered){
              mStayVolte = isRegistered;
          }
      }
  };

  private synchronized void unTryRegisterImsListener(){
      if(context != null && context.checkSelfPermission(READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
              && ImsManager.isVolteEnabledByPlatform(context)){
          try{
              if(mIsImsListenerRegistered){
                  mIsImsListenerRegistered = false;
                  mIImsServiceEx.unregisterforImsRegisterStateChanged(mImsUtListenerExBinder);
              }
          }catch(RemoteException e){
              LogUtil.e("CallButtonPresenter unTryRegisterImsListener", " e = " + e);
          }
      }
  }
  /* @} */
/* UNISOC Feature Porting: Add for change video type feature. */
  @Override
  public void changeVideoTypeClicked() {
    displayModifyCallOptions(call, getActivity());
  }

  //UNISOC:add for bug958670
  private int mPreSubId = -1;

  public boolean isSupportTxRxVideo(DialerCall call) {
     /* UNISOC: add for Bug 905754 & 958670 & 1137831 @{ */
    CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
            Context.CARRIER_CONFIG_SERVICE);
    PhoneAccountHandle accountHandle = call.getAccountHandle();
    int currentSubId = InCallUiUtils.getSubIdForPhoneAccountHandle(context,accountHandle);
    if(-1 == currentSubId || mPreSubId == currentSubId) {
      LogUtil.i("CallButtonPresenter",
              "has already got the value or invalid sub id. currentSubId =" + currentSubId);
      return mIsSupportTxRxVideo && mStayVolte;
    }

    if (configManager.getConfigForSubId(currentSubId) != null) {
      mIsSupportTxRxVideo = configManager.getConfigForSubId(currentSubId).getBoolean(
              CarrierConfigManagerEx.KEY_CARRIER_SUPPORT_VIDEO_CALL_TX_RX_CONTROL);
      LogUtil.i("CallButtonPresenter", "isSupportTxRxVideo:"
              + mIsSupportTxRxVideo);
      mPreSubId = currentSubId;
    } else {
      LogUtil.i("CallButtonPresenter", "isSupportTxRxVideo getConfigForDefaultPhone = null");
    }
    /* @}*/
    // UNISOC: add for Bug 846738
    return mIsSupportTxRxVideo && mStayVolte;
  }

  /**
   * The function is called when Modify Call button gets pressed. The function creates and
   * displays modify call options.
   */
  public void displayModifyCallOptions(final DialerCall call, final Context context) {
    if (call == null) {
      Log.d(this, "Can't display modify call options. Call is null");
      return;
    }
    /*if (context.getResources().getBoolean(
            R.bool.config_enable_enhance_video_call_ui)) {
        // selCallType is set to -1 default, if the value is not updated, it is unexpected.
        if (selectType != -1) {
            VideoProfile videoProfile = new VideoProfile(selectType);
            Log.v(this, "Videocall: Enhance videocall: upgrade/downgrade to "
                    + callTypeToString(selectType));
            changeToVideoClicked(call, videoProfile);
        }
        return;
    }*/
    final ArrayList<CharSequence> items = new ArrayList<CharSequence>();
    final ArrayList<Integer> itemToCallType = new ArrayList<Integer>();
    final Resources res = context.getResources();
    // Prepare the string array and mapping.
    if (isDowngradeToAudioSupported(call)) {
      items.add(res.getText(R.string.call_type_voice));
      itemToCallType.add(VideoProfile.STATE_AUDIO_ONLY);
    }
    if (hasVideoCallCapabilities(call)) {
      if(!VideoUtils.isRxOnlyVideoCall(call)){
        items.add(res.getText(R.string.onscreenVideoCallTxText));
        itemToCallType.add(VideoProfile.STATE_TX_ENABLED);
      }
      if(!VideoUtils.isTxOnlyVideoCall(call)){
        items.add(res.getText(R.string.onscreenVideoCallRxText));
        itemToCallType.add(VideoProfile.STATE_RX_ENABLED);
      }
      items.add(res.getText(R.string.incall_label_videocall));
      itemToCallType.add(VideoProfile.STATE_BIDIRECTIONAL);
    }
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(R.string.call_type_title);
    final AlertDialog alert;
    DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int item) {
        Toast.makeText(context, items.get(item), Toast.LENGTH_SHORT).show();
        final int selCallType = itemToCallType.get(item);
        changeToCallTypeClicked(call, selCallType);
        dialog.dismiss();
      }
    };
    final int currUnpausedVideoState = VideoUtils.getUnPausedVideoState(call.getVideoState());
    final int index = itemToCallType.indexOf(currUnpausedVideoState);
    builder.setSingleChoiceItems(items.toArray(new CharSequence[0]), index, listener);
    alert = builder.create();
    alert.show();

  }

  /**
   * Sends a session modify request to the telephony framework
   */
  private void changeToCallTypeClicked(DialerCall call, int videoState) {
    if(call == null){
      Log.d(this,"changeToCallTypeClicked: call = null");
      return;
    }
    if(videoState == VideoProfile.STATE_AUDIO_ONLY){
      call.getVideoTech().degradeToVoice();
    }else{
      if(CallList.getInstance() != null && CallList.getInstance().getBackgroundCall() != null) {
        Toast.makeText(context, R.string.fail_change_video_due_to_bgcall,
                Toast.LENGTH_SHORT).show();//mContext
        return;
      }
      if (CallUtil.isBatteryLow(context)) {
       CallUtil.showLowBatteryChangeToVideoDialog(context,call.getTelecomCall()); //mCall
       }else{
        switch (videoState){
          case VideoProfile.STATE_BIDIRECTIONAL:
            call.getVideoTech().upgradeToVideo(context);
            break;
          case VideoProfile.STATE_RX_ENABLED:
            call.getVideoTech().changeToRxVideo();
            break;
          case VideoProfile.STATE_TX_ENABLED:
            call.getVideoTech().changeToTxVideo(); // UNISOC: modify for bug1137831
            break;
        }
      }
    }
  }
  /* @} */

  /* UNISOC: add for Bug 1146551 1214394 @{ */
  private boolean isSupportUpAndDownGrade(DialerCall call) {
    if (context != null && call != null && (call.isConferenceCall() || call.usedToBeConference())) {
      CarrierConfigManager configManager = (CarrierConfigManager) context.getSystemService(
              Context.CARRIER_CONFIG_SERVICE);
      PhoneAccountHandle accountHandle = call.getAccountHandle();
      int currentSubId = InCallUiUtils.getSubIdForPhoneAccountHandle(context,accountHandle);

      if (-1 == currentSubId) {
        LogUtil.i("CallButtonPresenter", "isSupportUpAndDownGrade getSubIdForPhoneAccountHandle failed");
        return false;
      }
      if (context.checkSelfPermission(READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
              && configManager.getConfigForSubId(currentSubId) != null) {
        return configManager.getConfigForSubId(currentSubId).getBoolean(
                CarrierConfigManagerEx.KEY_SUPPORT_UP_DOWN_GRADE_VT_CONFERENCE);
      } else {
        LogUtil.i("CallButtonPresenter", "isSupportUpAndDownGrade getConfigForSubId = null");
        return false;
      }
    }
    return true;
  }/*@}*/

  private boolean isInLockTaskMode(){ //UNISOC:add for bug1156168
    try {
      if(ActivityManager.getService() != null){
        return ActivityManager.getService().isInLockTaskMode();
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }
}
