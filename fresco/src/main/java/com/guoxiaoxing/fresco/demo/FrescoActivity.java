package com.guoxiaoxing.fresco.demo;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.facebook.drawee.view.SimpleDraweeView;

public class FrescoActivity extends AppCompatActivity implements View.OnClickListener {

    SimpleDraweeView simpleDraweeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        simpleDraweeView = findViewById(R.id.drawee_view);
        findViewById(R.id.btn_load_image).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_load_image:
                loadImage();
                break;
        }
    }

    private void loadImage() {
        String url = "https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/fresco/scenery.jpg";
        simpleDraweeView.setImageURI(Uri.parse(url));
    }
}
