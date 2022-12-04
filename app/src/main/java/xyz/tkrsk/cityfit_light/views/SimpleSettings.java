package xyz.tkrsk.cityfit_light.views;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import xyz.tkrsk.cityfit_light.R;

public class SimpleSettings extends AppCompatActivity {
    public SharedPreferences prefs;
    public SharedPreferences.Editor edit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_settings);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        edit = prefs.edit();

        EditText numberEdit = (EditText) findViewById(R.id.editImageSize);
        String val = String.valueOf(prefs.getInt("ImageSize", 200));
        numberEdit.setText(val);


        Button saveBtn = (Button) findViewById(R.id.save);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit.putInt("ImageSize", Integer.parseInt(String.valueOf(numberEdit.getText())));
                edit.commit();

                Intent i = new Intent(SimpleSettings.this, MainActivity.class);
                startActivity(i);
            }
        });
    }
}