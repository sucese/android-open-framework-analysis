package com.guoxiaoxing.greendao.demo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";

    private DBManager dbManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupData();

        findViewById(R.id.btn_insert).setOnClickListener(this);
        findViewById(R.id.btn_delete).setOnClickListener(this);
        findViewById(R.id.btn_query).setOnClickListener(this);
        findViewById(R.id.btn_update).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        List<User> users = dbManager.listAllData();
        switch (view.getId()) {
            case R.id.btn_insert:
                User user = new User();
                user.setAge(11 * 5);
                user.setName("增加的人");
                insert(user);
                break;
            case R.id.btn_delete:
                User delete = users.get(5);
                delete(delete);
                break;
            case R.id.btn_query:
                query(45);
                break;
            case R.id.btn_update:
                User update = new User();
                update.setId(63L);
                update.setAge(15);
                update.setName("更新的人");
                update(update);
                break;
        }
    }

    private void setupData() {
        dbManager = DBManager.getInstance(this);
        for (int i = 0; i < 10; i++) {
            User user = new User();
            user.setAge(i * 5);
            user.setName("第" + i + "人");
            dbManager.insert(user);
        }
    }

    private void insert(User user) {
        dbManager.insert(user);
        printData();
    }

    private void delete(User user) {
        dbManager.delete(user);
        printData();
    }

    private void query(int age) {
        dbManager.query(age);
        printData();
    }

    private void update(User user) {
        dbManager.update(user);
        printData();
    }

    private void printData() {
        List<User> users = dbManager.listAllData();
        for (User user : users) {
            Log.d(TAG, user.getId() + "-" + user.getAge() + "-" + user.getName());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbManager.clear();
    }
}
