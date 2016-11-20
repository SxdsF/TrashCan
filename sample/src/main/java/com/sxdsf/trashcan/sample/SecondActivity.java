package com.sxdsf.trashcan.sample;

import android.app.Activity;
import android.os.Bundle;

import com.sxdsf.trashcan.TrashCan;

import java.util.List;

public class SecondActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);
        List<String> data = TrashCan.getInstance().get("test");
        for (String text : data) {
            System.out.println(text);
        }
        System.out.println(TrashCan.getInstance().get("duration"));
        findViewById(R.id.text).postDelayed(new Runnable() {
            @Override
            public void run() {
                System.out.println(TrashCan.getInstance().get("duration"));
            }
        }, 20000L);
    }
}
