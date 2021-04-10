package com.android.dialer.sprd.calllog;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.app.ActionBar;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteException;
import android.Manifest.permission;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.CallLog.Calls;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.format.DateUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.dialer.app.calllog.CallLogFilterFragment;
import com.android.dialer.app.calllog.calllogcache.CallLogCache;
import com.android.dialer.app.sprd.telcel.DialerTelcelHelper;
import com.android.dialer.calllogutils.CallTypeIconsView;
import com.android.dialer.calllogutils.PhoneAccountUtils;
import com.android.dialer.database.CallLogQueryHandler;
import com.android.dialer.R;
import com.android.dialer.util.PermissionsUtil;

import com.google.common.collect.Lists;

import static android.Manifest.permission.READ_PHONE_STATE;

import com.android.dialer.phonenumberutil.PhoneNumberHelper;


//add by unisoc
public class CallLogClearActivity extends ListActivity implements CallLogClearColumn {
    private static final String TAG = "CallLogClearActivity";
    // UNISOC: add for bug544185
    private static final String KEY_CHECKED_ITEM_ID = "key_checked_item_id";
    // UNISOC: add for bug563640
    private static final String KEY_STAND_BY_DIALOG_SHOWING = "stand_by_dialog_is_showing";
    private boolean mSelected;

    private static final Executor sDefaultExecute = Executors.newCachedThreadPool();
    private static final String[] EMPTY_ARRAY = new String[0];
    private static final Uri CONTENT_URI = Uri.parse("content://call_log/calls");

    protected static final int MENU_OK = Menu.FIRST + 1;
    protected static final int MENU_CANCLE = Menu.FIRST;

    //filter call log by call log type (outgoing? incoming? missed?)
    private int mCallType = CallLogQueryHandler.CALL_TYPE_ALL;
    //filter call log by phoneId
    private int mShowType = CallLogFilterFragment.TYPE_ALL;
    private HashMap<Long, Long> mSelectId;
    private CallLogClearAdapter mAdapter;
    private static final int CALL_PHONE_PERMISSION_REQUEST_CODE = 1;
    // UNISOC: add for Bug 617600
    protected final CallLogCache mCallLogCache = new CallLogCache(this);

    CallLogClearDialog mClearDialog = new CallLogClearDialog();
    Dialog mDialog = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.call_log_clear_activity_ex);

        final ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        /* unisoc: modify for bug1042402 @{ */
        actionBar.setHomeAsUpIndicator(R.drawable.quantum_ic_arrow_back_white_24);
        /* @} */

        mCallType = getIntent().getIntExtra(SourceUtils.CALL_LOG_TYPE_EXTRA,
                CallLogQueryHandler.CALL_TYPE_ALL);
        mShowType = CallLogFilterFragment.getCallLogShowType(this);
        mAdapter = new CallLogClearAdapter(this, R.layout.clear_call_log_list_item_ex, null);
        /* UNISOC: modify for bug544185 @{ */
        if (savedInstanceState != null) {
            mSelectId =
                    (HashMap<Long, Long>) savedInstanceState.getSerializable(KEY_CHECKED_ITEM_ID);
        } else {
            mSelectId = new HashMap<Long, Long>();
        }
        /* @} */
        setListAdapter(mAdapter);
        getListView().setItemsCanFocus(true);
        /* UNISOC:add for bug512213 @{*/
        if (checkSelfPermission(permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission.CALL_PHONE},
                    CALL_PHONE_PERMISSION_REQUEST_CODE);
        } else {
            AsyncQueryThread thread = new AsyncQueryThread(
                    getApplicationContext(), mShowType, mCallType);
            thread.executeOnExecutor(sDefaultExecute);
        }
        /* @} */

        /* UNISOC: add for bug563640 @{ */
        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(KEY_STAND_BY_DIALOG_SHOWING)) {
                Runnable runSelect = getClearSelectCallLog(getApplicationContext());
                mDialog = mClearDialog.show(this, runSelect, false);
                mSelected = false;
            }
        }
        /* @} */
        mQueryHandler = new QueryHandler();
        getContentResolver().registerContentObserver(CONTENT_URI, true, mCallLogObserver);
    }

    /**
     * UNISOC: modify for bug512213 @{
     */
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case CALL_PHONE_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    AsyncQueryThread thread = new AsyncQueryThread(
                            getApplicationContext(), mShowType, mCallType);
                    thread.executeOnExecutor(sDefaultExecute);
                } else {
                    finish();
                }
                break;
            default:
                break;
        }
    }

    /**
     * @}
     */

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
            mAdapter = null;
        }
        if (mClearDialog != null) {
            mClearDialog = null;
        }
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        getContentResolver().unregisterContentObserver(mCallLogObserver);
    }

    private QueryHandler mQueryHandler = null;
    private final int DISMISS = 1;

    ContentObserver mCallLogObserver = new ContentObserver(mQueryHandler) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mQueryHandler.removeMessages(DISMISS);
            mQueryHandler.sendEmptyMessage(DISMISS);
        }
    };

    private class QueryHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DISMISS:
                    if (mDialog != null) {
                        mDialog.dismiss();
                    }
                    break;
            }
            super.handleMessage(msg);
        }
    }

    ;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.call_log_clear_options_ex, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem delete_select = menu.findItem(R.id.delete_selected);
        final MenuItem selectAll = menu.findItem(R.id.select_all);
        final MenuItem unselectAll = menu.findItem(R.id.unselect_all);
        int listCount = getListAdapter() == null ? 0 : getListAdapter().getCount();
        int selectCount = mSelectId.size();

        Log.d(TAG, "listCount = " + listCount + ", selectCount = " + selectCount);

        delete_select.setVisible(selectCount > 0 && listCount > 0);
        selectAll.setVisible(selectCount < listCount && listCount > 0);
        unselectAll.setVisible(selectCount == listCount && listCount > 0);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case MENU_OK:
                int listCount = getListAdapter() == null ? 0 : getListAdapter().getCount();
                int selectCount = mSelectId.size();
                if (selectCount == listCount && listCount > 0) {
                    Runnable runAll = getClearAllRunnable(getApplicationContext());
                    mDialog = mClearDialog.show(this, runAll, true);
                } else {
                    Runnable runSelect = getClearSelectCallLog(getApplicationContext());
                    mDialog = mClearDialog.show(this, runSelect, false);
                }
                break;
            case MENU_CANCLE:
                finish();
                break;
            case R.id.delete_selected:
                Runnable runSelect = getClearSelectCallLog(getApplicationContext());
                // UNISOC:add for bug563640
                mSelected = true;
                mDialog = mClearDialog.show(this, runSelect, false);
                return true;
            case R.id.select_all:
                AsyncThread selectThread = new AsyncThread();
                selectThread.execute(true);
                return true;
            case R.id.unselect_all:
                AsyncThread unSelectThread = new AsyncThread();
                unSelectThread.execute(false);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        CheckBox box = (CheckBox) v.findViewById(R.id.call_icon);
        box.setChecked(!box.isChecked());
        boolean checked = box.isChecked();
        Log.d(TAG, "position = " + position + ", id = " + id + ", checked = " + checked);
        if (checked) {
            mSelectId.put(id, id);
        } else {
            mSelectId.remove(id);
        }
    }

    private final class CallLogClearAdapter extends ResourceCursorAdapter {
        public CallLogClearAdapter(Context context, int layout, Cursor cursor) {
            super(context, layout, cursor, FLAG_REGISTER_CONTENT_OBSERVER);
        }

        @Override
        public void bindView(View view, Context context, Cursor c) {
            CallTypeIconsView iconView = (CallTypeIconsView) view.findViewById(R.id.call_type_icon);
            TextView line1View = (TextView) view.findViewById(R.id.line1);
            TextView numberView = (TextView) view.findViewById(R.id.number);
            TextView dateView = (TextView) view.findViewById(R.id.date);
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.call_icon);
            checkBox.setFocusable(false);
            checkBox.setClickable(false);

            final long id = c.getLong(ID_COLUMN_INDEX);
            long date = c.getLong(DATE_COLUMN_INDEX);

            String number = c.getString(NUMBER_COLUMN_INDEX);
            String formattedNumber = number;
            String name = c.getString(CALLER_NAME_COLUMN_INDEX);
            /**UNISOC: 1180855 add for distinguishing between private and unknown @{*/
            final int numberPresentation = c.getInt(NUMBER_PRESENTATION);
            /**@} */
            /** UNISOC: add for Bug 965628 show fdn name when fdn contact dialed@{ */
            name = DialerTelcelHelper.getInstance()
                    .queryFdnCacheForAllSubs(context, number, name);
            /** @{ */

            /* UNISOC: add for Bug 617600 @{ */
            final PhoneAccountHandle accountHandle = PhoneAccountUtils.getAccount(
                    c.getString(CallLogClearColumn.ACCOUNT_COMPONENT_NAME),
                    c.getString(CallLogClearColumn.ACCOUNT_ID));
            final boolean isVoicemailNumber =
                    mCallLogCache.isVoicemailNumber(accountHandle, number);
            /**UNISOC: add for bug1100219 set text direction for diplayName and number @{*/
            line1View.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            numberView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            numberView.setTextDirection(View.TEXT_DIRECTION_LTR);
            if (isVoicemailNumber) {
                line1View.setText(R.string.voicemail);
                numberView.setText(formattedNumber);
            } else {
                if (!TextUtils.isEmpty(name)) {
                    if (ContactDisplayUtils.isPossiblePhoneNumber(name)) {
                        line1View.setTextDirection(View.TEXT_DIRECTION_LTR);
                    }
                    line1View.setText(name);
                    numberView.setText(formattedNumber);
                } else {
                    if (!TextUtils.isEmpty(number)) {
                        line1View.setTextDirection(View.TEXT_DIRECTION_LTR);
                        line1View.setText(number);
                    } else {
                        /**UNISOC: 1180855 add for distinguishing between private and unknown @{*/
                        if (numberPresentation == Calls.PRESENTATION_RESTRICTED) {
                            line1View.setText(PhoneNumberHelper.getDisplayNameForRestrictedNumber(context));
                        } else if (numberPresentation == Calls.PRESENTATION_PAYPHONE) {
                            line1View.setText(R.string.payphone);
                        } else {
                            line1View.setText(R.string.unknown);
                        }
                        /**@} */
                    }
                    numberView.setText("");
                }
            }
            /**@}*/
            /* @} */

            if (iconView != null) {
                int type = c.getInt(CALL_TYPE_COLUMN_INDEX);
                // UNISOC: modify for bug709724 bug892117
                iconView.clear();
                iconView.add(type);
            }

            CharSequence dateText;
            if (date <= System.currentTimeMillis()) {
                dateText =
                        DateUtils.getRelativeTimeSpanString(date,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_NUMERIC_DATE);
            } else {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                dateText = dateFormat.format(date);
            }

            dateView.setText(dateText);

            if (mSelectId.containsKey(id)) {
                checkBox.setChecked(true);
            } else {
                checkBox.setChecked(false);
            }
        }

        @Override
        protected void onContentChanged() {
            super.onContentChanged();
            AsyncQueryThread thread = new AsyncQueryThread(
                    getApplicationContext(), mShowType, mCallType);
            thread.executeOnExecutor(sDefaultExecute);
        }
    }

    private class AsyncQueryThread extends AsyncTask<Void, Void, Cursor> {
        private int aCallType = CallLogQueryHandler.CALL_TYPE_ALL;
        private Context aContext;
        private int aShowType = CallLogFilterFragment.TYPE_ALL;

        public AsyncQueryThread(Context context, int showType) {
            this(context, showType, CallLogQueryHandler.CALL_TYPE_ALL);
        }

        public AsyncQueryThread(Context context, int showType, int callType) {
            aContext = context;
            aShowType = showType;
            aCallType = callType;
        }

        @Override
        protected Cursor doInBackground(Void... params) {
            StringBuffer where = new StringBuffer();
            List<String> args = Lists.newArrayList();
            // UNISOC: add for bug891031
            if (aShowType > CallLogFilterFragment.TYPE_ALL
                    && PermissionsUtil.hasPermission(aContext, READ_PHONE_STATE)) {
                // Translate slot ID to account ID
                final SubscriptionManager subscriptionManager = SubscriptionManager.from(aContext);
                SubscriptionInfo subscriptionInfo = subscriptionManager
                        .getActiveSubscriptionInfoForSimSlotIndex(mShowType);
                if (subscriptionInfo != null) {
                    String subscription_id = subscriptionInfo.getIccId();
                    where.append(String.format("(%s = ?)", Calls.PHONE_ACCOUNT_ID));
                    args.add(String.valueOf(subscription_id));
                }
            }
            if (aCallType > CallLogQueryHandler.CALL_TYPE_ALL) {
                if (where.length() > 0) {
                    where.append(" AND ");
                }
                where.append(String.format("(%s = ?)", Calls.TYPE));
                args.add(Integer.toString(aCallType));
            }
            /* UNISOC: add for bug588714 @{ */
            /*UserManager userManager = (UserManager) aContext.getSystemService(Context.USER_SERVICE);
            if (!userManager.isSystemUser()) {
                if (where.length() > 0) {
                    where.append(" AND ");
                }
                where.append(String.format("(%s = ?)", Calls.USERS_ID));
                args.add(Integer.toString(userManager.getUserHandle()));
            }*/
            //this block code may be need in feature,but not for this feature.so comment it.
            /* @} */
            ContentResolver cr = aContext.getContentResolver();
            final String selection = where.length() > 0 ? where.toString() : null;
            final String[] selectionArgs = args.toArray(EMPTY_ARRAY);
            //UNISOC: add for bug1165596, call log list order by date desc
            Cursor c = cr.query(Calls.CONTENT_URI, CALL_LOG_PROJECTION, selection, selectionArgs,
                    "date desc");
            return c;
        }

        @Override
        protected void onPostExecute(Cursor result) {
            if (mAdapter != null) {
                mAdapter.changeCursor(result);
                invalidateOptionsMenu();
                if (mAdapter.isEmpty()) {
                    finish();
                }
            } else {
                result.close();
            }
        }
    }

    ;

    private class AsyncThread extends AsyncTask<Boolean, Void, Void> {
        @Override
        protected Void doInBackground(Boolean... params) {
            if (mAdapter != null) {
                int count = mAdapter.getCount();
                for (int i = 0; i < count; i++) {
                    try {
                        if (mAdapter.getCursor() != null && !mAdapter.getCursor().isClosed()) {
                            long id = mAdapter.getItemId(i);
                            if (params[0] == true) {
                                mSelectId.put(id, id);
                            } else {
                                mSelectId.remove(id);
                            }
                        }
                    } catch (CursorIndexOutOfBoundsException ex) {
                        Log.e(TAG, "CursorIndexOutOfBoundsException " + ex);
                        break;
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    private Runnable getClearAllRunnable(final Context context) {
        Runnable run = new Runnable() {
            public void run() {
                ContentResolver cr = context.getContentResolver();
                StringBuffer where = new StringBuffer();
                List<String> args = Lists.newArrayList();
                if (mCallType > CallLogQueryHandler.CALL_TYPE_ALL) {
                    where.append(String.format("(%s = ?)", Calls.TYPE));
                    args.add(Integer.toString(mCallType));
                }
                String deleteWhere = where.length() > 0 ? where.toString() : null;
                String[] selectionArgs = deleteWhere == null ? null : args.toArray(EMPTY_ARRAY);
                cr.delete(Calls.CONTENT_URI, deleteWhere, selectionArgs);
                mSelectId.clear(); //should clear the map.
            }
        };
        return run;
    }

    private Runnable getClearSelectCallLog(final Context context) {
        Runnable run = new Runnable() {
            public void run() {
                ContentResolver cr = context.getContentResolver();
                StringBuffer where = new StringBuffer();
                where.append("calls._id in (");
                Set<Entry<Long, Long>> set = mSelectId.entrySet();
                Iterator<Entry<Long, Long>> iterator = set.iterator();
                boolean first = true;
                while (iterator.hasNext()) {
                    if (!first) {
                        where.append(",");
                    }
                    first = false;
                    Entry<Long, Long> entry = iterator.next();
                    long id = entry.getKey().longValue();
                    where.append(Long.toString(id));
                }
                where.append(")");
                /* UNISOC: modify for bug608081 @{ */
                try {
                    cr.delete(Calls.CONTENT_URI, where.toString(), null);
                } catch (SQLiteException ex) {
                    Log.e(TAG, "delete error : " + ex.getMessage());
                }
                /* @} */
                mSelectId.clear(); //should clear the map.
            }
        };
        return run;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        invalidateOptionsMenu();
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.call_log_delete_all);
        }
    }

    /* UNISOC: add for bug544185 @{ */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(KEY_CHECKED_ITEM_ID, mSelectId);
        /* UNISOC: add for bug563640 @{ */
        if (mClearDialog != null
                && mSelected
                && mDialog != null) {
            outState.putBoolean(KEY_STAND_BY_DIALOG_SHOWING, mDialog.isShowing());
        }
        /* @} */
    }
    /* @} */
}
