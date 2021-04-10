/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.incallui.video.impl;

import android.Manifest.permission;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Point;
import android.graphics.drawable.Animatable;
import android.os.Bundle;
import android.os.SystemClock;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.telecom.CallAudioState;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.android.dialer.common.Assert;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.postcall.PostCall;
import com.android.dialer.util.PermissionsUtil;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment;
import com.android.incallui.audioroute.AudioRouteSelectorDialogFragment.AudioRouteSelectorPresenter;
import com.android.incallui.call.CallList;
import com.android.incallui.call.DialerCall;
import com.android.incallui.contactgrid.ContactGridManager;
import com.android.incallui.hold.OnHoldFragment;
import com.android.incallui.incall.protocol.InCallButtonIds;
import com.android.incallui.incall.protocol.InCallButtonIdsExtension;
import com.android.incallui.incall.protocol.InCallButtonUi;
import com.android.incallui.incall.protocol.InCallButtonUiDelegate;
import com.android.incallui.incall.protocol.InCallButtonUiDelegateFactory;
import com.android.incallui.incall.protocol.InCallScreen;
import com.android.incallui.incall.protocol.InCallScreenDelegate;
import com.android.incallui.incall.protocol.InCallScreenDelegateFactory;
import com.android.incallui.incall.protocol.PrimaryCallState;
import com.android.incallui.incall.protocol.PrimaryInfo;
import com.android.incallui.incall.protocol.SecondaryInfo;
import com.android.incallui.sprd.InCallUiUtils;
import com.android.incallui.video.impl.CheckableImageButton.OnCheckedChangeListener;
import com.android.incallui.video.protocol.VideoCallScreen;
import com.android.incallui.video.protocol.VideoCallScreenDelegate;
import com.android.incallui.video.protocol.VideoCallScreenDelegateFactory;
import com.android.incallui.videosurface.bindings.VideoSurfaceBindings;
import com.android.incallui.videosurface.protocol.VideoSurfaceTexture;
import com.android.incallui.videotech.utils.VideoUtils;
import com.android.incallui.videosurface.impl.VideoSurfaceTextureImpl;//add for bug 1130479

/** Contains UI elements for a video call. */

public class VideoCallFragment extends Fragment
    implements InCallScreen,
        InCallButtonUi,
        VideoCallScreen,
        OnClickListener,
        OnCheckedChangeListener,
        AudioRouteSelectorPresenter,
        OnSystemUiVisibilityChangeListener {

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final String ARG_CALL_ID = "call_id";

  private static final String TAG_VIDEO_CHARGES_ALERT = "tag_video_charges_alert";

  @VisibleForTesting static final float BLUR_PREVIEW_RADIUS = 16.0f;
  @VisibleForTesting static final float BLUR_PREVIEW_SCALE_FACTOR = 1.0f;
  private static final float BLUR_REMOTE_RADIUS = 25.0f;
  private static final float BLUR_REMOTE_SCALE_FACTOR = 0.25f;
  private static final float ASPECT_RATIO_MATCH_THRESHOLD = 0.2f;

  private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
  private static final long CAMERA_PERMISSION_DIALOG_DELAY_IN_MILLIS = 2000L;
  private static final long VIDEO_OFF_VIEW_FADE_OUT_DELAY_IN_MILLIS = 2000L;
  private static final long VIDEO_CHARGES_ALERT_DIALOG_DELAY_IN_MILLIS = 500L;
  private ImageButton mChangeVideoTypeButton;
  private final ViewOutlineProvider circleOutlineProvider =
      new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
          int x = view.getWidth() / 2;
          int y = view.getHeight() / 2;
          int radius = Math.min(x, y);
          outline.setOval(x - radius, y - radius, x + radius, y + radius);
        }
      };

  private InCallScreenDelegate inCallScreenDelegate;
  private VideoCallScreenDelegate videoCallScreenDelegate;
  private InCallButtonUiDelegate inCallButtonUiDelegate;
  private View endCallButton;
  private CheckableImageButton speakerButton;
  private SpeakerButtonController speakerButtonController;
  private CheckableImageButton muteButton;
  private CheckableImageButton cameraOffButton;
  private ImageButton swapCameraButton;
  private ImageButton mOverflowButton;
  private View switchOnHoldButton;
  private View onHoldContainer;
  private SwitchOnHoldCallController switchOnHoldCallController;
  private TextView remoteVideoOff;
  private ImageView remoteOffBlurredImageView;
  private View mutePreviewOverlay;
  private View previewOffOverlay;
  private ImageView previewOffBlurredImageView;
  private View controls;
  private View controlsContainer;
  private TextureView previewTextureView;
  private TextureView remoteTextureView;
  private View greenScreenBackgroundView;
  private View fullscreenBackgroundView;
  private boolean shouldShowRemote;
  private boolean shouldShowPreview;
  private boolean isInFullscreenMode;
  private boolean isInGreenScreenMode;
  private boolean hasInitializedScreenModes;
  private boolean isRemotelyHeld;
  //add for bug 1130479
  private int smallTextureViewIndex = 0;
  private int smallBlurredImageIndex = 0;
  private int bigTextureViewIndex = 0;
  private int bigBlurredImageIndex = 0;
  private ContactGridManager contactGridManager;
  private SecondaryInfo savedSecondaryInfo;
  /* UNISOC: Add video call option menu@{ */
  private PopupMenu mOverflowPopup;
  //The button is currently visible in the UI
  private static final int BUTTON_VISIBLE = 1;
  // The button is hidden in the UI
  private static final int BUTTON_HIDDEN = 2;
  private SparseIntArray mButtonVisibilityMap = new SparseIntArray(InCallButtonIds.BUTTON_COUNT);
  /*@}*/
  private boolean isRecording = false;//UNISOC:add for bug1143842 whether call recording started
  private Context context;
  private final Runnable cameraPermissionDialogRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (videoCallScreenDelegate.shouldShowCameraPermissionToast()) {
            LogUtil.i("VideoCallFragment.cameraPermissionDialogRunnable", "showing dialog");
            checkCameraPermission();
          }
        }
      };

  private final Runnable videoChargesAlertDialogRunnable =
      () -> {
        VideoChargesAlertDialogFragment existingVideoChargesAlertFragment =
            (VideoChargesAlertDialogFragment)
                getChildFragmentManager().findFragmentByTag(TAG_VIDEO_CHARGES_ALERT);
        if (existingVideoChargesAlertFragment != null) {
          LogUtil.i(
              "VideoCallFragment.videoChargesAlertDialogRunnable", "already shown for this call");
          return;
        }

        if (VideoChargesAlertDialogFragment.shouldShow(getContext(), getCallId())) {
          LogUtil.i("VideoCallFragment.videoChargesAlertDialogRunnable", "showing dialog");
          VideoChargesAlertDialogFragment.newInstance(getCallId())
              .show(getChildFragmentManager(), TAG_VIDEO_CHARGES_ALERT);
        }
      };

  public static VideoCallFragment newInstance(String callId) {
    Bundle bundle = new Bundle();
    bundle.putString(ARG_CALL_ID, Assert.isNotNull(callId));

    VideoCallFragment instance = new VideoCallFragment();
    instance.setArguments(bundle);
    return instance;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    LogUtil.i("VideoCallFragment.onCreate", null);

    inCallButtonUiDelegate =
        FragmentUtils.getParent(this, InCallButtonUiDelegateFactory.class)
            .newInCallButtonUiDelegate();
    if (savedInstanceState != null) {
      inCallButtonUiDelegate.onRestoreInstanceState(savedInstanceState);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        LogUtil.i("VideoCallFragment.onRequestPermissionsResult", "Camera permission granted.");
        videoCallScreenDelegate.onCameraPermissionGranted();
      } else {
        LogUtil.i("VideoCallFragment.onRequestPermissionsResult", "Camera permission denied.");
      }
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
    LogUtil.i("VideoCallFragment.onCreateView", null);

    View view =
        layoutInflater.inflate(
            isLandscape() ? R.layout.frag_videocall_land : R.layout.frag_videocall,
            viewGroup,
            false);
    contactGridManager =
        new ContactGridManager(view, null /* no avatar */, 0, false /* showAnonymousAvatar */);

    controls = view.findViewById(R.id.videocall_video_controls);
    controls.setVisibility(getActivity().isInMultiWindowMode() ? View.GONE : View.VISIBLE);
    controlsContainer = view.findViewById(R.id.videocall_video_controls_container);
    speakerButton = (CheckableImageButton) view.findViewById(R.id.videocall_speaker_button);
    muteButton = (CheckableImageButton) view.findViewById(R.id.videocall_mute_button);
    muteButton.setOnCheckedChangeListener(this);
    mutePreviewOverlay = view.findViewById(R.id.videocall_video_preview_mute_overlay);
    cameraOffButton = (CheckableImageButton) view.findViewById(R.id.videocall_mute_video);
    cameraOffButton.setOnCheckedChangeListener(this);
    previewOffOverlay = view.findViewById(R.id.videocall_video_preview_off_overlay);
    previewOffBlurredImageView =
        (ImageView) view.findViewById(R.id.videocall_preview_off_blurred_image_view);
    swapCameraButton = (ImageButton) view.findViewById(R.id.videocall_switch_video);
    swapCameraButton.setOnClickListener(this);
    view.findViewById(R.id.videocall_switch_controls)
        .setVisibility(getActivity().isInMultiWindowMode() ? View.GONE : View.VISIBLE);
    /* UNISOC: Add video call option menu@{ */
    mOverflowButton = (ImageButton) view.findViewById(R.id.videocall_overflow_button);
    mOverflowButton.setOnClickListener(this);
    /*@}*/
    /* UNISOC: Add for change video type feature@{ */
    mChangeVideoTypeButton = (ImageButton) view.findViewById(R.id.videocall_change_type);
    mChangeVideoTypeButton.setOnClickListener(
            new OnClickListener() {
              @Override
              public void onClick(View v) {
                inCallButtonUiDelegate.changeVideoTypeClicked();
              }
            });
    /*@}*/
    switchOnHoldButton = view.findViewById(R.id.videocall_switch_on_hold);
    onHoldContainer = view.findViewById(R.id.videocall_on_hold_banner);
    remoteVideoOff = (TextView) view.findViewById(R.id.videocall_remote_video_off);
    remoteVideoOff.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    remoteOffBlurredImageView =
        (ImageView) view.findViewById(R.id.videocall_remote_off_blurred_image_view);
    endCallButton = view.findViewById(R.id.videocall_end_call);
    endCallButton.setOnClickListener(this);
    previewTextureView = (TextureView) view.findViewById(R.id.videocall_video_preview);
    previewTextureView.setClipToOutline(true);
    previewOffOverlay.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(View v) {
            checkCameraPermission();
          }
        });
    remoteTextureView = (TextureView) view.findViewById(R.id.videocall_video_remote);
    greenScreenBackgroundView = view.findViewById(R.id.videocall_green_screen_background);
    fullscreenBackgroundView = view.findViewById(R.id.videocall_fullscreen_background);
    //add for bug 1130479
    smallTextureViewIndex = ((ViewGroup)previewTextureView.getParent()).indexOfChild(previewTextureView);
    smallBlurredImageIndex = ((ViewGroup)previewOffBlurredImageView.getParent()).indexOfChild(previewOffBlurredImageView);
    bigTextureViewIndex = ((ViewGroup)remoteTextureView.getParent()).indexOfChild(remoteTextureView);
    bigBlurredImageIndex = ((ViewGroup)remoteOffBlurredImageView.getParent()).indexOfChild(remoteOffBlurredImageView);

    remoteTextureView.addOnLayoutChangeListener(
        new OnLayoutChangeListener() {
          @Override
          public void onLayoutChange(
              View v,
              int left,
              int top,
              int right,
              int bottom,
              int oldLeft,
              int oldTop,
              int oldRight,
              int oldBottom) {
            LogUtil.i("VideoCallFragment.onLayoutChange", "remoteTextureView layout changed");
            updateRemoteVideoScaling();
            updateRemoteOffView();
          }
        });

    previewTextureView.addOnLayoutChangeListener(
        new OnLayoutChangeListener() {
          @Override
          public void onLayoutChange(
              View v,
              int left,
              int top,
              int right,
              int bottom,
              int oldLeft,
              int oldTop,
              int oldRight,
              int oldBottom) {
            LogUtil.i("VideoCallFragment.onLayoutChange", "previewTextureView layout changed");
            updatePreviewVideoScaling();
            updatePreviewOffView();
          }
        });
    return view;
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle bundle) {
    super.onViewCreated(view, bundle);
    LogUtil.i("VideoCallFragment.onViewCreated", null);

    inCallScreenDelegate =
        FragmentUtils.getParentUnsafe(this, InCallScreenDelegateFactory.class)
            .newInCallScreenDelegate();
    videoCallScreenDelegate =
        FragmentUtils.getParentUnsafe(this, VideoCallScreenDelegateFactory.class)
            .newVideoCallScreenDelegate(this);

    speakerButtonController =
        new SpeakerButtonController(speakerButton, inCallButtonUiDelegate, videoCallScreenDelegate);
    switchOnHoldCallController =
        new SwitchOnHoldCallController(
            switchOnHoldButton, onHoldContainer, inCallScreenDelegate, videoCallScreenDelegate);

    videoCallScreenDelegate.initVideoCallScreenDelegate(getContext(), this);
    //add for bug 113479
    videoCallScreenDelegate.getLocalVideoSurfaceTexture().attachToTextureView(previewTextureView);
    videoCallScreenDelegate.getLocalVideoSurfaceTexture().attachToImageView(previewOffBlurredImageView);
    videoCallScreenDelegate.getRemoteVideoSurfaceTexture().attachToTextureView(remoteTextureView);
    videoCallScreenDelegate.getRemoteVideoSurfaceTexture().attachToImageView(remoteOffBlurredImageView);

    inCallScreenDelegate.onInCallScreenDelegateInit(this);
    inCallScreenDelegate.onInCallScreenReady();
    inCallButtonUiDelegate.onInCallButtonUiReady(this);

    view.setOnSystemUiVisibilityChangeListener(this);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    inCallButtonUiDelegate.onSaveInstanceState(outState);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    LogUtil.i("VideoCallFragment.onDestroyView", null);
    inCallButtonUiDelegate.onInCallButtonUiUnready();
    inCallScreenDelegate.onInCallScreenUnready();
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    LogUtil.i("VideoCallFragment.onAttach", null);
    this.context = Assert.isNotNull(context).getApplicationContext(); //add for bug1145284
    if (savedSecondaryInfo != null) {
      setSecondary(savedSecondaryInfo);
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    LogUtil.i("VideoCallFragment.onStart", null);
    onVideoScreenStart();
    onVideoCallIsFront(); //UNISOC: add for bug1166982
  }

  @Override
  public void onVideoScreenStart() {
    inCallButtonUiDelegate.refreshMuteState();
    videoCallScreenDelegate.onVideoCallScreenUiReady();
    getView().postDelayed(cameraPermissionDialogRunnable, CAMERA_PERMISSION_DIALOG_DELAY_IN_MILLIS);
    getView()
        .postDelayed(videoChargesAlertDialogRunnable, VIDEO_CHARGES_ALERT_DIALOG_DELAY_IN_MILLIS);
  }

  @Override
  public void onResume() {
    super.onResume();
    LogUtil.i("VideoCallFragment.onResume", null);
    inCallScreenDelegate.onInCallScreenResumed();
    if(!videoCallScreenDelegate.getLocalVideoSurfaceTexture().getSmallSurface()) {
      // UNISOC: add for bug1181012
      if (getCall() != null && getCall().isConferenceCall()) {
        LogUtil.i("VideoCallFragment.onResume", "PreviewTexture is small for call is conference");
        remoteSurfaceClickForChange();
      } else {
        LogUtil.i("VideoCallFragment.onResume", "PreviewTexture is big");
        localSurfaceClickForChange();
      }
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    LogUtil.i("VideoCallFragment.onPause", null);
    inCallScreenDelegate.onInCallScreenPaused();
  }

  @Override
  public void onStop() {
    super.onStop();
    LogUtil.i("VideoCallFragment.onStop", null);
    onVideoScreenStop();
    onVideoCallIsBack();// UNISOC:add for bug1166982
  }

  @Override
  public void onVideoScreenStop() {
    getView().removeCallbacks(videoChargesAlertDialogRunnable);
    getView().removeCallbacks(cameraPermissionDialogRunnable);
    videoCallScreenDelegate.onVideoCallScreenUiUnready();
    if (mOverflowPopup != null && mOverflowPopup.getMenu() != null
            && mOverflowPopup.getMenu().hasVisibleItems()) { //UNISOC:add for bug1110191
      mOverflowPopup.dismiss();
    }
  }

  private void exitFullscreenMode() {
    LogUtil.i("VideoCallFragment.exitFullscreenMode", null);

    if (!getView().isAttachedToWindow()) {
      LogUtil.i("VideoCallFragment.exitFullscreenMode", "not attached");
      return;
    }

    showSystemUI();

    LinearOutSlowInInterpolator linearOutSlowInInterpolator = new LinearOutSlowInInterpolator();

    // Animate the controls to the shown state.
    controls
        .animate()
        .translationX(0)
        .translationY(0)
        .setInterpolator(linearOutSlowInInterpolator)
        .alpha(1)
        .start();

    // Animate onHold to the shown state.
    switchOnHoldButton
        .animate()
        .translationX(0)
        .translationY(0)
        .setInterpolator(linearOutSlowInInterpolator)
        .alpha(1)
        .withStartAction(
            new Runnable() {
              @Override
              public void run() {
                switchOnHoldCallController.setOnScreen();
              }
            });

    View contactGridView = contactGridManager.getContainerView();
    // Animate contact grid to the shown state.
    contactGridView
        .animate()
        .translationX(0)
        .translationY(0)
        .setInterpolator(linearOutSlowInInterpolator)
        .alpha(1)
        .withStartAction(
            new Runnable() {
              @Override
              public void run() {
                contactGridManager.show();
              }
            });

    endCallButton
        .animate()
        .translationX(0)
        .translationY(0)
        .setInterpolator(linearOutSlowInInterpolator)
        .alpha(1)
        .withStartAction(
            new Runnable() {
              @Override
              public void run() {
                endCallButton.setVisibility(View.VISIBLE);
              }
            })
        .start();

    // Animate all the preview controls up to make room for the navigation bar.
    // In green screen mode we don't need this because the preview takes up the whole screen and has
    // a fixed position.
    if (!isInGreenScreenMode) {
      Point previewOffsetStartShown = getPreviewOffsetStartShown();
      for (View view : getAllPreviewRelatedViews()) {
        // Animate up with the preview offset above the navigation bar.
        view.animate()
            .translationX(previewOffsetStartShown.x)
            .translationY(previewOffsetStartShown.y)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
      }
    }

    updateOverlayBackground();
  }

  private void showSystemUI() {
    View view = getView();
    if (view != null) {
      // Code is more expressive with all flags present, even though some may be combined
      // noinspection PointlessBitwiseExpression
      view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
  }

  /** Set view flags to hide the system UI. System UI will return on any touch event */
  private void hideSystemUI() {
    View view = getView();
    if (view != null) {
      view.setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
  }

  private Point getControlsOffsetEndHidden(View controls) {
    if (isLandscape()) {
      return new Point(0, getOffsetBottom(controls));
    } else {
      return new Point(getOffsetStart(controls), 0);
    }
  }

  private Point getSwitchOnHoldOffsetEndHidden(View swapCallButton) {
    if (isLandscape()) {
      return new Point(0, getOffsetTop(swapCallButton));
    } else {
      return new Point(getOffsetEnd(swapCallButton), 0);
    }
  }

  private Point getContactGridOffsetEndHidden(View view) {
    return new Point(0, getOffsetTop(view));
  }

  private Point getEndCallOffsetEndHidden(View endCallButton) {
    if (isLandscape()) {
      return new Point(getOffsetEnd(endCallButton), 0);
    } else {
      return new Point(0, ((MarginLayoutParams) endCallButton.getLayoutParams()).bottomMargin);
    }
  }

  private Point getPreviewOffsetStartShown() {
    // No insets in multiwindow mode, and rootWindowInsets will get the display's insets.
    if (getActivity().isInMultiWindowMode()) {
      return new Point();
    }
    if (isLandscape()) {
      int stableInsetEnd =
          getView().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
              ? getView().getRootWindowInsets().getStableInsetLeft()
              : -getView().getRootWindowInsets().getStableInsetRight();
      return new Point(stableInsetEnd, 0);
    } else {
      return new Point(0, -getView().getRootWindowInsets().getStableInsetBottom());
    }
  }

  private View[] getAllPreviewRelatedViews() {
    return new View[] {
      previewTextureView, previewOffOverlay, previewOffBlurredImageView, mutePreviewOverlay,
    };
  }

  private int getOffsetTop(View view) {
    return -(view.getHeight() + ((MarginLayoutParams) view.getLayoutParams()).topMargin);
  }

  private int getOffsetBottom(View view) {
    return view.getHeight() + ((MarginLayoutParams) view.getLayoutParams()).bottomMargin;
  }

  private int getOffsetStart(View view) {
    int offset = view.getWidth() + ((MarginLayoutParams) view.getLayoutParams()).getMarginStart();
    if (view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
      offset = -offset;
    }
    return -offset;
  }

  private int getOffsetEnd(View view) {
    int offset = view.getWidth() + ((MarginLayoutParams) view.getLayoutParams()).getMarginEnd();
    if (view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
      offset = -offset;
    }
    return offset;
  }

  private void enterFullscreenMode() {
    LogUtil.i("VideoCallFragment.enterFullscreenMode", null);

    hideSystemUI();

    Interpolator fastOutLinearInInterpolator = new FastOutLinearInInterpolator();

    // Animate controls to the hidden state.
    Point offset = getControlsOffsetEndHidden(controls);
    controls
        .animate()
        .translationX(offset.x)
        .translationY(offset.y)
        .setInterpolator(fastOutLinearInInterpolator)
        .alpha(0)
        .start();

    // Animate onHold to the hidden state.
    offset = getSwitchOnHoldOffsetEndHidden(switchOnHoldButton);
    switchOnHoldButton
        .animate()
        .translationX(offset.x)
        .translationY(offset.y)
        .setInterpolator(fastOutLinearInInterpolator)
        .alpha(0);

    View contactGridView = contactGridManager.getContainerView();
    // Animate contact grid to the hidden state.
    offset = getContactGridOffsetEndHidden(contactGridView);
    contactGridView
        .animate()
        .translationX(offset.x)
        .translationY(offset.y)
        .setInterpolator(fastOutLinearInInterpolator)
        .alpha(0);

    offset = getEndCallOffsetEndHidden(endCallButton);
    // Use a fast out interpolator to quickly fade out the button. This is important because the
    // button can't draw under the navigation bar which means that it'll look weird if it just
    // abruptly disappears when it reaches the edge of the naivgation bar.
    endCallButton
        .animate()
        .translationX(offset.x)
        .translationY(offset.y)
        .setInterpolator(fastOutLinearInInterpolator)
        .alpha(0)
        .withEndAction(
            new Runnable() {
              @Override
              public void run() {
                endCallButton.setVisibility(View.INVISIBLE);
              }
            })
        .setInterpolator(new FastOutLinearInInterpolator())
        .start();

    // Animate all the preview controls down now that the navigation bar is hidden.
    // In green screen mode we don't need this because the preview takes up the whole screen and has
    // a fixed position.
    if (!isInGreenScreenMode) {
      for (View view : getAllPreviewRelatedViews()) {
        // Animate down with the navigation bar hidden.
        view.animate()
            .translationX(0)
            .translationY(0)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
      }
    }
    updateOverlayBackground();
    //UNISOC: Add video call option menu
    if (mOverflowPopup != null && mOverflowPopup.getMenu() != null && mOverflowPopup.getMenu().hasVisibleItems()) {
        mOverflowPopup.dismiss();
    }
  }

  @Override
  public void onClick(View v) {
    if (v == endCallButton) {
      LogUtil.i("VideoCallFragment.onClick", "end call button clicked");
      inCallButtonUiDelegate.onEndCallClicked();
      videoCallScreenDelegate.resetAutoFullscreenTimer();
      PostCall.onDisconnectPressed(context);//add for bug1145284
    } else if (v == swapCameraButton) {
      if (swapCameraButton.getDrawable() instanceof Animatable) {
          /* UNISOC: add for bug1177044(1111450) {@*/
          Animatable swapAnime = (Animatable) swapCameraButton.getDrawable();
          if (swapAnime.isRunning()) {
              swapAnime.stop();
          }
          swapAnime.start();
          /* @} */
      }
      inCallButtonUiDelegate.toggleCameraClicked();
      videoCallScreenDelegate.resetAutoFullscreenTimer();
    } else if (v == mOverflowButton){  //UNISOC: Add video call option menu
        if (mOverflowPopup != null) {
            mOverflowPopup.show();
        }
    }
  }

  @Override
  public void onCheckedChanged(CheckableImageButton button, boolean isChecked) {
    if (button == cameraOffButton) {
      if (!isChecked && !VideoUtils.hasCameraPermissionAndShownPrivacyToast(getContext())) {
        LogUtil.i("VideoCallFragment.onCheckedChanged", "show camera permission dialog");
        checkCameraPermission();
      } else {
        inCallButtonUiDelegate.pauseVideoClicked(isChecked);
        videoCallScreenDelegate.resetAutoFullscreenTimer();
      }
    } else if (button == muteButton) {
      inCallButtonUiDelegate.muteClicked(isChecked, true /* clickedByUser */);
      videoCallScreenDelegate.resetAutoFullscreenTimer();
    }
  }

  @Override
  public void showVideoViews(
      boolean shouldShowPreview, boolean shouldShowRemote, boolean isRemotelyHeld) {
    LogUtil.i(
        "VideoCallFragment.showVideoViews",
        "showPreview: %b, shouldShowRemote: %b",
        shouldShowPreview,
        shouldShowRemote);


    // UNISOC: add for bug1147261
    if (this.shouldShowRemote != shouldShowRemote || this.isRemotelyHeld != isRemotelyHeld) {
      this.isRemotelyHeld = isRemotelyHeld;
      this.shouldShowRemote = shouldShowRemote;
      updateRemoteOffView();
    }
    if (this.shouldShowPreview != shouldShowPreview) {
      this.shouldShowPreview = shouldShowPreview;
      updatePreviewOffView();
    }
  }

  @Override
  public void onLocalVideoDimensionsChanged() {
    LogUtil.i("VideoCallFragment.onLocalVideoDimensionsChanged", null);
    updatePreviewVideoScaling();
  }

  @Override
  public void onLocalVideoOrientationChanged() {
    LogUtil.i("VideoCallFragment.onLocalVideoOrientationChanged", null);
    updatePreviewVideoScaling();
  }

  /** Called when the remote video's dimensions change. */
  @Override
  public void onRemoteVideoDimensionsChanged() {
    LogUtil.i("VideoCallFragment.onRemoteVideoDimensionsChanged", null);
    updateRemoteVideoScaling();
  }

  @Override
  public void updateFullscreenAndGreenScreenMode(
      boolean shouldShowFullscreen, boolean shouldShowGreenScreen) {
    LogUtil.i(
        "VideoCallFragment.updateFullscreenAndGreenScreenMode",
        "shouldShowFullscreen: %b, shouldShowGreenScreen: %b",
        shouldShowFullscreen,
        shouldShowGreenScreen);

    if (getActivity() == null) {
      LogUtil.i("VideoCallFragment.updateFullscreenAndGreenScreenMode", "not attached to activity");
      return;
    }

    // Check if anything is actually going to change. The first time this function is called we
    // force a change by checking the hasInitializedScreenModes flag. We also force both fullscreen
    // and green screen modes to update even if only one has changed. That's because they both
    // depend on each other.
    if (hasInitializedScreenModes
        && shouldShowGreenScreen == isInGreenScreenMode
        && shouldShowFullscreen == isInFullscreenMode) {
      LogUtil.i(
          "VideoCallFragment.updateFullscreenAndGreenScreenMode", "no change to screen modes");
      return;
    }
    hasInitializedScreenModes = true;
    isInGreenScreenMode = shouldShowGreenScreen;
    isInFullscreenMode = shouldShowFullscreen;

    if (getView().isAttachedToWindow() && !getActivity().isInMultiWindowMode()) {
      controlsContainer.onApplyWindowInsets(getView().getRootWindowInsets());
    }
    // UNISOC: modify for bug1155897
    if (shouldShowGreenScreen && !InCallUiUtils.isSupportRingTone(getContext(), getCall())) {
      enterGreenScreenMode();
    } else if(videoCallScreenDelegate.getLocalVideoSurfaceTexture().getSmallSurface()) {
      exitGreenScreenMode();
    } else if (remoteTextureView != null) {
      remoteTextureView.setVisibility(View.VISIBLE);
    }
    if (shouldShowFullscreen) {
      enterFullscreenMode();
    } else {
      exitFullscreenMode();
    }

    OnHoldFragment onHoldFragment =
        ((OnHoldFragment)
            getChildFragmentManager().findFragmentById(R.id.videocall_on_hold_banner));
    if (onHoldFragment != null) {
      onHoldFragment.setPadTopInset(!isInFullscreenMode);
    }
  }

  @Override
  public Fragment getVideoCallScreenFragment() {
    return this;
  }

  @Override
  @NonNull
  public String getCallId() {
    return Assert.isNotNull(getArguments().getString(ARG_CALL_ID));
  }

  @Override
  public void onHandoverFromWiFiToLte() {
    getView().post(videoChargesAlertDialogRunnable);
  }

  @Override
  public void showButton(@InCallButtonIds int buttonId, boolean show) {
    LogUtil.i(
            "VideoCallFragment.showButton",
            " buttonId:"+InCallButtonIdsExtension.toString(buttonId) + " show:"+show);
    if (buttonId == InCallButtonIds.BUTTON_AUDIO) {
      speakerButtonController.setEnabled(show);
    } else if (buttonId == InCallButtonIds.BUTTON_MUTE) {
      muteButton.setEnabled(show);
    } else if (buttonId == InCallButtonIds.BUTTON_PAUSE_VIDEO) {
      cameraOffButton.setVisibility(show ? View.VISIBLE : View.GONE); // UNISOC: add for bug1152075
    } else if (buttonId == InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY) {
      switchOnHoldCallController.setVisible(show);
    } else if (buttonId == InCallButtonIds.BUTTON_SWITCH_CAMERA) {
      swapCameraButton.setEnabled(show);
    } else if (buttonId == InCallButtonIds.BUTTON_ADD_CALL
            || buttonId == InCallButtonIds.BUTTON_MERGE
            || buttonId == InCallButtonIds.BUTTON_HOLD
            || buttonId == InCallButtonIds.BUTTON_SWAP
            || buttonId == InCallButtonIds.BUTTON_DOWNGRADE_TO_AUDIO
            || buttonId == InCallButtonIds.BUTTON_DIALPAD // UNISOC: add for bug1152075
            || buttonId == InCallButtonIds.BUTTON_RECORD){
      mButtonVisibilityMap.put(buttonId, show ? BUTTON_VISIBLE : BUTTON_HIDDEN);
    }//UNISOC: Add video call option menu
    else if (buttonId == InCallButtonIds.BUTTON_CHANGE_VIDEO_TYPE) { // UNISOC:add for bug1137837 1137838
      mChangeVideoTypeButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }
  }

  @Override
  public void enableButton(@InCallButtonIds int buttonId, boolean enable) {
    LogUtil.i(
            "VideoCallFragment.setEnabled",
            " buttonId:" + InCallButtonIdsExtension.toString(buttonId) + " enable:" + enable);
    if (buttonId == InCallButtonIds.BUTTON_AUDIO) {
      speakerButtonController.setEnabled(enable);
    } else if (buttonId == InCallButtonIds.BUTTON_MUTE) {
      muteButton.setEnabled(enable);
    } else if (buttonId == InCallButtonIds.BUTTON_PAUSE_VIDEO) {
      cameraOffButton.setEnabled(enable);
    } else if (buttonId == InCallButtonIds.BUTTON_SWITCH_TO_SECONDARY) {
      switchOnHoldCallController.setEnabled(enable);
    } else if (buttonId == InCallButtonIds.BUTTON_ADD_CALL
            || buttonId == InCallButtonIds.BUTTON_DIALPAD // UNISOC: add for bug1152075
            || buttonId == InCallButtonIds.BUTTON_MERGE
            || buttonId == InCallButtonIds.BUTTON_HOLD
            || buttonId == InCallButtonIds.BUTTON_SWAP
            || buttonId == InCallButtonIds.BUTTON_DOWNGRADE_TO_AUDIO){
      mButtonVisibilityMap.put(buttonId, enable ? BUTTON_VISIBLE : BUTTON_HIDDEN);
    }//UNISOC: Add video call option menu
  }

  @Override
  public void setEnabled(boolean enabled) {
    LogUtil.i("VideoCallFragment.setEnabled", "enabled: " + enabled);
    speakerButtonController.setEnabled(enabled);
    speakerButton.setEnabled(enabled);//add for bug1151371
    muteButton.setEnabled(enabled);
    cameraOffButton.setEnabled(enabled);
    switchOnHoldCallController.setEnabled(enabled);
    //add for bug1151371
    swapCameraButton.setEnabled(enabled);
    mOverflowButton.setEnabled(enabled);

    mOverflowButton.setEnabled(enabled);//UNISOC: Add video call option menu
    /* UNISOC: Add for change video type feature@{ */
    mChangeVideoTypeButton.setEnabled(enabled);
    /*@}*/
  }

  @Override
  public void setHold(boolean value) {
    LogUtil.i("VideoCallFragment.setHold", "value: " + value);
    /* UNISOC: Add video call option menu@{ */
    if(mOverflowPopup == null){
        LogUtil.i("VideoCallFragment.setHold", "mOverflowPopup is null ");
        return;
    }
    MenuItem menuItem = mOverflowPopup.getMenu().findItem(R.id.hold_call_menu);
    if ( menuItem != null && menuItem.isChecked()!= value) {
        menuItem.setChecked(value);
        menuItem.setTitle(getContext().getString(
                value ? R.string.incall_content_description_hold
                        : R.string.incall_content_description_unhold));
    }
    /*@}*/
  }

  @Override
  public void setCameraSwitched(boolean isBackFacingCamera) {
    LogUtil.i("VideoCallFragment.setCameraSwitched", "isBackFacingCamera: " + isBackFacingCamera);
  }

  @Override
  public void setVideoPaused(boolean isPaused) {
    LogUtil.i("VideoCallFragment.setVideoPaused", "isPaused: " + isPaused);
    cameraOffButton.setChecked(isPaused);
  }

  @Override
  public void setAudioState(CallAudioState audioState) {
    LogUtil.i("VideoCallFragment.setAudioState", "audioState: " + audioState);
    speakerButtonController.setAudioState(audioState);
    muteButton.setChecked(audioState.isMuted());
    updateMutePreviewOverlayVisibility();
  }

  @Override
  public void updateButtonStates() {
    LogUtil.i("VideoCallFragment.updateButtonState", null);
    speakerButtonController.updateButtonState();
    switchOnHoldCallController.updateButtonState();

    /* UNISOC: Add video call option menu@{ */
    /* UNISOC:modify for bug608545 @ { */
    if (mOverflowPopup != null && mOverflowPopup.getMenu() != null && mOverflowPopup.getMenu().hasVisibleItems()) {
        mOverflowPopup.dismiss();
    }
    /* @} */
    mOverflowPopup = new PopupMenu(getActivity(), mOverflowButton);
    mOverflowPopup.getMenuInflater().inflate(R.menu.videocall_option_menu, mOverflowPopup.getMenu());

    Menu menu = mOverflowPopup.getMenu();
    int count = menu.size();
    for (int i = 0; i < count; i++) {
        MenuItem item = menu.getItem(i);
        boolean visible = false;
        switch (item.getItemId()) {
            case R.id.add_call_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_ADD_CALL) == BUTTON_VISIBLE ? true : false;
                break;
            case R.id.merge_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_MERGE) == BUTTON_VISIBLE ? true : false;
                break;
            case R.id.hold_call_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_HOLD) == BUTTON_VISIBLE ? true : false;
                break;
            case R.id.swap_call_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_SWAP) == BUTTON_VISIBLE ? true : false;
                break;
            case R.id.changeto_audio_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_DOWNGRADE_TO_AUDIO) == BUTTON_VISIBLE ? true : false;
                break;
            // UNISOC: add for bug1143842
            case R.id.call_record_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_RECORD) == BUTTON_VISIBLE ? true : false;
                break;
            // UNISOC: add for bug1142881
            case R.id.manager_conference_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_MANAGE_VIDEO_CONFERENCE) == BUTTON_VISIBLE ? true : false;
                break;
            // UNISOC: add for bug1152075
            case R.id.dialpad_menu:
                visible = mButtonVisibilityMap.get(InCallButtonIds.BUTTON_DIALPAD) == BUTTON_VISIBLE ? true : false;
                break;
        }
        item.setVisible(visible);
    }
    mOverflowPopup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            selectMenuItem(item);
            return true;
        }
    });
    mOverflowButton.setVisibility((mOverflowPopup != null && mOverflowPopup.getMenu() != null && mOverflowPopup.getMenu().hasVisibleItems()) ? View.VISIBLE : View.GONE);
    /*@}*/
  }

  @Override
  public void updateInCallButtonUiColors(@ColorInt int color) {}

  @Override
  public Fragment getInCallButtonUiFragment() {
    return this;
  }

  @Override
  public void showAudioRouteSelector() {
    LogUtil.i("VideoCallFragment.showAudioRouteSelector", null);
    AudioRouteSelectorDialogFragment.newInstance(inCallButtonUiDelegate.getCurrentAudioState())
        .show(getChildFragmentManager(), null);
  }

  @Override
  public void onAudioRouteSelected(int audioRoute) {
    LogUtil.i("VideoCallFragment.onAudioRouteSelected", "audioRoute: " + audioRoute);
    inCallButtonUiDelegate.setAudioRoute(audioRoute);
  }

  @Override
  public void onAudioRouteSelectorDismiss() {}

  @Override
  public void setPrimary(@NonNull PrimaryInfo primaryInfo) {
    LogUtil.i("VideoCallFragment.setPrimary", primaryInfo.toString());
    contactGridManager.setPrimary(primaryInfo);
  }

  @Override
  public void setSecondary(@NonNull SecondaryInfo secondaryInfo) {
    LogUtil.i("VideoCallFragment.setSecondary", secondaryInfo.toString());
    if (!isAdded()) {
      savedSecondaryInfo = secondaryInfo;
      return;
    }
    savedSecondaryInfo = null;
    switchOnHoldCallController.setSecondaryInfo(secondaryInfo);
    updateButtonStates();
    FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
    Fragment oldBanner = getChildFragmentManager().findFragmentById(R.id.videocall_on_hold_banner);
    if (secondaryInfo.shouldShow()) {
      OnHoldFragment onHoldFragment = OnHoldFragment.newInstance(secondaryInfo);
      onHoldFragment.setPadTopInset(!isInFullscreenMode);
      transaction.replace(R.id.videocall_on_hold_banner, onHoldFragment);
    } else {
      if (oldBanner != null) {
        transaction.remove(oldBanner);
      }
    }
    transaction.setCustomAnimations(R.anim.abc_slide_in_top, R.anim.abc_slide_out_top);
    transaction.commitAllowingStateLoss();
  }

  @Override
  public void setCallState(@NonNull PrimaryCallState primaryCallState) {
    LogUtil.i("VideoCallFragment.setCallState", primaryCallState.toString());
    contactGridManager.setCallState(primaryCallState);
  }

  @Override
  public void setEndCallButtonEnabled(boolean enabled, boolean animate) {
    // add for bug1151371
    if (endCallButton != null) {
      endCallButton.setEnabled(enabled);
    }
    LogUtil.i("VideoCallFragment.setEndCallButtonEnabled", "enabled: " + enabled);
  }

  @Override
  public void showManageConferenceCallButton(boolean visible) {
    LogUtil.i("VideoCallFragment.showManageConferenceCallButton", "visible: " + visible);
    mButtonVisibilityMap.put(InCallButtonIds.BUTTON_MANAGE_VIDEO_CONFERENCE, visible ? BUTTON_VISIBLE : BUTTON_HIDDEN);//modify sor bug1182821
    // UNISOC: add for bug1142881 1168216 1176681 1185265
    if (visible) {
      updateButtonStates();
    }
  }

  @Override
  public boolean isManageConferenceVisible() {
    LogUtil.i("VideoCallFragment.isManageConferenceVisible", null);
    // UNISOC: add for bug1142881
    return mButtonVisibilityMap.get(InCallButtonIds.BUTTON_MANAGE_VIDEO_CONFERENCE) == BUTTON_VISIBLE ? true :false;
  }

  @Override
  public void dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
    contactGridManager.dispatchPopulateAccessibilityEvent(event);
  }

  @Override
  public void showNoteSentToast() {
    LogUtil.i("VideoCallFragment.showNoteSentToast", null);
  }

  @Override
  public void updateInCallScreenColors() {
    LogUtil.i("VideoCallFragment.updateColors", null);
  }

  @Override
  public void onInCallScreenDialpadVisibilityChange(boolean isShowing) {
    LogUtil.i("VideoCallFragment.onInCallScreenDialpadVisibilityChange", null);
  }

  @Override
  public int getAnswerAndDialpadContainerResourceId() {
    // UNISOC: modify for bug1152075
    return R.id.videocall_dialpad_container;
  }

  @Override
  public Fragment getInCallScreenFragment() {
    return this;
  }

  @Override
  public boolean isShowingLocationUi() {
    return false;
  }

  @Override
  public void showLocationUi(Fragment locationUi) {
    LogUtil.e("VideoCallFragment.showLocationUi", "Emergency video calling not supported");
    // Do nothing
  }

  private void updatePreviewVideoScaling() {
    if (previewTextureView.getWidth() == 0 || previewTextureView.getHeight() == 0) {
      LogUtil.i("VideoCallFragment.updatePreviewVideoScaling", "view layout hasn't finished yet");
      return;
    }
    VideoSurfaceTexture localVideoSurfaceTexture =
        videoCallScreenDelegate.getLocalVideoSurfaceTexture();
    Point cameraDimensions = localVideoSurfaceTexture.getSurfaceDimensions();
    if (cameraDimensions == null) {
      LogUtil.i(
          "VideoCallFragment.updatePreviewVideoScaling", "camera dimensions haven't been set");
      return;
    }
    /* UNISOC: bug1146954 1155897 (900278,905887)@{ */
    if((isInGreenScreenMode || !videoCallScreenDelegate.getLocalVideoSurfaceTexture().getSmallSurface())
            && !InCallUiUtils.isSupportRingTone(getContext(), getCall())) {
      if (isLandscape()) {
        VideoSurfaceBindings.scaleVideoMaintainingAspectRatioWithRot(
                previewTextureView,
                cameraDimensions.y,
                cameraDimensions.x,
                videoCallScreenDelegate.getDeviceOrientation());
      }else{
        VideoSurfaceBindings.scaleVideoMaintainingAspectRatioWithRot(
                previewTextureView,
                cameraDimensions.x,
                cameraDimensions.y,
                videoCallScreenDelegate.getDeviceOrientation());
      }
    } else {
      if (isLandscape()) {
        VideoSurfaceBindings.scaleVideoAndFillView(
                previewTextureView,
                cameraDimensions.y,
                cameraDimensions.x,
                videoCallScreenDelegate.getDeviceOrientation());
      } else {
        VideoSurfaceBindings.scaleVideoAndFillView(
                previewTextureView,
                cameraDimensions.x,
                cameraDimensions.y,
                videoCallScreenDelegate.getDeviceOrientation());
      }
    }
    /*@}*/
  }

  private void updateRemoteVideoScaling() {
    VideoSurfaceTexture remoteVideoSurfaceTexture =
        videoCallScreenDelegate.getRemoteVideoSurfaceTexture();
    Point videoSize = remoteVideoSurfaceTexture.getSourceVideoDimensions();
    if (videoSize == null) {
      LogUtil.i("VideoCallFragment.updateRemoteVideoScaling", "video size is null");
      return;
    }
    if (remoteTextureView.getWidth() == 0 || remoteTextureView.getHeight() == 0) {
      LogUtil.i("VideoCallFragment.updateRemoteVideoScaling", "view layout hasn't finished yet");
      return;
    }

    // If the video and display aspect ratio's are close then scale video to fill display
    float videoAspectRatio = ((float) videoSize.x) / videoSize.y;
    float displayAspectRatio =
        ((float) remoteTextureView.getWidth()) / remoteTextureView.getHeight();
    float delta = Math.abs(videoAspectRatio - displayAspectRatio);
    float sum = videoAspectRatio + displayAspectRatio;
    if (delta / sum < ASPECT_RATIO_MATCH_THRESHOLD) {
      // UNISOC: modify for bug1212023
      if (remoteVideoSurfaceTexture.getSmallSurface()) {
        VideoSurfaceBindings.scaleVideoAndFillView(remoteTextureView, videoSize.x, videoSize.y, 0);
      } else {
        LogUtil.i("VideoCallFragment.updateRemoteVideoScaling", "is not samllSurface");
        VideoSurfaceBindings.scaleVideoMaintainingAspectRatioWithRot(remoteTextureView, videoSize.x, videoSize.y, 0);
      }
    } else {
      VideoSurfaceBindings.scaleVideoMaintainingAspectRatio(
          remoteTextureView, videoSize.x, videoSize.y);
    }
  }

  private boolean isLandscape() {
    // Choose orientation based on display orientation, not window orientation
    int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
    return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
  }

  private void enterGreenScreenMode() {
    LogUtil.i("VideoCallFragment.enterGreenScreenMode", null);
    RelativeLayout.LayoutParams params =
        new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
    params.addRule(RelativeLayout.ALIGN_PARENT_START);
    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
    previewTextureView.setLayoutParams(params);
    previewTextureView.setOutlineProvider(null);
    updateOverlayBackground();
    // UNISOC: modify for bug1155897
    contactGridManager.setIsMiddleRowVisible(isInGreenScreenMode ? true : false);
    updateMutePreviewOverlayVisibility();

    previewOffBlurredImageView.setLayoutParams(params);
    previewOffBlurredImageView.setOutlineProvider(null);
    previewOffBlurredImageView.setClipToOutline(false);
    //UNISOC:add for bug1147201
    if (remoteTextureView != null) {
      remoteTextureView.setVisibility(View.GONE);
    }
  }

  private void exitGreenScreenMode() {
    LogUtil.i("VideoCallFragment.exitGreenScreenMode", null);
    Resources resources = getResources();
    RelativeLayout.LayoutParams params =
        new RelativeLayout.LayoutParams(
            (int) resources.getDimension(R.dimen.videocall_preview_width),
            (int) resources.getDimension(R.dimen.videocall_preview_height));
    params.setMargins(
        0, 0, 0, (int) resources.getDimension(R.dimen.videocall_preview_margin_bottom));
    if (isLandscape()) {
      params.addRule(RelativeLayout.ALIGN_PARENT_END);
      params.setMarginEnd((int) resources.getDimension(R.dimen.videocall_preview_margin_end));
    } else {
      params.addRule(RelativeLayout.ALIGN_PARENT_START);
      params.setMarginStart((int) resources.getDimension(R.dimen.videocall_preview_margin_start));
    }
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
    previewTextureView.setLayoutParams(params);
    previewTextureView.setOutlineProvider(circleOutlineProvider);
    updateOverlayBackground();
    contactGridManager.setIsMiddleRowVisible(false);
    updateMutePreviewOverlayVisibility();

    previewOffBlurredImageView.setLayoutParams(params);
    previewOffBlurredImageView.setOutlineProvider(circleOutlineProvider);
    previewOffBlurredImageView.setClipToOutline(true);
    //UNISOC:add for bug1147201
    if (remoteTextureView != null) {
      remoteTextureView.setVisibility(View.VISIBLE);
    }
  }

  private void updatePreviewOffView() {
    LogUtil.enterBlock("VideoCallFragment.updatePreviewOffView");

    // Always hide the preview off and remote off views in green screen mode.
    boolean previewEnabled = isInGreenScreenMode || shouldShowPreview;
    previewOffOverlay.setVisibility(previewEnabled
            // UNISOC: add for bug1209744
            || (getContext() != null && getContext().getResources().getBoolean(R.bool.config_hideConferencePreviewView))
            ? View.GONE : View.VISIBLE);
    // UNISOC: modify for bug1155897
    if (videoCallScreenDelegate.isRingToneOnAudioCall()) {
      previewTextureView.setVisibility(shouldShowPreview ? View.VISIBLE : View.INVISIBLE);
    }
    updateBlurredImageView(
        previewTextureView,
        previewOffBlurredImageView,
        shouldShowPreview,
        BLUR_PREVIEW_RADIUS,
        BLUR_PREVIEW_SCALE_FACTOR);
  }

  private void updateRemoteOffView() {
    LogUtil.enterBlock("VideoCallFragment.updateRemoteOffView");
    boolean remoteEnabled = isInGreenScreenMode || shouldShowRemote;
    boolean isResumed = remoteEnabled && !isRemotelyHeld;
    if (isResumed) {
      boolean wasRemoteVideoOff =
          TextUtils.equals(
              remoteVideoOff.getText(),
              remoteVideoOff.getResources().getString(R.string.videocall_remote_video_off));
      // The text needs to be updated and hidden after enough delay in order to be announced by
      // talkback.
      remoteVideoOff.setText(
          wasRemoteVideoOff
              ? R.string.videocall_remote_video_on
              : R.string.videocall_remotely_resumed);
      remoteVideoOff.postDelayed(
          new Runnable() {
            @Override
            public void run() {
              remoteVideoOff.setVisibility(View.GONE);
            }
          },
          VIDEO_OFF_VIEW_FADE_OUT_DELAY_IN_MILLIS);
    } else {
      remoteVideoOff.setText(
          isRemotelyHeld ? R.string.videocall_remotely_held : R.string.videocall_remote_video_off);
      //modify for bug 1130479
      if(videoCallScreenDelegate.getLocalVideoSurfaceTexture().getSmallSurface()) {
        remoteVideoOff.setVisibility(View.VISIBLE);
      } else {
        remoteVideoOff.setVisibility(View.GONE);
      }
    }
    updateBlurredImageView(
        remoteTextureView,
        remoteOffBlurredImageView,
        shouldShowRemote,
        BLUR_REMOTE_RADIUS,
        BLUR_REMOTE_SCALE_FACTOR);
  }

  @VisibleForTesting
  void updateBlurredImageView(
      TextureView textureView,
      ImageView blurredImageView,
      boolean isVideoEnabled,
      float blurRadius,
      float scaleFactor) {
    Context context = getContext();

    if (isVideoEnabled || context == null) {
      blurredImageView.setImageBitmap(null);
      blurredImageView.setVisibility(View.GONE);
      return;
    }

    // UNISOC: add for bug1209744
    if (textureView == previewTextureView
            && context.getResources().getBoolean(R.bool.config_hideConferencePreviewView)) {
      LogUtil.i("VideoCallFragment.updateBlurredImageView", "hide the blurred image for reliance");
      blurredImageView.setVisibility(View.GONE);
      return;
    }

    long startTimeMillis = SystemClock.elapsedRealtime();
    int width = Math.round(textureView.getWidth() * scaleFactor);
    int height = Math.round(textureView.getHeight() * scaleFactor);

    LogUtil.i("VideoCallFragment.updateBlurredImageView", "width: %d, height: %d", width, height);

    // This call takes less than 10 milliseconds.
    Bitmap bitmap = textureView.getBitmap(width, height);

    /* UNISOC:add for bug 1154772(960205 965780) @{ */
    if (textureView == previewTextureView  && bitmap != null) {
      VideoSurfaceTexture localVideoSurfaceTexture =
              videoCallScreenDelegate.getLocalVideoSurfaceTexture();
      Point cameraDimensions = localVideoSurfaceTexture.getSurfaceDimensions();
      if (cameraDimensions != null && !isLandscape()) {
        if (height * cameraDimensions.x > width * cameraDimensions.y) {
          height = (int) (width * cameraDimensions.y / cameraDimensions.x);
        } else if (height * cameraDimensions.x < width * cameraDimensions.y) {
          width = (int) (height * cameraDimensions.x / cameraDimensions.y);
        }
        bitmap = textureView.getBitmap(width, height);
        // UNISOC: modify for bug1211397
        if (InCallUiUtils.isSupportRingTone(getContext(), getCall())) {
          blurredImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
          blurredImageView.setScaleType(ImageView.ScaleType.CENTER);
        }
      }
    }
    /*@}*/

    if (bitmap == null) {
      // UNISOC:modify for bug 1154772
      // blurredImageView.setImageBitmap(null);
      blurredImageView.setVisibility(View.VISIBLE);
      return;
    }

    // TODO(mdooley): When the view is first displayed after a rotation the bitmap is empty
    // and thus this blur has no effect.
    // This call can take 100 milliseconds.
    blur(getContext(), bitmap, blurRadius);

    // TODO(mdooley): Figure out why only have to apply the transform in landscape mode
    if (width > height) {
      bitmap =
          Bitmap.createBitmap(
              bitmap,
              0,
              0,
              bitmap.getWidth(),
              bitmap.getHeight(),
              textureView.getTransform(null),
              true);
    }

    blurredImageView.setImageBitmap(bitmap);
    blurredImageView.setVisibility(View.VISIBLE);

    LogUtil.i(
        "VideoCallFragment.updateBlurredImageView",
        "took %d millis",
        (SystemClock.elapsedRealtime() - startTimeMillis));
  }

  private void updateOverlayBackground() {
    if (isInGreenScreenMode) {
      // We want to darken the preview view to make text and buttons readable. The fullscreen
      // background is below the preview view so use the green screen background instead.
      animateSetVisibility(greenScreenBackgroundView, View.VISIBLE);
      animateSetVisibility(fullscreenBackgroundView, View.GONE);
    } else if (!isInFullscreenMode) {
      // We want to darken the remote view to make text and buttons readable. The green screen
      // background is above the preview view so it would darken the preview too. Use the fullscreen
      // background instead.
      animateSetVisibility(greenScreenBackgroundView, View.GONE);
      animateSetVisibility(fullscreenBackgroundView, View.VISIBLE);
    } else {
      animateSetVisibility(greenScreenBackgroundView, View.GONE);
      animateSetVisibility(fullscreenBackgroundView, View.GONE);
    }
  }

  private void updateMutePreviewOverlayVisibility() {
    // Normally the mute overlay shows on the bottom right of the preview bubble. In green screen
    // mode the preview is fullscreen so there's no where to anchor it.
    mutePreviewOverlay.setVisibility(
        muteButton.isChecked() && !isInGreenScreenMode ? View.VISIBLE : View.GONE);
  }

  private static void animateSetVisibility(final View view, final int visibility) {
    if (view.getVisibility() == visibility) {
      return;
    }

    int startAlpha;
    int endAlpha;
    if (visibility == View.GONE) {
      startAlpha = 1;
      endAlpha = 0;
    } else if (visibility == View.VISIBLE) {
      startAlpha = 0;
      endAlpha = 1;
    } else {
      Assert.fail();
      return;
    }

    view.setAlpha(startAlpha);
    view.setVisibility(View.VISIBLE);
    view.animate()
        .alpha(endAlpha)
        .withEndAction(
            new Runnable() {
              @Override
              public void run() {
                view.setVisibility(visibility);
              }
            })
        .start();
  }

  private static void blur(Context context, Bitmap image, float blurRadius) {
    RenderScript renderScript = RenderScript.create(context);
    ScriptIntrinsicBlur blurScript =
        ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
    Allocation allocationIn = Allocation.createFromBitmap(renderScript, image);
    Allocation allocationOut = Allocation.createFromBitmap(renderScript, image);
    blurScript.setRadius(blurRadius);
    blurScript.setInput(allocationIn);
    blurScript.forEach(allocationOut);
    allocationOut.copyTo(image);
    blurScript.destroy();
    allocationIn.destroy();
    allocationOut.destroy();
  }

  @Override
  public void onSystemUiVisibilityChange(int visibility) {
    boolean navBarVisible = (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
    videoCallScreenDelegate.onSystemUiVisibilityChange(navBarVisible);
  }

  private void checkCameraPermission() {
    // Checks if user has consent of camera permission and the permission is granted.
    // If camera permission is revoked, shows system permission dialog.
    // If camera permission is granted but user doesn't have consent of camera permission
    // (which means it's first time making video call), shows custom dialog instead. This
    // will only be shown to user once.
    if (!VideoUtils.hasCameraPermissionAndShownPrivacyToast(getContext())) {
      videoCallScreenDelegate.onCameraPermissionDialogShown();
      if (!VideoUtils.hasCameraPermission(getContext())) {
        requestPermissions(new String[] {permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
      } else {
        PermissionsUtil.showCameraPermissionToast(getContext());
        videoCallScreenDelegate.onCameraPermissionGranted();
      }
    }
  }

  /* UNISOC Feature Porting: Add for call recorder feature.for bug1143842 @{  */
  @Override
  public void setRecord(boolean value) {
    LogUtil.i("VideoCallFragment.setRecord", "value: " + value);
    isRecording = value;
    if(mOverflowPopup == null){//UNISOC:add for bug1143842
      LogUtil.e(
              "VideoCallFragment", "setRecord mOverflowPopup is null return");
      return;
    }
    MenuItem menuItem =  mOverflowPopup.getMenu().findItem(R.id.call_record_menu);
    if(!value){
      menuItem.setTitle(R.string.record_menu_title);
    }else{
      menuItem.setTitle(R.string.record_menu_title_recording);
    }
  }

  @Override
  public void setRecordTime(String recordTime) {
    LogUtil.i("VideoCallFragment.setRecordTime", "recordTime: " + recordTime);
    if(mOverflowPopup == null){//UNISOC:add for bug1143842
      LogUtil.e(
              "VideoCallFragment", "setRecordTime mOverflowPopup is null return");
      return;
    }
    MenuItem menuItem =  mOverflowPopup.getMenu().findItem(R.id.call_record_menu);
    if(menuItem!=null && isRecording){
      String timeStr = mOverflowButton.getResources().getString(R.string.record_menu_title_recording)+" "+recordTime;
      menuItem.setTitle(timeStr);
    }
  }
  /* @} */

  /* UNISOC: Added for video call conference @{ */
  @Override
  public void showPreviewVideoViews(boolean showPreview) {
    if (previewTextureView != null) {
      previewTextureView.setVisibility(showPreview ? View.VISIBLE : View.INVISIBLE);
    }else{
      LogUtil.i("VideoCallFragment.showPreviewVideoViews", "previewView == null ");
    }
  }
  /* @} */

  /* UNISOC: Add video call option menu@{ */
  protected void selectMenuItem(MenuItem item) {
      switch (item.getItemId()) {
          case R.id.add_call_menu:
              inCallButtonUiDelegate.addCallClicked();
              break;
          case R.id.merge_menu:
              inCallButtonUiDelegate.mergeClicked();
              break;
          case R.id.hold_call_menu:
              inCallButtonUiDelegate.holdClicked(!item.isChecked());
              break;
          case R.id.swap_call_menu:
              inCallButtonUiDelegate.swapClicked();
              break;
          case R.id.changeto_audio_menu:
              inCallButtonUiDelegate.changeToVoiceClicked();
              break;
          //UNISOC:add for bug1143842
          case R.id.call_record_menu:
              inCallButtonUiDelegate.recordClick(!isRecording);
              break;
          // UNISOC: add for bug1142881
          case R.id.manager_conference_menu:
              inCallScreenDelegate.onManageConferenceClicked();
              break;
          // UNISOC: add for bug1152075
          case R.id.dialpad_menu:
              inCallButtonUiDelegate.showDialpadClicked(!item.isChecked());
              break;
      }
  }
  /*@}*/
  //add for bug 1130479

  public void localSurfaceClickForChange() {
    videoCallScreenDelegate.getLocalVideoSurfaceTexture().changeToBigSurface();
    videoCallScreenDelegate.getRemoteVideoSurfaceTexture().changeToSmallSurface();
    updateMuteLocation();
    updateMutePreviewOverlayVisibility();
  }

  public void remoteSurfaceClickForChange() {
    videoCallScreenDelegate.getRemoteVideoSurfaceTexture().changeToBigSurface();
    videoCallScreenDelegate.getLocalVideoSurfaceTexture().changeToSmallSurface();
    updateMuteLocation();
    updateMutePreviewOverlayVisibility();
  }

  public void changeSmallSizeAndPosition(VideoSurfaceTextureImpl videoCallSurface){
    Resources resources = getResources();
    RelativeLayout.LayoutParams params =
            new RelativeLayout.LayoutParams(
                    (int) resources.getDimension(R.dimen.videocall_preview_width),
                    (int) resources.getDimension(R.dimen.videocall_preview_height));
    params.setMargins(
            0, 0, 0, (int) resources.getDimension(R.dimen.videocall_preview_margin_bottom));
    if (isLandscape()) {
      params.addRule(RelativeLayout.ALIGN_PARENT_END);
      params.setMarginEnd((int) resources.getDimension(R.dimen.videocall_preview_margin_end));
    } else {
      params.addRule(RelativeLayout.ALIGN_PARENT_START);
      params.setMarginStart((int) resources.getDimension(R.dimen.videocall_preview_margin_start));
    }
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
    videoCallSurface.getTextureView().setLayoutParams(params);
    videoCallSurface.getTextureView().setOutlineProvider(circleOutlineProvider);
    videoCallSurface.getTextureView().setClipToOutline(true);
    bringToIndex(videoCallSurface.getTextureView(),smallTextureViewIndex);
    updateOverlayBackground();
    contactGridManager.setIsMiddleRowVisible(false);

    videoCallSurface.getImageView().setLayoutParams(params);
    videoCallSurface.getImageView().setOutlineProvider(circleOutlineProvider);
    videoCallSurface.getImageView().setClipToOutline(true);
    bringToIndex(videoCallSurface.getImageView(),smallBlurredImageIndex);

    videoCallSurface.setSmallSurface(true);
  }

  public void changeBigSizeAndPosition(VideoSurfaceTextureImpl videoCallSurface) {
    RelativeLayout.LayoutParams params =
            new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
    params.addRule(RelativeLayout.ALIGN_PARENT_START);
    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
    videoCallSurface.getTextureView().setLayoutParams(params);
    videoCallSurface.getTextureView().setOutlineProvider(null);
    updateOverlayBackground();
    contactGridManager.setIsMiddleRowVisible(true);
    bringToIndex(videoCallSurface.getTextureView(),bigTextureViewIndex);

    videoCallSurface.getImageView().setLayoutParams(params);
    videoCallSurface.getImageView().setOutlineProvider(null);
    videoCallSurface.getImageView().setClipToOutline(false);
    bringToIndex(videoCallSurface.getImageView(),bigBlurredImageIndex);

    videoCallSurface.setSmallSurface(false);
  }

  private void bringToIndex(View child, int index) {
    ViewGroup mParent = (ViewGroup) child.getParent();
    if (index >= 0) {
      mParent.removeView(child);
      mParent.addView(child, index);
      mParent.requestLayout();
      mParent.invalidate();
    }
  }

  private void updateMuteLocation() {
    Resources resources = getResources();
    RelativeLayout.LayoutParams params =
            new RelativeLayout.LayoutParams(
                    (int) resources.getDimension(R.dimen.videocall__mute_image_size),
                    (int) resources.getDimension(R.dimen.videocall__mute_image_size));
    if(getSmallTextureView() != null){//add for bug1158394
      params.addRule(RelativeLayout.ALIGN_BOTTOM,getSmallTextureView().getId());
      params.addRule(RelativeLayout.ALIGN_RIGHT,getSmallTextureView().getId());
      mutePreviewOverlay.setLayoutParams(params);
    }else{
      LogUtil.i("VideoCallFragment.updateMuteLocation", "getSmallTextureView() == null ");
    }

  }

  private TextureView getSmallTextureView() {
    if(videoCallScreenDelegate.getLocalVideoSurfaceTexture().getSmallSurface()) {
      return previewTextureView != null ? previewTextureView: null;
    } else {
      return remoteTextureView != null ? remoteTextureView: null;
    }
  }
  // UNISOC: modify for bug1155897
  private DialerCall getCall() {
    return CallList.getInstance().getCallById(getCallId());
  }

  /* unisoc: add for bug1166982(bug904816)@{ */
  @Override
  public void onVideoCallIsFront() {
    LogUtil.i("VideoCallFragment.onVideoCallIsFront", null);
    if (videoCallScreenDelegate != null) {
      videoCallScreenDelegate.onVideoCallIsFront();
    }
  }
  @Override
  public void onVideoCallIsBack() {
    LogUtil.i("VideoCallFragment.onVideoCallIsBack", null);
    if (videoCallScreenDelegate != null) {
      videoCallScreenDelegate.onVideoCallIsBack();
    }
  }
  /* @} */
}

