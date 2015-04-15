package jp.gr.java_conf.t1h.ldruck;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RatingBar;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class SubListActivity extends ListActivity
        implements SubListActivityHelper.SubListable, LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "SubListActivity";

    private final Handler handler = new Handler();
    private SubsAdapter subsAdapter;
    private ReaderService readerService;
    private int lastPosition;

    private Menu mOptionsMenu;
    private Object mSyncObserverHandle;
    private Boolean mRefreshStatus = false;


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void setRefreshActionButtonState(boolean refreshing) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            setProgressBarIndeterminateVisibility(refreshing);
            return;
        }

        if (mOptionsMenu == null) {
            return;
        }

        final MenuItem refreshItem = mOptionsMenu.findItem(R.id.menu_item_reload);
        if (refreshItem != null) {
            if (refreshing) {
                refreshItem.setActionView(R.layout.actionbar_indeterminate_progress);
            } else {
                refreshItem.setActionView(null);
            }
        }
    }

    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ReaderService.ReaderBinder binder = (ReaderService.ReaderBinder) service;
            SubListActivity.this.readerService = binder.getService();
        }
        @Override
        public void onServiceDisconnected(ComponentName className) {
            SubListActivity.this.readerService = null;
        }
    };

    private BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(ReaderService.ACTION_SYNC_FINISHED)){
                SubListActivity.this.setRefreshActionButtonState(false);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.sub_list);

        bindService(new Intent(this, ReaderService.class),
            this.serviceConn, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ReaderService.ACTION_SYNC_SUBS_FINISHED);
        filter.addAction(ReaderService.ACTION_UNREAD_MODIFIED);
        filter.addAction(ReaderService.ACTION_SYNC_FINISHED);
        registerReceiver(this.refreshReceiver, filter);

        ActivityHelper.bindTitle(this);

        subsAdapter = new SubsAdapter(this, null);

        getListView().setAdapter(subsAdapter);
        getLoaderManager().initLoader(0, null, this);

    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(this.serviceConn);
        unregisterReceiver(this.refreshReceiver);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        return SubListActivityHelper.onCreateDialog(this, id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        return SubListActivityHelper.onCreateOptionsMenu(this, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.menu_item_reload:
//                item.setEnabled(false);
                setRefreshActionButtonState(true);

                break;
        }
        boolean result = SubListActivityHelper.onOptionsItemSelected(this, item);

        return result;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        SubListActivityHelper.onActivityResult(this, requestCode, resultCode, data);
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public ReaderService getReaderService() {
        return this.readerService;
    }

    @Override
    public Handler getHandler() {
        return this.handler;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        Cursor c = (Cursor) getListView().getItemAtPosition(position);

        Long subId = c.getLong(c.getColumnIndex(Subscription._ID));//v.getTag();
        if (subId != null) {
            this.lastPosition = position;
            SubListActivityHelper.startItemActivities(this, subId);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            // NOTE: ignore search
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public synchronized void initListAdapter() {
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Log.d(getClass().getSimpleName(), "onCreateLoader called.");

        this.lastPosition = 0;
        Context context = getApplicationContext();
        StringBuilder where = new StringBuilder(64);
        where.append(Subscription._DISABLED).append(" = 0");
        if (ReaderPreferences.isViewUnreadOnly(context)) {
            where.append(" and ");
            where.append(Subscription._UNREAD_COUNT).append(" > 0");
        }
        int subsSort = ReaderPreferences.getSubsSort(context);
        if (subsSort < 1 || subsSort > Subscription.SORT_ORDERS.length) {
            subsSort = 1;
        }
        String orderby = Subscription.SORT_ORDERS[subsSort - 1];

        return new CursorLoader(this, Subscription.CONTENT_URI, null, where.toString(), null, orderby);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Log.d(getClass().getSimpleName(), "onLoadFinished called.");
        subsAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        Log.d(getClass().getSimpleName(), "onLoaderReset called.");
        subsAdapter.swapCursor(null);
    }

    private class SubsAdapter extends ResourceCursorAdapter {
        private final class ViewHolder {
            public TextView titleView;
            public ImageView iconView;
            public RatingBar ratingBar;
            public TextView etcView;
        }

        public SubsAdapter(Context context, Cursor cursor) {
            super(context, R.layout.sub_list_row, null, false);
        }


        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context.
                    getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View v = inflater.inflate(R.layout.sub_list_row, null, true);

            ViewHolder holder = new ViewHolder();
            holder.iconView = (ImageView) v.findViewById(R.id.icon);
            holder.titleView = (TextView) v.findViewById(R.id.title);
            holder.ratingBar = (RatingBar) v.findViewById(R.id.rating_bar);
            holder.etcView = (TextView) v.findViewById(R.id.etc);

            v.setTag(holder);

            return v;
        }
        @Override
        public void bindView(View view, Context context, Cursor cursor) {

            Subscription sub = (new Subscription.FilterCursor(cursor)).getSubscription();

            ViewHolder holder = (ViewHolder) view.getTag();
            holder.titleView.setText(sub.getTitle() + " (" + sub.getUnreadCount() + ")");
            Bitmap icon = sub.getIcon(context);
            if (icon == null) {
                holder.iconView.setImageResource(R.drawable.item_read);
            } else {
                holder.iconView.setImageBitmap(icon);
            }
            holder.ratingBar.setRating(sub.getRate());

            StringBuilder buff = new StringBuilder(64);
            buff.append(sub.getSubscribersCount());
            buff.append(" users");
            String folder = sub.getFolder();
            if (folder != null && folder.length() > 0) {
                buff.append(" | ");
                buff.append(folder);
            }
            holder.etcView.setText(new String(buff));

        }
    }
}
