package xyz.tkrsk.cityfit_light.cityfit;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.widget.TextView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.instacart.library.truetime.TrueTime;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import kotlin.collections.ArraysKt;
import kotlin.collections.CollectionsKt;

public class CityFitREAPI {
    private static CityFitREAPI instance;
    public Handler QRReqHandler = new Handler(Looper.getMainLooper());
    public Runnable qrThread = new Runnable() {
        @Override
        public void run() {
            requestQueue.cancelAll("login-req");
            requestQueue.cancelAll("qr-req");
            JsonObjectRequest sq = new JsonObjectRequest(Request.Method.GET, qr_url + prefs.getString("device_id", ""), new JSONObject(), new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    System.out.println("Success");
                    aCode = response.optString("accessToken");
                    aLevel = response.optString("accessLevel");
                    wTime = response.optInt("passwordValidityDuration", 15);

                    System.out.println(aCode + " "  + aLevel + " " + wTime);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    String body = null;
                    try {
                        body = new String(error.networkResponse.data,"UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    System.out.println(body);
                }
            }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    Map<String, String>  params = new HashMap<String, String>();
                    params.put("authorization", "Bearer " + prefs.getString("token", ""));
                    return params;
                }
            };
            sq.setTag("qr-req");
            requestQueue.add(sq);
            if (RUFlag(false, false))
                QRReqHandler.postDelayed(qrThread, 5000);
            else
            {
                requestQueue.cancelAll("login-req");
                requestQueue.cancelAll("qr-req");
                QRReqHandler.removeCallbacksAndMessages(null);
            }
        }
    };

    public synchronized boolean RUFlag(boolean u, boolean v) {
        if (u)
            shouldUpdateQRData = v;
        return shouldUpdateQRData;
    }
    private boolean shouldUpdateQRData = false;

    private String aCode = "1";
    private String aLevel = "1";
    private int wTime = 1;

    private RequestQueue requestQueue;
    public SharedPreferences prefs;
    public SharedPreferences.Editor edit;
    public String login_url = "https://klubowicz.cityfit.pl/api/tokens";
    public String register_url = "https://klubowicz.cityfit.pl/api/me/device?mobileDeviceId=";
    public String qr_url = "https://klubowicz.cityfit.pl/api/me/access/permit?mobileDeviceId=";

    public RequestQueue getRequestQueue(Activity activity) {
        if (requestQueue == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            requestQueue = Volley.newRequestQueue(activity.getApplicationContext());
        }
        return requestQueue;
    }

    public <T> void addToRequestQueue(Activity activitiy, Request<T> req) {
        getRequestQueue(activitiy).add(req);
    }


    private CityFitREAPI(Activity activity) {
        prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        edit = prefs.edit();

            Thread t = new Thread() {
                @Override
                public void run() {
                    try {
                        TrueTime.build().withNtpHost("tempus1.gum.gov.pl").initialize();
                    } catch (IOException e) {
                        activity.finishAndRemoveTask();
                        e.printStackTrace();
                    }
                }
            };
            t.start();
    }

    public void registerDevice(TextView status, String token) {
        if (prefs.getString("device_id", "") == "") {
            status.setText("bad-device-id");
            System.out.println("NO DEVICE ID?");
            return;
        }
        JSONObject data = new JSONObject();
        try {
            data.put("mobileDeviceId", prefs.getString("device_id", ""));
        } catch (JSONException e) {
            e.printStackTrace();
            status.setText("Error: cant find email/password in preferences?");
            return;
        }
        System.out.println("URL: " + register_url + prefs.getString("device_id", ""));
        JsonObjectRequest req = new JsonObjectRequest(register_url + prefs.getString("device_id", ""), data, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                status.setText("register-success");
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                try {
                    if (error.networkResponse.statusCode == 409) {
                        status.setText("already-registered");
                        return;
                    }
                } catch(Exception ex) {
                    System.out.println(ex);
                    status.setText("uknown-error");
                    return;
                }
                status.setText("failed-register");
                System.out.println(error.toString());
                System.out.println(error.getLocalizedMessage());
                String body = null;
                try {
                    body = new String(error.networkResponse.data,"UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                System.out.println(body);
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<String, String>();
                params.put("authorization", "Bearer " + token);
                System.out.println("HEADERS = " + "authorization" + " Bearer " + token);
                return params;
            }
        };
        req.setTag("login-req");
        status.setText("register-in");

        requestQueue.add(req);
    }

    public static CityFitREAPI getInstance(Activity activity) {
        if (instance == null)
            instance = new CityFitREAPI(activity);
        return instance;
    }

    private final String toUuidString(String paramString) {
        Charset charset = Charset.forName("utf8");
        byte[] arrayOfByte = paramString.getBytes(charset);
        String str = UUID.nameUUIDFromBytes(arrayOfByte).toString();
        return str;
    }

    public String getNewDeviceID(Activity activity) {
        // testing c26576d4-40fe-3a57-b758-22cc9f887d7f
        return toUuidString(Settings.Secure.getString(activity.getContentResolver(),
                Settings.Secure.ANDROID_ID));
    }

    public final String QRString(String str, final String str2, final int n) throws InvalidKeyException, NoSuchAlgorithmException {
        final byte[] bytes = String.valueOf(getSeconds() / n).getBytes(StandardCharsets.UTF_8);
        final byte[] bytes2 = str.getBytes(StandardCharsets.UTF_8);
        final SecretKeySpec secretKeySpec = new SecretKeySpec(bytes2, "HmacSHA1");
        final Mac instance = Mac.getInstance("HmacSHA1");
        instance.init(secretKeySpec);
        final byte[] doFinal = instance.doFinal(bytes);
        str = toHex(CollectionsKt.toByteArray(ArraysKt.take(doFinal, 8)));
        str = "##" + str2 + str;
        return str;
    }

    private static final String toHex(byte[] bArr) {
        String str = "";
        for(int i = 0; i < bArr.length; ++i) {
            byte b = bArr[i];
            StringBuilder append = new StringBuilder().append(str);
            String format = String.format("%02X", Arrays.copyOf(new Object[]{Byte.valueOf(b)}, 1));
            str = append.append(format).toString();
        }
        return str;
    }

    public String GetQRData() {
        try {
            return QRString(aCode, aLevel, wTime);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    public long getSeconds() {
        long l;
        try {
            if (TrueTime.isInitialized()) {
                l = TrueTime.now().getTime();
                l = TimeUnit.MILLISECONDS.toSeconds(l);
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    l = Instant.now().getEpochSecond();
                } else {
                    l = 0;
                }
            }
        } catch (Exception exception) {
            System.out.println(exception.toString());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                l = Instant.now().getEpochSecond();
            } else {
                l = 0;
            }
        }
        return l;
    }
}
