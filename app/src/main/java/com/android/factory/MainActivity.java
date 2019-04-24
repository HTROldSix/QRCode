package com.android.factory;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.SeekBar;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.crypt_keeper);

        ProgressBar progressBar = findViewById(R.id.progress_bar);
        progressBar.setProgress(30);
    }
}
