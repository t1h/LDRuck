package jp.gr.java_conf.t1h.ldruck;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.InputStream;

public class URLImageParser implements Html.ImageGetter {
    Context c;
    TextView container;

    Float scaledDensity;


    /***
     * Construct the URLImageParser which will execute AsyncTask and refresh the container
     * @param t
     * @param c
     */
    public URLImageParser(TextView t, Context c) {
        this.c = c;
        this.container = t;

        DisplayMetrics m = this.c.getResources().getDisplayMetrics();
        this.scaledDensity = m.scaledDensity;

    }

    public Drawable getDrawable(String source) {

        URLDrawable urlDrawable = new URLDrawable();

        // get the actual source
        ImageGetterAsyncTask asyncTask =
                new ImageGetterAsyncTask( urlDrawable, this.scaledDensity);

        asyncTask.execute(source);

        // return reference to URLDrawable where I will change with actual image from
        // the src tag
        return urlDrawable;
    }

    public class ImageGetterAsyncTask extends AsyncTask<String, Void, Drawable> {
        URLDrawable urlDrawable;
        Float scaledDensity;

        public ImageGetterAsyncTask(URLDrawable d, Float density) {
            this.urlDrawable = d;
            this.scaledDensity = density;
        }

        @Override
        protected Drawable doInBackground(String... params) {
            String source = params[0];
            return fetchDrawable(source);
        }

        @Override
        protected void onPostExecute(Drawable result) {
            // set the correct bound according to the result from HTTP call

            Integer w = Math.round(result.getIntrinsicWidth() * this.scaledDensity);
            Integer h = Math.round(result.getIntrinsicHeight() * this.scaledDensity);
            urlDrawable.setBounds(0, 0, 0 + w, 0 + h);

            // change the reference of the current drawable to the result
            // from the HTTP call
            urlDrawable.drawable = result;

            // redraw the image by invalidating the container
            URLImageParser.this.container.invalidate();

            // For ICS
            URLImageParser.this.container.setHeight((URLImageParser.this.container.getHeight()
                    + h));

            // Pre ICS
            URLImageParser.this.container.setEllipsize(null);


        }

        /***
         * Get the Drawable from URL
         * @param urlString
         * @return
         */
        public Drawable fetchDrawable(String urlString) {
            try {
                InputStream is = fetch(urlString);

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 4;
                Bitmap b = BitmapFactory.decodeStream(is, null, options);
                Drawable drawable = new BitmapDrawable(Resources.getSystem(), b);

                Integer ri = 0 + Math.round(drawable.getIntrinsicWidth() * this.scaledDensity);
                Integer bo = 0 + Math.round(drawable.getIntrinsicHeight() * this.scaledDensity);

                drawable.setBounds(0, 0, ri, bo);
                return drawable;
            } catch (Exception e) {
                return null;
            }
        }

        private InputStream fetch(String urlString) throws IOException {
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpGet request = new HttpGet(urlString);
            HttpResponse response = httpClient.execute(request);
            return response.getEntity().getContent();
        }
    }
}
