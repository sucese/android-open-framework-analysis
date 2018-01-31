package com.guoxiaoxing.fresco.demo;

import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.facebook.drawee.view.SimpleDraweeView;

public class FrescoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String url = "";
        SimpleDraweeView simpleDraweeView = findViewById(R.id.drawee_view);
        simpleDraweeView.setImageURI(Uri.parse(url));
    }
}
