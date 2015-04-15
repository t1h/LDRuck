package jp.gr.java_conf.t1h.ldruck;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class ItemListActivity extends ListActivity
        implements ItemActivityHelper.Itemable, LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "ItemListActivity";
    private static final int DIALOG_MOVE = 3;
    private static final int REQUEST_ITEM_ID = 1;
    private static final int REQUEST_PREFERENCES = 1;

    private final Handler handler = new Handler();
    private Uri subUri;
    private Subscription sub;
    private long lastItemId;
    private ItemsAdapter itemsAdapter;
    private ReaderService readerService;
    private ReaderManager readerManager;
    private String keyword;
    private boolean unreadOnly;

    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ReaderService.ReaderBinder binder = (ReaderService.ReaderBinder) service;
            ItemListActivity.this.readerService = binder.getService();
            ItemListActivity.this.readerManager = binder.getManager();
            Subscription s = ItemListActivity.this.sub;
            ItemsAdapter a = ItemListActivity.this.itemsAdapter;
            if (s != null && s.getLastItemId() == 0
                    && a != null && a.getCount() == 0) {
                ItemActivityHelper.progressSyncItems(ItemListActivity.this, false);
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName className) {
            ItemListActivity.this.readerService = null;
            ItemListActivity.this.readerManager = null;
        }
    };

    private BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ItemListActivity.this.initListAdapter();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bindService(new Intent(this, ReaderService.class), this.serviceConn,
            Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ReaderService.ACTION_SYNC_SUBS_FINISHED);
        filter.addAction(ReaderService.ACTION_UNREAD_MODIFIED);
        registerReceiver(this.refreshReceiver, filter);

        setContentView(R.layout.item_list);
        ActivityHelper.bindTitle(this);

        Intent intent = getIntent();
        long subId = intent.getLongExtra(ActivityHelper.EXTRA_SUB_ID, 0);
        this.subUri = ContentUris.withAppendedId(Subscription.CONTENT_URI, subId);
        bindSubTitleView(true);
        ImageView iconView = (ImageView) findViewById(R.id.sub_icon);
        Bitmap icon = sub.getIcon(this);
        if (icon == null) {
            iconView.setImageResource(R.drawable.item_read);
        } else {
            iconView.setImageBitmap(icon);
        }

        final TextView keywordEdit = (TextView) findViewById(R.id.edit_keyword);
        keywordEdit.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_ENTER) {
                    handleSearch(v, keywordEdit);
                    return true;
                }
                return false;
            }
        });
        View search = findViewById(R.id.btn_search);
        search.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                handleSearch(v, keywordEdit);
            }
        });

        mAdapter = new ItemsAdapter(this, null);
        getListView().setAdapter(mAdapter);
        getLoaderManager().initLoader(0, null, this);

        ItemActivityHelper.progressTouchFeedLocal2(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        moveToItemId(this.lastItemId);
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

    private void bindSubTitleView(boolean reloadSub) {
        if (this.subUri == null) {
            return;
        }
        if (reloadSub) {
            ContentResolver cr = getContentResolver();
            Cursor cursor = cr.query(this.subUri, null, null, null, null);
            cursor.moveToFirst();
            this.sub = new Subscription.FilterCursor(cursor).getSubscription();
            cursor.close();
        }
        TextView subTitleView = (TextView) findViewById(R.id.sub_title);
        subTitleView.setText(this.sub.getTitle());
    }

//    @Override
//    protected Dialog onCreateDialog(int id) {
//        switch (id) {
//        case ItemActivityHelper.DIALOG_RELOAD:
//            return new AlertDialog.Builder(this)
//                .setTitle(R.string.dialog_items_reload_title)
//                .setSingleChoiceItems(R.array.dialog_items_reload_items, 0,
//                        new DialogInterface.OnClickListener() {
//                            public void onClick(DialogInterface dialog, int i) {
//                                ItemActivityHelper.progressSyncItems(ItemListActivity.this, i == 1);
//                                dialog.dismiss();
//                            }
//                        }
//                ).create();
//
//        case DIALOG_MOVE:
//            return new AlertDialog.Builder(this)
//                .setIcon(R.drawable.alert_dialog_icon)
//                .setTitle(R.string.dialog_items_move_title)
//                .setSingleChoiceItems(R.array.dialog_items_move, 0,
//                    new DialogInterface.OnClickListener() {
//                        public void onClick(DialogInterface dialog, int i) {
//                            switch (i) {
//                            case 0:
//                                moveToLastRead();
//                                break;
//                            case 1:
//                                moveToNewUnread();
//                                break;
//                            case 2:
//                                moveToOldUnread();
//                                break;
//                            }
//                            dialog.dismiss();
//                        }
//                    }
//                ).create();
//        case ItemActivityHelper.DIALOG_REMOVE:
//            return ItemActivityHelper.createDialogRemove(this);
//        }
//        return null;
//    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.item_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
        case R.id.menu_item_reload:
//            showDialog(ItemActivityHelper.DIALOG_RELOAD);

//            AlertDialog dialog1 = new AlertDialog.Builder(this)
//                    .setTitle(R.string.dialog_items_reload_title)
//                    .setSingleChoiceItems(R.array.dialog_items_reload_items, 0,
//                            new DialogInterface.OnClickListener() {
//                                public void onClick(DialogInterface dialog, int i) {
//                                    ItemActivityHelper.progressSyncItems(ItemListActivity.this, i == 1);
//                                    dialog.dismiss();
//                                }
//                            }
//                    ).create();
//            dialog1.show();
            ItemActivityHelper.progressSyncItems(ItemListActivity.this, false);

            return true;
        case R.id.menu_item_pin_list:
            startActivity(new Intent(this, PinActivity.class));
            return true;
//        case R.id.menu_item_touch_feed_local:
//            ItemActivityHelper.progressTouchFeedLocal(this);
//            return true;
//        case R.id.menu_item_move:
//            showDialog(DIALOG_MOVE);
//            return true;
//        case R.id.menu_unreads:
//            toggleUnreadOnly(menuItem);
//            return true;
//        case R.id.menu_search:
//            toggleSearchBar();
//            return true;
//        case R.id.menu_remove:
//            showDialog(ItemActivityHelper.DIALOG_REMOVE);
//            return true;
        case R.id.menu_unsubscribe:

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.msg_confirm_unsubscribe)
                    .setTitle(sub.getTitle())
                    .setPositiveButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            Intent intent = new Intent(ItemListActivity.this, ReaderIntentService.class);
                            intent.setAction(ReaderIntentService.ACTION_UNSUBSCRIBE);
                            intent.putExtra("subscriptionId", sub.getId());
                            startService(intent);

                            Intent backIntent = new Intent(ItemListActivity.this, SubListActivity.class);
                            backIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(backIntent);

                            try {
                                getContentResolver().applyBatch(ReaderProvider.AUTHORITY,
                                        processClearSubscription(sub.getId()));
                            } catch (Exception e) {
                                Log.d(TAG, "Error clearing data from Subscription", e);
                            }



                        }
                    })
                    .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // User cancelled the dialog
                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();

            return true;

        case R.id.menu_item_setting:
            startActivityForResult(new Intent(this, ReaderPreferenceActivity.class),
                REQUEST_PREFERENCES);
            return true;
        }
        return false;
    }

    private ArrayList<ContentProviderOperation> processClearSubscription(long subscriptionId) {

        ArrayList<ContentProviderOperation> batch = new ArrayList<ContentProviderOperation>();

        batch.add(ContentProviderOperation
                .newDelete(Subscription.CONTENT_URI)
                .withSelection(
                        Subscription._ID + " = ?",
                        new String[]{
                                Long.toString(subscriptionId)
                        })
                .build());
//        batch.add(ContentProviderOperation
//                .newDelete(Item.CONTENT_URI)
//                .withSelection(
//                        Item._SUBSCRIPTION_ID + " = ?",
//                        new String[]{
//                                Long.toString(subscriptionId)
//                        })
//                .build());


        return batch;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ITEM_ID && data != null) {
            this.lastItemId = data.getLongExtra(ActivityHelper.EXTRA_ITEM_ID, 0);
            bindSubTitleView(true);
            initListAdapter();
            moveToItemId(this.lastItemId);
        } else if (requestCode == REQUEST_PREFERENCES) {
//            if (ReaderPreferences.isOmitItemList(this)) {
//                finish();
//            }
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Cursor c = (Cursor) getListView().getItemAtPosition(position);
        Long itemId = c.getLong(c.getColumnIndex(Item._ID));

        if (itemId != null) {
            this.lastItemId = itemId;
            Intent intent = new Intent(this, ItemActivity.class)
                .putExtra(ActivityHelper.EXTRA_SUB_ID, this.sub.getId())
                .putExtra(ActivityHelper.EXTRA_ITEM_ID, itemId)
                .putExtra(ActivityHelper.EXTRA_WHERE, createBaseWhere());
            startActivityForResult(intent, REQUEST_ITEM_ID);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_SEARCH:
            toggleSearchBar();
            return true;
        case KeyEvent.KEYCODE_BACK:
            View searchBar = findViewById(R.id.search_bar);
            if (searchBar.getVisibility() == View.VISIBLE) {
                toggleSearchBar();
                return true;
            }
            break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void toggleUnreadOnly(MenuItem menuItem) {
        this.unreadOnly = !this.unreadOnly;
        initListAdapter();
        if (this.unreadOnly) {
            menuItem.setTitle("Show unread");
        } else {
            menuItem.setTitle("Hide unread");
        }
    }

    private void toggleSearchBar() {
        View searchBar = findViewById(R.id.search_bar);
        if (searchBar.getVisibility() == View.VISIBLE) {
            searchBar.setVisibility(View.GONE);
            if (this.keyword != null) {
                this.keyword = null;
                initListAdapter();
            }
        } else {
            searchBar.setVisibility(View.VISIBLE);
        }
    }

    private void handleSearch(View v, TextView keywordEdit) {
        CharSequence keywordChars = keywordEdit.getText();
        if (keywordChars != null) {
            this.keyword = keywordChars.toString();
            initListAdapter();
        }
        InputMethodManager imm = (InputMethodManager)
            getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromInputMethod(v.getWindowToken(), 0);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private ActivityHelper.Where createBaseWhere() {
        String keyword = this.keyword;
        String[] args = null;
        StringBuilder buff = new StringBuilder(
            (keyword == null) ? 64: 128 + keyword.length());
        buff.append(Item._SUBSCRIPTION_ID).append(" = ").append(this.sub.getId());
        if (keyword != null && keyword.length() > 0) {
            buff.append(" and (");
            buff.append(Item._TITLE).append(" like ? escape '\\'");
            buff.append(" or ");
            buff.append(Item._BODY).append(" like ? escape '\\'");
            buff.append(")");
            keyword = keyword.replaceAll("\\\\", "\\\\\\\\");
            keyword = keyword.replaceAll("%", "\\%");
            keyword = keyword.replaceAll("_", "\\_");
            keyword = "%" + keyword + "%";
            args = new String[]{keyword, keyword};
        }
        if (this.unreadOnly) {
            buff.append(" and ");
            buff.append(Item._UNREAD).append(" = 1");
        }
        return new ActivityHelper.Where(buff, args);
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public Handler getHandler() {
        return this.handler;
    }

    @Override
    public ReaderManager getReaderManager() {
        return this.readerManager;
    }

    @Override
    public long getSubId() {
        return this.sub.getId();
    }

    @Override
    public Uri getSubUri() {
        return this.subUri;
    }

    @Override
    public void initItems() {
        bindSubTitleView(true);
        initListAdapter();
    }

    private void initListAdapter() {
    }

    private void moveToItemId(long itemId) {
        if (itemId <= 0) {
            return;
        }
        ActivityHelper.Where where = createBaseWhere();
        where.buff.append(" and ");
        where.buff.append(Item._ID).append(" > ").append(itemId);
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(Item.CONTENT_URI, Item.SELECT_COUNT,
                new String(where.buff), where.args, null);
        cursor.moveToNext();
        int pos = cursor.getInt(0);
        cursor.close();
        getListView().setSelectionFromTop(pos, 48);
    }

    private ItemsAdapter mAdapter;
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Log.d(getClass().getSimpleName(), "onCreateLoader called.");

        ActivityHelper.Where where = createBaseWhere();
        String orderby = Item._ID + " desc";

        return new CursorLoader(this, Item.CONTENT_URI, null, new String(where.buff), where.args, orderby);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Log.d(getClass().getSimpleName(), "onLoadFinished called.");
        mAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        Log.d(getClass().getSimpleName(), "onLoaderReset called.");
        mAdapter.swapCursor(null);
    }

    private class ItemsAdapter extends ResourceCursorAdapter {

        public ItemsAdapter(Context context, Cursor cursor) {
            super(context, R.layout.item_list_row, null, false);
        }

        private final class ViewHolder {
            public TextView titleView;
            public ImageView iconView;
            public TextView summaryView;
            public TextView urlView;
            public CheckBox pinView;
        }


        private Item.FilterCursor getItemCursor() {
            return (Item.FilterCursor) getCursor();
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context.
                    getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View v = inflater.inflate(R.layout.item_list_row, null, true);

            ViewHolder holder = new ViewHolder();
            holder.iconView = (ImageView) v.findViewById(R.id.icon_read_unread);
            holder.titleView = (TextView) v.findViewById(R.id.title);
            holder.summaryView = (TextView) v.findViewById(R.id.summary);
            holder.urlView = (TextView) v.findViewById(R.id.url);
            holder.pinView = (CheckBox) v.findViewById(R.id.checkBox);

            v.setTag(holder);

            return v;
        }
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder holder = (ViewHolder) view.getTag();

            Item item = (new Item.FilterCursor(cursor)).getItem();

            holder.iconView.setImageResource(item.isUnread()
                ? R.drawable.item_unread: R.drawable.item_read);
            holder.titleView.setText(item.getTitle());
            holder.summaryView.setText(item.getSummary());
            holder.urlView.setText(item.getUri());
            holder.pinView.setChecked(item.isPin());

            final String url = item.getUri();
            final String title = item.getTitle();
            CheckBox pinCheckbox = (CheckBox) view.findViewById(R.id.checkBox);
            pinCheckbox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CheckBox checkbox = (CheckBox)v;

                    Intent intent = new Intent(ItemListActivity.this, ReaderIntentService.class);
                    if(checkbox.isChecked()){
                        intent.setAction(ReaderIntentService.ACTION_PIN_ADD);
                        intent.putExtra("url", url);
                        intent.putExtra("title", title);
                    }else{
                        intent.setAction(ReaderIntentService.ACTION_PIN_REMOVE);
                        intent.putExtra("url", url);
                    }

                    startService(intent);
                }
            });
        }
    }
}
