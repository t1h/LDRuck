package jp.gr.java_conf.t1h.ldruck;

import android.preference.PreferenceActivity; 
import android.os.Bundle;

public class ReaderPreferenceActivity extends PreferenceActivity {

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference);
    }
}
