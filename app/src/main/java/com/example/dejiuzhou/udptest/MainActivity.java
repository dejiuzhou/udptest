package com.example.dejiuzhou.udptest;


import android.app.Activity;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.app.AlertDialog;
import android.content.DialogInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;


public class MainActivity extends AppCompatActivity {

    public static TextView show;
    public static Button mannual_btn;
    public static Button auto_btn;

    private static final int MAX_DATA_PACKET_LENGTH = 8192;
    byte[] buffer = new byte[MAX_DATA_PACKET_LENGTH];

    public int revNums = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        show = (TextView)findViewById(R.id.textView);
        mannual_btn = (Button)findViewById(R.id.button);
        auto_btn = (Button)findViewById(R.id.button2);
        mannual_btn.setBackgroundColor(0xff00ff00);
        auto_btn.setBackgroundColor(0xff00ff00);

        mannual_btn.setOnClickListener(listener);
        auto_btn.setOnClickListener(listener);

        new WorkThread((int)9123, (byte)0x23).start();

    }


    private View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
           if (v == auto_btn ) {
               //auto_btn.setEnabled(false);
               //mannual_btn.setEnabled(true);
               AlertDialog dialog   = new AlertDialog.Builder(MainActivity.this).create();
               dialog.setIcon(R.mipmap.ic_launcher);
               dialog .setTitle("提示" ) ;
               dialog .setMessage("确认切换到自动模式？" ) ;
               dialog .setButton(DialogInterface.BUTTON_POSITIVE, "确定", new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int which) {
                       auto_btn.setBackgroundColor(0xffff0000);
                       // 新开一个线程 发送 udp 多播
                       new udpBroadCast((byte)0x0a, (short)1).start();
                   }
               });
               dialog .setButton(DialogInterface.BUTTON_NEGATIVE, "取消", new DialogInterface.OnClickListener() {
                   @Override
                   public void onClick(DialogInterface dialog, int which) {

                   }
               });
               dialog .show();

           }
           else if(v == mannual_btn)
           {
               //auto_btn.setEnabled(true);
               //mannual_btn.setEnabled(false);
               mannual_btn.setBackgroundColor(0xffff0000);
            /* 新开一个线程 发送 udp 多播 */
               new udpBroadCast((byte)0x0a, (short)0).start();
           }
        }
    };

    /* 发送udp多播 */
    private  class udpBroadCast extends Thread {
        MulticastSocket sender = null;
        DatagramPacket dj = null;
        InetAddress group = null;

       // int[] str = {12,1212,123};
        byte[] data = new byte[13];
        //{0x28, 0x28, 0x28, 0xf6, 0x00, 0x03, 0x04, 0x00, 0x00,0x00,0x00,0x00, 0xf8};
        public udpBroadCast(byte cmd, short shValue) {
            //data = dataString.getBytes();
            data[0] = (byte)0x28;
            data[1] = (byte)0x28;
            data[2] = (byte)0x28;
            data[3] = (byte)0xf6;
            data[4] = (byte)0x00;
            data[5] = (byte)0x23;
            data[6] = (byte)0x04;
            data[7] = (byte)0x00;
            data[8] = (byte)cmd;
            data[9] = (byte)0x00;//命令方向
            data[10] = (byte)shValue;
            data[11] = (byte)(shValue >> 8);
            data[12] = BCC_CheckSum(data, 12);

        }

        @Override
        public void run() {
            try {
                sender = new MulticastSocket();
                group = InetAddress.getByName("224.0.0.1");
                dj = new DatagramPacket(data,data.length,group,9123);
                sender.send(dj);
                //Thread.sleep(100);
                sender.close();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class WorkThread extends Thread {
        MulticastSocket mSocket = null;
        DatagramPacket dj = null;
        InetAddress group = null;
        byte msgid;
        byte[] buffer = new byte[4096];

        public WorkThread(int iPort, byte msg){
            try {
                msgid = msg;
                group = InetAddress.getByName("224.0.0.1");
                mSocket = new MulticastSocket(iPort);
                // mSocket.setTimeToLive(100);
                mSocket.setLoopbackMode(false);
                mSocket.joinGroup(group);
                dj = new DatagramPacket(buffer, 4096);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        @Override
        public void run() {


            while (true) {
                try {
                    mSocket.receive(dj);

                    if(buffer[3] != (byte)0xf6 || buffer[2] != (byte)0x28 || buffer[1] != (byte)0x28 || buffer[0] != (byte)0x28) continue;

                    byte byCheck = BCC_CheckSum(buffer, dj.getLength()-1);
                    if(byCheck != buffer[dj.getLength()-1]) continue;

                    if(buffer[5] == msgid) {
                        revNums++;
                        //模式切换
                        if(buffer[8] == (byte)0x0a){
                            if(buffer[9] == (byte)1){
                                if(buffer[10] == 0){
                                    mannual_btn.setBackgroundColor(0xff00ff00);
                                    show.setText("Cmd Nums:"+String.valueOf(revNums)+"<-- response manual");
                                }else{
                                    auto_btn.setBackgroundColor(0xff00ff00);
                                    show.setText("Cmd Nums:"+String.valueOf(revNums)+"<-- response auto");
                                }
                            }else{
                                if(buffer[10] == 0){
                                    show.setText("Cmd Nums:"+String.valueOf(revNums)+"--> request  manual");
                                }else{
                                    show.setText("Cmd Nums:"+String.valueOf(revNums)+"--> request  auto");
                                }

                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

        }
    }

    byte BCC_CheckSum(byte buf[], int iLen) {
        byte checksum = 0;
        int i;

        for (i = 0; i < iLen; i++)
            checksum ^= buf[i];

        return checksum;
    }

        @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
