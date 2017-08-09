package com.guoxiaoxing.framwork.rxjava;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_opertor).setOnClickListener(this);
        findViewById(R.id.btn_thread).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_opertor:
                startActivity(new Intent(MainActivity.this, OperatorActivity.class));
                break;
            case R.id.btn_thread:
                startActivity(new Intent(MainActivity.this, ThreadActivity.class));
                break;
        }
    }
}
