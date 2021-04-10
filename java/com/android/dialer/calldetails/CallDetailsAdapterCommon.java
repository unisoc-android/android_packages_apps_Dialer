/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.calldetails;

import android.content.Context;
import android.support.annotation.CallSuper;
import android.support.annotation.MainThread;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.calldetails.CallDetailsEntries.CallDetailsEntry;
import com.android.dialer.calldetails.CallDetailsEntryViewHolder.CallDetailsEntryListener;
import com.android.dialer.calldetails.CallDetailsFooterViewHolder.DeleteCallDetailsListener;
import com.android.dialer.calldetails.CallDetailsFooterViewHolder.ReportCallIdListener;
import com.android.dialer.calldetails.CallDetailsHeaderViewHolder.CallDetailsHeaderListener;
import com.android.dialer.calllogutils.CallTypeHelper;
import com.android.dialer.calllogutils.CallbackActionHelper;
import com.android.dialer.calllogutils.CallbackActionHelper.CallbackAction;
import com.android.dialer.common.Assert;
import com.android.dialer.duo.DuoComponent;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.android.dialer.calldetails.CallOperationsViewHolder;
import com.android.dialer.dialercontact.DialerContact;

/**
 * Contains common logic shared between {@link OldCallDetailsAdapter} and {@link
 * CallDetailsAdapter}.
 */
abstract class CallDetailsAdapterCommon extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private static final int HEADER_VIEW_TYPE = 1;
  private static final int OPERATIONS_VIEW_TYPE = 2;
  private static final int CALL_ENTRY_VIEW_TYPE = 3;
  private static final int FOOTER_VIEW_TYPE = 4;

  private final CallDetailsEntryListener callDetailsEntryListener;
  private final CallDetailsHeaderListener callDetailsHeaderListener;
  private final ReportCallIdListener reportCallIdListener;
  private final DeleteCallDetailsListener deleteCallDetailsListener;
  private final CallTypeHelper callTypeHelper;

  private CallDetailsEntries callDetailsEntries;

  protected abstract void bindCallDetailsHeaderViewHolder(
      CallDetailsHeaderViewHolder viewHolder, int position);

  protected abstract CallDetailsHeaderViewHolder createCallDetailsHeaderViewHolder(
      View container, CallDetailsHeaderListener callDetailsHeaderListener);

  /** Returns the phone number of the call details. */
  protected abstract String getNumber();

  /** Returns the primary text shown on call details toolbar, usually contact name or number. */
  protected abstract String getPrimaryText();

  /** Returns {@link PhotoInfo} of the contact. */
  protected abstract PhotoInfo getPhotoInfo();

  /** UNISOC:bug1072689 Add contact @{*/
  private DialerContact contact;
  /**@}*/

  CallDetailsAdapterCommon(
          Context context,
          CallDetailsEntries callDetailsEntries,
          CallDetailsEntryListener callDetailsEntryListener,
          CallDetailsHeaderListener callDetailsHeaderListener,
          ReportCallIdListener reportCallIdListener,
          DeleteCallDetailsListener deleteCallDetailsListener) {
    this.contact = contact;
    this.callDetailsEntries = callDetailsEntries;
    this.callDetailsEntryListener = callDetailsEntryListener;
    this.callDetailsHeaderListener = callDetailsHeaderListener;
    this.reportCallIdListener = reportCallIdListener;
    this.deleteCallDetailsListener = deleteCallDetailsListener;
    this.callTypeHelper =
            new CallTypeHelper(context.getResources(), DuoComponent.get(context).getDuo());
  }

  /** UNISOC:bug1072689 Add contact @{*/
  CallDetailsAdapterCommon(
      Context context,
      DialerContact contact,
      CallDetailsEntries callDetailsEntries,
      CallDetailsEntryListener callDetailsEntryListener,
      CallDetailsHeaderListener callDetailsHeaderListener,
      ReportCallIdListener reportCallIdListener,
      DeleteCallDetailsListener deleteCallDetailsListener) {
    this.contact = contact;
    this.callDetailsEntries = callDetailsEntries;
    this.callDetailsEntryListener = callDetailsEntryListener;
    this.callDetailsHeaderListener = callDetailsHeaderListener;
    this.reportCallIdListener = reportCallIdListener;
    this.deleteCallDetailsListener = deleteCallDetailsListener;
    this.callTypeHelper =
        new CallTypeHelper(context.getResources(), DuoComponent.get(context).getDuo());
  }
  /**@}*/

  @Override
  @CallSuper
  public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    switch (viewType) {
      case HEADER_VIEW_TYPE:
        return createCallDetailsHeaderViewHolder(
            inflater.inflate(R.layout.contact_container, parent, false), callDetailsHeaderListener);
      case OPERATIONS_VIEW_TYPE:
        return new CallOperationsViewHolder(
                inflater.inflate(R.layout.call_detail_action_ex, parent, false));
      case CALL_ENTRY_VIEW_TYPE:
        return new CallDetailsEntryViewHolder(
            inflater.inflate(R.layout.call_details_entry, parent, false), callDetailsEntryListener);
      case FOOTER_VIEW_TYPE:
        return new CallDetailsFooterViewHolder(
            inflater.inflate(R.layout.call_details_footer, parent, false),
            reportCallIdListener,
            deleteCallDetailsListener);
      default:
        throw Assert.createIllegalStateFailException(
            "No ViewHolder available for viewType: " + viewType);
    }
  }

  @Override
  @CallSuper
  public void onBindViewHolder(ViewHolder holder, int position) {
    if (position == 0) { // Header
      bindCallDetailsHeaderViewHolder((CallDetailsHeaderViewHolder) holder, position);
    } else if (position == 1) {
      ((CallOperationsViewHolder) holder).updateContactInfo(contact);
    }  else if (position == getItemCount() - 1) {
      ((CallDetailsFooterViewHolder) holder).setPhoneNumber(getNumber());
    } else {
      CallDetailsEntryViewHolder viewHolder = (CallDetailsEntryViewHolder) holder;
      /** UNISOC: AndroidQ feature porting for bug1072689 @{*/
      CallDetailsEntry entry = callDetailsEntries.getEntries(position - 2);
      /**@}*/
      viewHolder.setCallDetails(
          getNumber(),
          getPrimaryText(),
          getPhotoInfo(),
          entry,
          callTypeHelper,
          !entry.getHistoryResultsList().isEmpty() && position != getItemCount() - 2);
    }
  }

  @Override
  @CallSuper
  public int getItemViewType(int position) {
    if (position == 0) { // Header
      return HEADER_VIEW_TYPE;
    }else if (position == 1) {
      return OPERATIONS_VIEW_TYPE;
    } else if (position == getItemCount() - 1) {
      return FOOTER_VIEW_TYPE;
    } else {
      return CALL_ENTRY_VIEW_TYPE;
    }
  }

  @Override
  @CallSuper
  public int getItemCount() {
    return callDetailsEntries.getEntriesCount() == 0
        ? 0
        : callDetailsEntries.getEntriesCount() + 3; // UNISOC: AndroidQ feature porting for bug1072689
  }

  final CallDetailsEntries getCallDetailsEntries() {
    return callDetailsEntries;
  }

  @MainThread
  final void updateCallDetailsEntries(CallDetailsEntries entries) {
    Assert.isMainThread();
    callDetailsEntries = entries;
    notifyDataSetChanged();
  }

  final @CallbackAction int getCallbackAction() {
    Assert.checkState(!callDetailsEntries.getEntriesList().isEmpty());

    CallDetailsEntry entry = callDetailsEntries.getEntries(0);
    return CallbackActionHelper.getCallbackAction(
        getNumber(), entry.getFeatures(), entry.getIsDuoCall());
  }
}
