package com.gentek.stompclientandroid;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import org.java_websocket.WebSocket;
import java.text.SimpleDateFormat;
import java.util.Locale;
import io.reactivex.FlowableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.client.StompClient;


public class MainActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener {

    private static final String TAG = "MainActivity";
    private static final String TAGE = "Error";
    private StompClient mStompClient;
    private final SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private SwitchCompat mSwitch;
    private static int flag = 0;
    private LineGraphSeries<DataPoint> mSeries2;
    private static long millis;
    private static double mYValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //adding graphview with data points

        GraphView graphView = (GraphView)findViewById(R.id.graph);
        mSeries2  = new LineGraphSeries<>();
        millis = System.currentTimeMillis();
        double milliBound = Double.valueOf(millis);

        //add series generated to the graph

        graphView.addSeries(mSeries2);

        //add horizontal scrolling

        graphView.getViewport().setScrollable(true);

        //add bounds in X-Axis with max 5 elements

        graphView.getViewport().setXAxisBoundsManual(true);
        graphView.getViewport().setMinX(milliBound+60000);
        graphView.getViewport().setMaxX(milliBound+360000);

        //set Chart Title and Graph Title

        graphView.setTitle("Temperature Chart");
        graphView.getGridLabelRenderer().setHorizontalAxisTitle("Time");

        //add a custom label formatter to display time on X-Axis

        graphView.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter(){
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if(isValueX){
                    long millis = (new Double(value)).longValue();
                    return mTimeFormat.format(millis);
                }else
                {
                    return super.formatLabel(value,isValueX);
                }
            }
        });

        graphView.getGridLabelRenderer().setVerticalAxisTitle("Temperature");

        //set humanRounding to false to generate accurate received data

        graphView.getGridLabelRenderer().setHumanRounding(true);
        graphView.getGridLabelRenderer().setNumHorizontalLabels(4);

        mSwitch = (SwitchCompat)findViewById(R.id.switch_light);
        mSwitch.setOnCheckedChangeListener(this);
        mSwitch.setClickable(false);
    }

    public void disconnectStomp(View view) {
        if(flag==1){
            mStompClient.disconnect();
            flag=0;
        }
        else
            toast("Please connect to disconnect");
    }

    public void connectStomp(View view) {
        flag =1;
        mStompClient = Stomp.over(WebSocket.class, "ws://172.16.73.4:8080/IOTServer/websocket");
        mStompClient.lifecycle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(lifecycleEvent -> {
                    switch (lifecycleEvent.getType()) {
                        case OPENED:
                            toast("Stomp connection opened");
                            mStompClient.send("/app/tempsub")
                                    .compose(applySchedulers())
                                    .subscribe(aVoid -> {
                                        Log.d(TAGE, "STOMP echo send successfully");
                                    }, throwable -> {
                                        Log.e(TAGE, "Error send STOMP echo", throwable);
                                        toast(throwable.getMessage());
                                    });
                            break;
                        case ERROR:
                            Log.e(TAG, "Stomp connection error", lifecycleEvent.getException());
                            toast("Stomp connection error");
                            break;
                        case CLOSED:
                            toast("Stomp connection closed");
                    }
                });

        // Receive greetings

        subscribe(view,"/topic/message");
        subscribe(view,"/topic/tempsub");

        mStompClient.connect();
        mSwitch.setClickable(true);
    }

    private void subscribe(View v, String topic) {
        mStompClient.topic(topic)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(topicMessage -> {
                    if(topic.equalsIgnoreCase("/topic/tempsub")){
                        mYValue = Double.valueOf(topicMessage.getPayload());
                        double data = (new Long(millis)).doubleValue();
                        mSeries2.appendData(new DataPoint(data,mYValue),true,60);
                        millis+=60000;
                    }
                    Log.d(TAG, "Received " + topicMessage.getPayload());
                });
    }

    public void sendEchoViaStomp(String topic,String data) {
        mStompClient.send(topic, data)
                .compose(applySchedulers())
                .subscribe(aVoid -> {
                    Log.d(TAGE, "STOMP echo send successfully");
                }, throwable -> {
                    Log.e(TAGE, "Error send STOMP echo", throwable);
                    toast(throwable.getMessage());
                });
    }

    private void toast(String text) {
        Log.i(TAG, text);
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    protected <T> FlowableTransformer<T, T> applySchedulers() {
        return tFlowable -> tFlowable
                .unsubscribeOn(Schedulers.newThread())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    protected void onDestroy() {
        if(flag==1)
            mStompClient.disconnect();
        super.onDestroy();
    }


    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if(flag==1){
            if(compoundButton.getId() == R.id.switch_light){
                if(b){
                    sendEchoViaStomp("/app/LedON","ON");
                    Log.d(TAG,"Data send On");
                }
                else{
                    sendEchoViaStomp("/app/LedOFF","OFF");
                    Log.d(TAG,"Data send Off");
                }
            }
        }
        else
            toast("Please connect to the server!");
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onPause() {
        super.onPause();

    }
}
