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
 * limitations under the License.
 */

package com.android.incallui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.drawable.ColorDrawable;
import android.content.res.TypedArray;
import android.content.res.Configuration;

/** Shows the {@link ConferenceManagerFragment} */
public class ManageConferenceActivity extends AppCompatActivity {

  private boolean isVisible;

  public boolean isVisible() {
    return isVisible;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    InCallPresenter.getInstance().setManageConferenceActivity(this);

    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    setContentView(R.layout.activity_manage_conference);
    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.manageConferencePanel);
    if (fragment == null) {
      fragment = new ConferenceManagerFragment();
      getSupportFragmentManager()
          .beginTransaction()
          .replace(R.id.manageConferencePanel, fragment)
          .commit();
    }
    int currentNightMode =  getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
      updateTheme(currentNightMode);
    }
  }

  private void updateTheme(int currentNightMode) {
    final int[] attrs = new int[] {
            android.R.attr.colorBackground,
            android.R.attr.textColorPrimary,
            android.R.attr.colorPrimary,
            android.R.attr.colorPrimaryDark,
            android.R.attr.colorAccent,
    };
    TypedArray array = getTheme().obtainStyledAttributes(attrs);
    if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
      getSupportActionBar().setBackgroundDrawable(new ColorDrawable(array.getColor(0, 0xFF00FF)));
    } else {
      getSupportActionBar().setBackgroundDrawable(new ColorDrawable(array.getColor(2, 0xFF00FF)));
    }


    Window window = getWindow();
    //After LOLLIPOP not translucent status bar
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
    //Then call setStatusBarColor.
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
      window.setStatusBarColor(array.getColor(0, 0xFF00FF));
    } else {
      window.setStatusBarColor(array.getColor(3, 0xFF00FF));
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (isFinishing()) {
      InCallPresenter.getInstance().setManageConferenceActivity(null);
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    final int itemId = item.getItemId();
    if (itemId == android.R.id.home) {
      onBackPressed();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onBackPressed() {
    InCallPresenter.getInstance().bringToForeground(false);
    finish();
  }

  @Override
  protected void onStart() {
    super.onStart();
    isVisible = true;
  }

  @Override
  protected void onStop() {
    super.onStop();
    isVisible = false;
  }
  //add for Bug1150940
  @Override
  public void finish() {
    super.finishAndRemoveTask();
  }
  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    int currentNightMode =  newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
    updateTheme(currentNightMode);
  }
}
