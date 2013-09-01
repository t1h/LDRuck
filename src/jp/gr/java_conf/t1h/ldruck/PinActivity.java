package jp.gr.java_conf.t1h.ldruck;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

public class PinActivity extends ListActivity {

    private static final String TAG = "PinActivity";
    private static final int DIALOG_PIN_ACTION = 1;

    private final Handler handler = new Handler();
    private PinsAdapter pinsAdapter;
    private Pin selectedPin;
    private ReaderService readerService;
    private ReaderManager readerManager;

    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            ReaderService.ReaderBinder binder = (ReaderService.ReaderBinder) service;
            PinActivity.this.readerService = binder.getService();
            PinActivity.this.readerManager = binder.getManager();
            if (PinActivity.this.readerManager.isConnected()) {
                PinActivity.this.progressPinAll();
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName className) {
            PinActivity.this.readerService = null;
            PinActivity.this.readerManager = null;
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bindService(new Intent(this, ReaderService.class), this.serviceConn,
            Context.BIND_AUTO_CREATE);

        setContentView(R.layout.pin);

        final View clearAll = findViewById(R.id.clear_all);
        clearAll.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                PinActivity.this.progressPinClear();
            }
        });

        initListAdapter();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(this.serviceConn);
        if (this.pinsAdapter != null) {
            this.pinsAdapter.closeCursor();
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Pin pin = (Pin) v.getTag();

        if (pin != null) {

            // 開くとピンは外れる
            Intent service = new Intent(PinActivity.this, ReaderIntentService.class);
            service.setAction(ReaderIntentService.ACTION_PIN_REMOVE);
            service.putExtra("url", pin.getUri());
            startService(service);

            Intent intent = new Intent(this, PinDetailActivity.class)
                    .putExtra("url", pin.getUri())
                    .putExtra("title", pin.getTitle());
            startActivityForResult(intent, 1);
        }

    }

    private Cursor listQuery() {
        String where = Pin._ACTION + " <> " + Pin.ACTION_REMOVE;
        String orderby = Pin._CREATED_TIME + " asc";
        return managedQuery(Pin.CONTENT_URI, null, where, null, orderby);
    }

    private void initListAdapter() {
        if (this.pinsAdapter == null) {
            this.pinsAdapter = new PinsAdapter(this, listQuery());
            setListAdapter(this.pinsAdapter);
        } else {
            this.pinsAdapter.changeCursor(listQuery());
        }

        View controls = findViewById(R.id.controls);
        View message = findViewById(R.id.message);
        if (this.pinsAdapter.getCount() == 0) {
            controls.setVisibility(View.INVISIBLE);
            message.setVisibility(View.VISIBLE);
        } else {
            controls.setVisibility(View.VISIBLE);
            message.setVisibility(View.INVISIBLE);
        }
    }

    private void progressPinAll() {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setIndeterminate(true);
        dialog.setMessage(getText(R.string.msg_pin_all_running));
        dialog.show();
        new Thread() {
            public void run() {
//                ReaderManager rm = PinActivity.this.readerManager;
//                try {
//                    rm.syncPins();
//                } catch (IOException e) {
//                    showToast(e);
//                } catch (ReaderException e) {
//                    showToast(e);
//                }
                Intent intent = new Intent(PinActivity.this, ReaderIntentService.class);
                intent.setAction(ReaderIntentService.ACTION_PIN_SYNC);
                startService(intent);

                handler.post(new Runnable() {
                    public void run() {
                        PinActivity.this.initListAdapter();
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }

    private void progressPinRemove(final Pin pin) {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setIndeterminate(true);
        dialog.setMessage(getText(R.string.msg_pin_remove_running));
        dialog.show();
        new Thread() {
            public void run() {
                ReaderManager rm = PinActivity.this.readerManager;
                try {
                    rm.pinRemove(pin.getUri(), false);
                } catch (IOException e) {
                    showToast(e);
                } catch (ReaderException e) {
                    showToast(e);
                }
                handler.post(new Runnable() {
                    public void run() {
                        PinActivity.this.initListAdapter();
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }

    private void progressPinClear() {
        final Context context = getApplicationContext();
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setIndeterminate(true);
        dialog.setMessage(getText(R.string.msg_pin_remove_running));
        dialog.show();
        new Thread() {
            public void run() {
                ReaderManager rm = PinActivity.this.readerManager;
                try {
                    rm.pinClear();
                } catch (IOException e) {
                    showToast(e);
                } catch (ReaderException e) {
                    showToast(e);
                }
                handler.post(new Runnable() {
                    public void run() {
                        PinActivity.this.finish();
                        dialog.dismiss();
                    }
                });
            }
        }.start();
    }

    private void showToast(IOException e) {
        e.printStackTrace();
        showToast(getText(R.string.err_io) + " (" + e.getLocalizedMessage() + ")");
    }

    private void showToast(ReaderException e) {
        e.printStackTrace();
        showToast(e.getLocalizedMessage());
    }

    private void showToast(final CharSequence text) {
        this.handler.post(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class PinsAdapter extends ResourceCursorAdapter {

        public PinsAdapter(Context context, Cursor cursor) {
            super(context, R.layout.pin_row, new Pin.FilterCursor(cursor));
        }

        private void closeCursor() {
            Cursor cursor = getCursor();
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(new Pin.FilterCursor(cursor));
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            Pin.FilterCursor pinCursor = (Pin.FilterCursor) cursor;

            TextView titleView = (TextView) view.findViewById(R.id.title);
            TextView uriView = (TextView) view.findViewById(R.id.uri);

            Pin pin = pinCursor.getPin();
            titleView.setText(pin.getTitle());
            uriView.setText(pin.getUri());
            view.setTag(pin.clone());
        }
    }

    private class PinActionListener implements DialogInterface.OnClickListener {

        private final Pin pin;

        private PinActionListener(Pin pin) {
            this.pin = pin;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
            case 0:
                startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(this.pin.getUri())));
                break;
            case 2:
                PinActivity.this.progressPinRemove(this.pin);
                break;
            }
        }
    }
}
