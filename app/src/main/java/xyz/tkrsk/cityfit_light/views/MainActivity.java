package xyz.tkrsk.cityfit_light.views;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import xyz.tkrsk.cityfit_light.QRFragment;
import xyz.tkrsk.cityfit_light.R;
import xyz.tkrsk.cityfit_light.cityfit.CityFitREAPI;

public class MainActivity extends AppCompatActivity {
    public QRFragment frag;
    public CityFitREAPI api;
    ImageView image;
    public Handler imageUpdateHandler = new Handler(Looper.getMainLooper());
    public Runnable imageUpdate = new Runnable() {
        @Override
        public void run() {
            System.out.println("QRData: " + api.GetQRData());
            try {
                image.setImageBitmap(encodeAsBitmap(api.GetQRData()));
            } catch (WriterException e) {
                e.printStackTrace();
            }
            imageUpdateHandler.postDelayed(imageUpdate, 1000);
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        api =  CityFitREAPI.getInstance(this);
        api.getRequestQueue(this);
        Activity ctx = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText email = (EditText) findViewById(R.id.editTextTextEmailAddress);
        EditText password = (EditText) findViewById(R.id.editTextTextPassword2);
        EditText device_id = (EditText) findViewById(R.id.editTextTextPersonName4);

        Button settingsBtn = (Button)findViewById(R.id.button);
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, SimpleSettings.class);
                startActivity(i);
            }
        });


        device_id.setText(api.prefs.getString("device_id", api.getNewDeviceID(this)));
        api.edit.putString("device_id", String.valueOf(device_id.getText()));

        email.setText(api.prefs.getString("email", ""));
        api.edit.putString("email", String.valueOf(email.getText()));
        password.setText(api.prefs.getString("password", ""));
        api.edit.putString("password", String.valueOf(password.getText()));

        device_id.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() != 0) {
                    api.edit.putString("device_id", s.toString());
                    api.edit.commit();
                }
            }
        });

        Button loginBtn = (Button) findViewById(R.id.UpdateCredentialsButton);
        Button qrBtn = (Button) findViewById(R.id.ShowQRCodeButton);

        TextView status = (TextView) findViewById(R.id.statusText);
        CheckBox check = (CheckBox)findViewById(R.id.checkBox);
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                api.edit.putString("email", email.getText().toString());
                api.edit.putString("password", password.getText().toString());
                api.edit.commit();

                api.getRequestQueue(ctx).cancelAll("login-req");
                api.getRequestQueue(ctx).cancelAll("qr-req");
                JSONObject data = new JSONObject();
                try {
                    data.put("email", api.prefs.getString("email", ""));
                    data.put("password", api.prefs.getString("password", ""));
                } catch (JSONException e) {
                    e.printStackTrace();
                    status.setText("Error: cant find email/password in preferences?");
                    return;
                }
                JsonObjectRequest req = new JsonObjectRequest(api.login_url, data, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        status.setText("login-success");
                        api.edit.putString("token", response.optString("accessToken"));
                        api.edit.commit();

                        System.out.println(response.optString("accessToken"));

                        if (check.isChecked())
                            api.registerDevice(status, response.optString("accessToken"));
                        check.setChecked(false);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        status.setText("failed-login");
                        System.out.println(error.toString());
                    }
                });
                System.out.println(req.toString());
                req.setTag("login-req");
                status.setText("Logging-in");
                api.addToRequestQueue(ctx, req);
            }
        });
        qrBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (api.prefs.getString("token", "") == "") {
                    api.getRequestQueue(ctx).cancelAll("login-req");
                    api.getRequestQueue(ctx).cancelAll("qr-req");
                    status.setText("no-login-token");
                    return;
                }
                try {
                    System.out.println("QRData: " + api.GetQRData());
                    if (image != null)
                        image.setImageBitmap(encodeAsBitmap(api.GetQRData()));
                } catch (WriterException e) {
                    e.printStackTrace();
                }

                AlertDialog.Builder builder =
                        new AlertDialog.Builder(ctx).
                                setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.cancel();
                                        StopGeneratingQR();
                                    }
                                });
                OnShowQRGeneration(builder);
                builder.setCancelable(true);
                AlertDialog dialog_card = builder.create();
                dialog_card.getWindow().setGravity(Gravity.TOP);
                dialog_card.show();
            }
        });
    }

    Bitmap encodeAsBitmap(String str) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        int imageSize = api.prefs.getInt("ImageSize", 200);
        BitMatrix bitMatrix = writer.encode(str, BarcodeFormat.QR_CODE, imageSize, imageSize);

        int w = bitMatrix.getWidth();
        int h = bitMatrix.getHeight();
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pixels[y * w + x] = bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return bitmap;
    }

    public void OnShowQRGeneration(AlertDialog.Builder builder) {
        image = new ImageView(this);
        builder.setView(image);
        StartGeneratingQR();
    }

    private void StartGeneratingQR() {
        api.RUFlag(true, true);
        api.QRReqHandler.post(api.qrThread);
        imageUpdateHandler.post(imageUpdate);
    }

    private void StopGeneratingQR() {
        api.RUFlag(true, false);
        imageUpdateHandler.removeCallbacksAndMessages(null);

        image = null;
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        CityFitREAPI api = CityFitREAPI.getInstance(this);
        api.getRequestQueue(this).stop();
        super.onDestroy();
    }
}