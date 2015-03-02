package jp.gr.java_conf.t1h.ldruck;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;

import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import static jp.gr.java_conf.t1h.ldruck.Utils.asInt;
import static jp.gr.java_conf.t1h.ldruck.Utils.asLong;
import static jp.gr.java_conf.t1h.ldruck.Utils.asString;

public class ReaderIntentService extends IntentService{

    public static final String ACTION_PIN_ADD
            = "jp.gr.java_conf.t1h.ldruck.action.PIN_ADD";
    public static final String ACTION_PIN_REMOVE
            = "jp.gr.java_conf.t1h.ldruck.action.PIN_REMOVE";
    public static final String ACTION_PIN_SYNC
            = "jp.gr.java_conf.t1h.ldruck.action.PIN_SYNC";
    public static final String ACTION_SUBSCRIPTION_READ
            = "jp.gr.java_conf.t1h.ldruck.action.SUBSCRIPTION_READ";
    public static final String ACTION_UNSUBSCRIBE
            = "jp.gr.java_conf.t1h.ldruck.action.UNSUBSCRIBE";

    private static final String TAG = "ReaderIntentService";

    private final ApiClient client = new ApiClient();
    private final Context context;

    public ReaderIntentService() {
        super("ReaderIntentService");

        this.context = this;
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        String action = intent.getAction();

        if(action.equals(ACTION_PIN_ADD)){
            try {
                String uri = intent.getStringExtra("url");
                String title = intent.getStringExtra("title");

                pinAdd(uri, title, true);

                return;
            }catch (Exception e) {
            }
        }else if(action.equals(ACTION_PIN_REMOVE)){
            String uri = intent.getStringExtra("url");

            try {
                pinRemove(uri);
            }catch (Exception e) {
                ActivityHelper.showToast(getApplicationContext(), "error");
            }

        }else if(action.equals(ACTION_PIN_SYNC)){

            try {
                syncPins();
            }catch (Exception e) {
                ActivityHelper.showToast(getApplicationContext(), "error");
            }
        }else if(action.equals(ACTION_SUBSCRIPTION_READ)){
            Long subscriptionId = intent.getLongExtra("subscriptionId", 0);
            Long timestamp = intent.getLongExtra("timestamp", 0);
            readAll(subscriptionId, timestamp);
        }else if(action.equals(ACTION_UNSUBSCRIBE)){
            Long subscriptionId = intent.getLongExtra("subscriptionId", 0);
            unsubscribe(subscriptionId);
        }
    }

    public int syncPins() throws IOException, ReaderException {
        if (!isLogined()) {
            login();
        }

        ContentResolver cr = this.context.getContentResolver();
        cr.query(ReaderProvider.URI_TXN_BEGIN, null, null, null, null);
        try {
            String where = Pin._ACTION + " > " + Pin.ACTION_NONE;
            String order = Pin._ID + " asc";
            Pin.FilterCursor cursor = new Pin.FilterCursor(
                    cr.query(Pin.CONTENT_URI, null, where, null, order));
            try {
                while (cursor.moveToNext()) {
                    Pin pin = cursor.getPin();
                    if (pin.getAction() == Pin.ACTION_ADD) {
                        pinAdd(pin.getUri(), pin.getTitle(), false);
                    } else {
                        pinRemove(pin.getUri());
                    }
                    Uri uri = ContentUris.withAppendedId(Pin.CONTENT_URI, pin.getId());
                    cr.delete(uri, null, null);
                }
            } finally {
                cursor.close();
            }

            PinsHandler pinsHandler = new PinsHandler();
            try {
                this.client.handlePinAll(pinsHandler);
            } catch (ParseException e) {
                throw new ReaderException("json parse error", e);
            }
            cr.query(ReaderProvider.URI_TXN_SUCCESS, null, null, null, null);
            return pinsHandler.counter;
        } finally {
            cr.query(ReaderProvider.URI_TXN_END, null, null, null, null);
        }
    }

    public boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager)
                this.context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return (netInfo != null && netInfo.isConnected());
    }

    public boolean login() throws IOException, ReaderException {
        String loginId = ReaderPreferences.getLoginId(this.context);
        String password = ReaderPreferences.getPassword(this.context);
        return login(loginId, password);
    }
    public boolean login(String loginId, String password)
            throws IOException, ReaderException {
        return this.client.login(loginId, password);
    }

    public boolean isLogined() {
        return this.client.isLogined();
    }
    public boolean pinAdd(final String uri, final String title, boolean nowait)
            throws IOException, ReaderException {

        try {
            final ContentResolver cr = this.context.getContentResolver();
            cr.delete(Pin.CONTENT_URI, Pin._URI + " = ? and " + Pin._ACTION
                + " > " + Pin.ACTION_NONE, new String[]{uri});

            final ContentValues values = new ContentValues();
            values.put(Pin._URI, uri);
            values.put(Pin._TITLE, title);
            values.put(Pin._ACTION, Pin.ACTION_ADD);
            values.put(Pin._CREATED_TIME, (long) (System.currentTimeMillis() / 1000));
            final Uri pinUri = cr.insert(Pin.CONTENT_URI, values);

            if (!isConnected()) {
                return true;
            }

            if (nowait) {
                new Thread() {
                    public void run() {
                        try {
                            runPinAdd(uri, title, cr, pinUri, values);
                        } catch (Exception e) {
                            // NOTE: ignore IOException, ParseException, ReaderException
                        }
                    }
                }.start();
                return true;
            }

            return runPinAdd(uri, title, cr, pinUri, values);
        } catch (ParseException e) {
            throw new ReaderException("json parse error", e);
        }
    }
    private boolean runPinAdd(String uri, String title, ContentResolver cr,
                              Uri pinUri, ContentValues values)
            throws IOException, ParseException, ReaderException {

        // item table
        String selectionClause = Item._URI + " = ?";
        ContentValues v = new ContentValues();
        v.put(Item._PIN, 1);
        String[] selectionArgs = {uri};
        cr.update(Item.CONTENT_URI, v, selectionClause, selectionArgs);

        // api
        if (!isLogined()) {
            login();
        }
        boolean success = this.client.pinAdd(uri, title);

        // pin table
        cr.delete(Pin.CONTENT_URI, Pin._URI + " = ? and " + Pin._ACTION
                + " = " + Pin.ACTION_NONE, new String[]{uri});
        values.put(Pin._ACTION, Pin.ACTION_NONE);
        cr.update(pinUri, values, null, null);

        return success;
    }
    public boolean pinRemove(final String uri)
            throws IOException, ReaderException {
        try {
            final ContentResolver cr = this.context.getContentResolver();
//            cr.delete(Pin.CONTENT_URI, Pin._URI + " = ?", new String[]{uri});

            final ContentValues values = new ContentValues();
//            values.put(Pin._URI, uri);
            values.put(Pin._ACTION, Pin.ACTION_REMOVE);
            values.put(Pin._CREATED_TIME, (long) (System.currentTimeMillis() / 1000));
//            final Uri pinUri = cr.insert(Pin.CONTENT_URI, values);
            cr.update(Pin.CONTENT_URI, values, Pin._URI + " = ?", new String[]{uri});

            if (!isConnected()) {
                return true;
            }

            // item table
            String selectionClause = Item._URI + " = ?";
            ContentValues v = new ContentValues();
            v.put(Item._PIN, 0);
            String[] selectionArgs = {uri};
            cr.update(Item.CONTENT_URI, v, selectionClause, selectionArgs);

            // API
            if (!isLogined()) {
                login();
            }
            boolean success = this.client.pinRemove(uri);
//        cr.delete(pinUri, null, null);

            return success;
        } catch (ParseException e) {
            throw new ReaderException("json parse error", e);
        }
    }
    public void readAll(Long subscriptionId, Long timestamp){


        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp * 1000);
        Log.d(TAG, "touch timestamp: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(cal.getTime()));

        if (ReaderPreferences.isAutoTouchAll(this)) {
            try {
                if (!isLogined()) {
                    login();
                }
                Boolean result = this.client.touch(subscriptionId, timestamp);
            } catch (Exception e) {
                ActivityHelper.showToast(getApplicationContext(), "error");

            }
        }
    }
    public void unsubscribe(Long subscriptionId){
        try {
            if (!isLogined()) {
                login();
            }
            Boolean result = this.client.unsubscribe(subscriptionId);
        } catch (Exception e) {
            ActivityHelper.showToast(getApplicationContext(), "error");

        }

    }

    private class PinsHandler extends ContentHandlerAdapter {

        private ContentResolver cr;
        private ContentValues values;
        private int counter;

        public void startJSON() throws ParseException, IOException {
            this.counter = 0;
            this.cr = ReaderIntentService.this.context.getContentResolver();
            cr.delete(Pin.CONTENT_URI, null, null);
        }

        public boolean startObject() throws ParseException, IOException {
            this.values = new ContentValues();
            this.counter++;
            return true;
        }

        public boolean endObject() throws ParseException, IOException {
            if (this.values != null) {
                this.values.put(Pin._ACTION, Pin.ACTION_NONE);
                this.cr.insert(Pin.CONTENT_URI, this.values);
                this.values = null;
            }
            return true;
        }

        public boolean primitive(Object value)
                throws ParseException, IOException {
            if (this.key == null || this.values == null) {
                return true;
            } else if (this.key.equals("link")) {
                this.values.put(Pin._URI, asString(value));
            } else if (this.key.equals("title")) {
                this.values.put(Pin._TITLE, asString(value));
            } else if (this.key.equals("created_on")) {
                this.values.put(Pin._CREATED_TIME, asLong(value));
            }
            return true;
        }
    }
    private static abstract class ContentHandlerAdapter
            implements ContentHandler {

        protected String key;

        public void startJSON() throws ParseException, IOException {
        }

        public void endJSON() throws ParseException, IOException {
        }

        public boolean startObject() throws ParseException, IOException {
            return true;
        }

        public boolean endObject() throws ParseException, IOException {
            return true;
        }

        public boolean startObjectEntry(String key)
                throws ParseException, IOException {
            this.key = key;
            return true;
        }

        public boolean endObjectEntry() throws ParseException, IOException {
            this.key = null;
            return true;
        }

        public boolean startArray() throws ParseException, IOException {
            return true;
        }

        public boolean endArray() throws ParseException, IOException {
            return true;
        }

        public boolean primitive(Object value)
                throws ParseException, IOException {
            return true;
        }
    }
}
