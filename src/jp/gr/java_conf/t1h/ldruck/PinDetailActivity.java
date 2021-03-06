package jp.gr.java_conf.t1h.ldruck;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashSet;

public class PinDetailActivity extends Activity {

    private static final String TAG = "ItemActivity";

    private Subscription sub;
    private Uri subUri;
    private ActivityHelper.Where baseWhere;
    private Item currentItem;
    private Item.FilterCursor itemsCursor;
    private HashSet<Long> readItemIds;
    private CheckBox pinView;

    private final Handler handler = new Handler();

    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {

            initItems(PinDetailActivity.this.currentItem.getUri());
        }
    };

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

//        bindService(new Intent(this, ReaderService.class), this.serviceConn,
//            Context.BIND_AUTO_CREATE);

        setContentView(R.layout.item);
        ActivityHelper.bindTitle(this);

        long itemId = 0;
        if (savedState != null) {
            itemId = savedState.getLong("itemId", 0);
            long[] ids = savedState.getLongArray("readItemIds");
            if (ids != null) {
                this.readItemIds = new HashSet<Long>(ids.length * 2);
                for (long id: ids) {
                    this.readItemIds.add(id);
                }
            }
        }
        if (this.readItemIds == null) {
            this.readItemIds = new HashSet<Long>(32);
        }

        Intent intent = getIntent();
        String pinUrl = intent.getStringExtra("url");


        ImageView iconView = (ImageView) findViewById(R.id.sub_icon);
        Bitmap icon = null; //sub.getIcon(this);
        if (icon == null) {
            iconView.setImageResource(R.drawable.item_read);
        } else {
            iconView.setImageBitmap(icon);
        }

        final WebView bodyView = (WebView) findViewById(R.id.item_body);
        bodyView.setOnTouchListener(new BodyWebViewTouchListener());
        bodyView.setWebViewClient(new BodyWebViewClient());
        WebSettings settings = bodyView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setBuiltInZoomControls(false);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);

        final View previous = findViewById(R.id.previous);
        previous.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                previousItem();
            }
        });
        final View next = findViewById(R.id.next);
        next.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                nextItem();
            }
        });

        this.pinView = (CheckBox) findViewById(R.id.pinCheckBox);
        this.pinView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                progressPin2();
            }
        });

        initItems(pinUrl);

        getContentResolver().registerContentObserver(
                Item.CONTENT_URI, true, mObserver);
    }

//    @Override
//    protected Dialog onCreateDialog(int id) {
//        switch (id) {
//        case ItemActivityHelper.DIALOG_RELOAD:
//            return ItemActivityHelper.createDialogReload(this);
//        case ItemActivityHelper.DIALOG_REMOVE:
//            return ItemActivityHelper.createDialogRemove(this);
//        }
//        return null;
//    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.item, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
        case R.id.menu_item_reload:
            showDialog(ItemActivityHelper.DIALOG_RELOAD);
            return true;
        case R.id.menu_item_pin:
            if(this.pinView.isChecked()){
                this.pinView.setChecked(false);
            }else{
                this.pinView.setChecked(true);
            }

            progressPin2();
            return true;
        case R.id.menu_item_pin_list:
            startActivity(new Intent(this, PinActivity.class));
            return true;
//        case R.id.menu_remove:
//            showDialog(ItemActivityHelper.DIALOG_REMOVE);
//            return true;
        case R.id.menu_item_setting:
            startActivity(new Intent(this, ReaderPreferenceActivity.class));
            return true;
        case R.id.menu_share:
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, this.currentItem.getUri());

            startActivity(intent);
            return true;

        }
        return false;
    }


    @Override
    public void onPause() {
        super.onPause();
        saveReadItemId();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        long[] ids = new long[this.readItemIds.size()];
        int i = 0;
        for (long id: this.readItemIds) {
            ids[i++] = id;
        }
        outState.putLongArray("readItemIds", ids);
        if (this.currentItem != null) {
            outState.putLong("itemId", this.currentItem.getId());
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
    private void progressPin2(){
        final Item item = this.currentItem;

        Intent intent = new Intent(PinDetailActivity.this, ReaderIntentService.class);
        if(this.pinView.isChecked()){
            intent.setAction(ReaderIntentService.ACTION_PIN_ADD);
            intent.putExtra("url", item.getUri());
            intent.putExtra("title", item.getTitle());
        }else{
            intent.setAction(ReaderIntentService.ACTION_PIN_REMOVE);
            intent.putExtra("url", item.getUri());
        }

        startService(intent);
    }
//    private void progressPin() {
//        final Item item = this.currentItem;
//        if (item == null) {
//            return;
//        }
//        final boolean add = !this.pinOn;
//        final ProgressDialog dialog = new ProgressDialog(this);
//        dialog.setIndeterminate(true);
//        if (add) {
//            dialog.setMessage(getText(R.string.msg_pin_add_running));
//        } else {
//            dialog.setMessage(getText(R.string.msg_pin_remove_running));
//        }
//        dialog.show();
//        new Thread() {
//            public void run() {
//                ReaderManager rm = PinDetailActivity.this.readerManager;
//                try {
//                    if (add) {
//                        //rm.pinAdd(item.getUri(), item.getTitle(), true);
//
//                        Intent intent = new Intent(PinDetailActivity.this, ReaderIntentService.class);
//                        intent.setAction(ReaderIntentService.ACTION_PIN_ADD);
//                        intent.putExtra("url", item.getUri());
//                        intent.putExtra("title", item.getTitle());
//
//                        startService(intent);
//                    } else {
////                        rm.pinRemove(item.getUri(), true);
//                        Intent intent = new Intent(PinDetailActivity.this, ReaderIntentService.class);
//                        intent.setAction(ReaderIntentService.ACTION_PIN_REMOVE);
//                        intent.putExtra("url", item.getUri());
//
//                        startService(intent);
//                    }
//                } catch (Throwable e) {
//                    // NOTE: ignore
//                    // showToast(e);
//                }
//                handler.post(new Runnable() {
//                    public void run() {
//                        bindPinView();
//                        dialog.dismiss();
//                    }
//                });
//            }
//        }.start();
//    }


    private void setCurrentItem(Item item) {
        this.currentItem = item;
        if (item == null) {
            this.sub = null;
            return;
        }

        setResult(RESULT_OK, new Intent()
            .putExtra(ActivityHelper.EXTRA_ITEM_ID, item.getId()));


        // subscription 取得
        this.subUri = ContentUris.withAppendedId(Subscription.CONTENT_URI, item.getSubscriptionId());
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(subUri, null, null, null, null);
        cursor.moveToNext();
        this.sub = new Subscription.FilterCursor(cursor).getSubscription();
        cursor.close();

        bindSubTitleView();
        bindItemView();
    }

    /*
     * item 取得
     */
    private void initItems(String url) {
        if (this.itemsCursor != null) {
            this.itemsCursor.close();
        }



        String orderby = Item._ID + " desc";
//        StringBuilder where = new StringBuilder(this.baseWhere.buff);
        String where = Item._URI + " = ?";
        int baseLength = where.length();
        String[] whereArgs = {url};
//        if (this.baseWhere.args != null) {
//            whereArgs = (String[]) this.baseWhere.args.clone();
//        }
//
//        if (itemId == 0 || this.unreadOnly) {
//            // NOTE: find unread
//            String unreadOnlyWhere = " and " + Item._UNREAD + " = 1";
//            int unreadOnlyWhereIndex = where.indexOf(unreadOnlyWhere);
//            if (this.unreadOnly && unreadOnlyWhereIndex == -1) {
//                where.append(unreadOnlyWhere);
//            }
//            Item.FilterCursor csr = new Item.FilterCursor(managedQuery(
//                Item.CONTENT_URI, null, new String(where), whereArgs, orderby));
//            int count = (csr == null) ? 0: csr.getCount();
//            if (count > 0) {
//                this.itemsCursor = skipCursor(csr, itemId);
//                setCurrentItem(csr.getItem());
//                return;
//            }
//            this.unreadOnly = false;
//            // NOTE: reset unread where buffer
//            if (unreadOnlyWhereIndex != -1) {
//                where.delete(unreadOnlyWhereIndex, unreadOnlyWhereIndex
//                    + unreadOnlyWhere.length());
//                this.baseWhere.buff = new StringBuilder(where);
//                baseLength = where.length();
//            } else {
//                where.setLength(baseLength);
//            }
//        }

        Item.FilterCursor csr = new Item.FilterCursor(managedQuery(
            Item.CONTENT_URI, null, new String(where), whereArgs, orderby));
        int count = (csr == null) ? 0: csr.getCount();

        if (count == 0) {
            Log.d(TAG, "url: " + url);
            ContentResolver cr = getContentResolver();
            Cursor cursor = cr.query(Pin.CONTENT_URI, null,
                    Pin._URI + " = ?",
                    new String[]{url}, null);
            Pin.FilterCursor c = new Pin.FilterCursor(cursor);
            c.moveToFirst();
            this.sub = new Subscription();
            this.sub.setTitle("");

            this.currentItem = new Item();
            this.currentItem.setTitle(c.getPin().getTitle());
            this.currentItem.setUri(url);

            c.close();
            cursor.close();

            bindSubTitleView();
            bindItemView();
            return;
        }

        csr.moveToFirst();
//        this.itemsCursor = skipCursor(csr, csr.getId());
        setCurrentItem(csr.getItem());
    }

    private static Item.FilterCursor skipCursor(Item.FilterCursor csr,
            long itemId) {
        if (itemId > 0) {
            boolean found = false;
            while (csr.moveToNext()) {
                if (csr.getId() == itemId) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                csr.moveToFirst();
            }
        } else {
            csr.moveToNext();
        }
        return csr;
    }

    private void saveReadItemId() {
        if (this.currentItem == null || this.subUri == null) {
            return;
        }
        this.sub.setReadItemId(this.currentItem.getId());
        ContentResolver cr = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(Subscription._READ_ITEM_ID, this.sub.getReadItemId());
        cr.update(this.subUri, values, null, null);
    }

    private void nextItem() {
//        scheduleHideTouchControlViews();
        if (this.itemsCursor != null && this.itemsCursor.moveToNext()) {
            setCurrentItem(this.itemsCursor.getItem());
        }
    }

    private void previousItem() {
//        scheduleHideTouchControlViews();
        if (this.itemsCursor != null && this.itemsCursor.moveToPrevious()) {
            setCurrentItem(this.itemsCursor.getItem());
        }
    }

    private void bindTouchControlViews(boolean visible) {
//        if (ReaderPreferences.isShowItemControlls(
//                getApplicationContext())) {
//            visible = true;
//        }

        boolean hasPrevious = false;
        boolean hasNext = false;
        if (this.itemsCursor != null) {
            hasPrevious = !this.itemsCursor.isFirst();
            hasNext = !this.itemsCursor.isLast();
        }

        final View previous = findViewById(R.id.previous);
        final View next = findViewById(R.id.next);

        boolean previousVisible = (previous.getVisibility() == View.VISIBLE);
        boolean nextVisible = (next.getVisibility() == View.VISIBLE);

        if (hasPrevious && visible && !previousVisible) {
            previous.setVisibility(View.VISIBLE);
        } else if (!hasPrevious && previousVisible) {
            previous.setVisibility(View.INVISIBLE);
        }

        if (hasNext && visible && !nextVisible) {
            next.setVisibility(View.VISIBLE);
        } else if (!hasNext && nextVisible) {
            next.setVisibility(View.INVISIBLE);
        }

    }

//    private void hideTouchControlViews() {
//        if (ReaderPreferences.isShowItemControlls(
//                getApplicationContext())) {
//            return;
//        }
//
//        final View previous = findViewById(R.id.previous);
//        final View next = findViewById(R.id.next);
//
//        if (previous.getVisibility() == View.VISIBLE) {
//            Animation a = this.previousHideAnimation;
//            a.setDuration(500);
//            previous.startAnimation(a);
//            previous.setVisibility(View.INVISIBLE);
//        }
//
//        if (next.getVisibility() == View.VISIBLE) {
//            Animation a = this.nextHideAnimation;
//            a.setDuration(500);
//            next.startAnimation(a);
//            next.setVisibility(View.INVISIBLE);
//        }
//    }

//    private void scheduleHideTouchControlViews() {
//        this.handler.removeCallbacks(hideTouchControlViewsRunner);
//        this.handler.postDelayed(hideTouchControlViewsRunner, 2000);
//    }

    private void bindSubTitleView() {
        TextView subTitleView = (TextView) findViewById(R.id.sub_title);
        StringBuilder buff = new StringBuilder(64);
        buff.append(this.sub.getTitle());
        if (this.itemsCursor != null) {
            buff.append(" [");

            buff.append(this.itemsCursor.getPosition() + 1);
            buff.append("/");
            buff.append(this.itemsCursor.getCount());
            buff.append("]");
        }
        subTitleView.setText(new String(buff));
    }

    private void bindItemView() {
        Context c = getApplicationContext();
//        boolean bindTouchControlViews = ReaderPreferences.isShowItemControlls(c);
        ImageView iconView = (ImageView) findViewById(R.id.icon_read_unread);
        TextView titleView = (TextView) findViewById(R.id.item_title);
        WebView bodyView = (WebView) findViewById(R.id.item_body);
        Item item = this.currentItem;
        if (item == null) {
            titleView.setText(getText(R.string.msg_no_item_for_title));
            bodyView.clearView();
        } else {
            iconView.setImageResource(item.isUnread()
                ? R.drawable.item_unread: R.drawable.item_read);
            titleView.setText(item.getTitle());

            bodyView.loadDataWithBaseURL("file:///android_asset/",
                    ItemActivityHelper.createBodyHtml(item), "text/html", "UTF-8", "about:blank");

            this.pinView.setChecked(item.isPin());

            bindTouchControlViews(true);
//            if (bindTouchControlViews) {
//            }
            if (item.isUnread()) {
                this.readItemIds.add(item.getId());
            }

        }
//        bindPinView();
    }

    private boolean pinExists() {
        if (this.currentItem == null) {
            return false;
        }
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(Pin.CONTENT_URI, null,
            Pin._URI + " = ? and " + Pin._ACTION + " <> " + Pin.ACTION_REMOVE,
            new String[]{this.currentItem.getUri()}, null);
        boolean exists = (cursor.getCount() > 0);
        cursor.close();
        return exists;
    }

//    private void bindPinView() {
//        if (pinExists()) {
//            this.pinView.setImageResource(R.drawable.pin_on);
//            this.pinOn = true;
//        } else {
//            this.pinView.setImageResource(R.drawable.pin_off);
//            this.pinOn = false;
//        }
//    }
    @Override
    public void onDestroy(){
            super.onDestroy();
            getContentResolver().unregisterContentObserver(mObserver);
    }
    private class BodyWebViewTouchListener implements View.OnTouchListener {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    bindTouchControlViews(true);
                    break;
                case MotionEvent.ACTION_UP:
//                scheduleHideTouchControlViews();
                    break;
                case MotionEvent.ACTION_MOVE:
                    break;
            }
            return false;
        }
    }

    private class BodyWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, final String url) {
//            if (ReaderPreferences.isDisableItemLinks(getApplicationContext())) {
//                return true;
//            }
            new AlertDialog.Builder(PinDetailActivity.this)
                    .setTitle(R.string.msg_confirm_browse)
                    .setMessage(url)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                        }
                    }).show();
            return true;
        }
    }
}
