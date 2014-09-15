package ru.slybeaver.SocketIO;


import android.util.Base64;
import android.util.Log;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;


public abstract class SocketIO {

    private Socket socket;
    private PrintWriter out = null;

    private String serverAddress = "192.168.0.1";
    private  int serverPort = 9000;
    private Boolean SocketConnected=false;
    private String socketIOPatch="/socket.io/websocket/";

   //Events
    public abstract void onConnect(); 
    public abstract void onDisconnect(); 
    public abstract void onMessage(String message); 



    public void Connect(String Address, int port) {
        Connect(Address, port, socketIOPatch);
    }

    public void Connect(String Address, int port, String socket_path){
        serverAddress = Address;
        serverPort = port;
        socketIOPatch=socket_path;
        Thread thread = new Thread(null, SocketConnect,
                "Background");
        thread.start();
    }



    private void disconnect(){
        if (SocketConnected){
            SocketConnected=false;
            onDisconnect();
        }
    }


    //Connecting to server
    private Runnable SocketConnect = new Runnable() {
        public void run() {
            try {
                socket = new Socket(serverAddress, serverPort);
                Thread slisten = new Thread(null,SocketListen,"SocketListen1");
                if (socket.isConnected()){
                    out = new PrintWriter(socket.getOutputStream(), false);
                    SocketConnected=true;
                    slisten.start(); //Начинаем слушать ответы от сокета
                    out.print("GET "+socketIOPatch+"?transport=websocket HTTP/1.1\r\n");
                    out.print("Host: http://"+serverAddress+":"+serverPort+"\r\n");
                    out.print("Upgrade: websocket\r\n");
                    out.print("Connection: Upgrade\r\n");
                    out.print("Sec-WebSocket-Key: " + generateSocketKey() +"\r\n");
                    out.print("Sec-WebSocket-Version: 13\r\n");
                    out.print("Sec-WebSocket-Protocol: websocket\r\n");
                    out.print("Origin: *\r\n\r\n");
                    out.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };



    //Socket key generation
    private String generateSocketKey() {
        int c = 0;
        String tmp = "";
        String subtmp = "";
        Random r = new Random();
        // int i1 = r.nextInt(80 - 65) + 65;
        while (c++ * 16 < 16) {
            subtmp = String.valueOf(r.nextInt());
            tmp += md5("dsd");
        }
        tmp = tmp.substring(0, 16);
        byte[] data = new byte[0];
        try {
            data = tmp.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String base64 = Base64.encodeToString(data, Base64.DEFAULT);
        return base64.substring(0,16);
    }

    //MD5 generation
    private static String md5(String s) {
        try {

            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < messageDigest.length; i++)
                hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }


    //Answer listener
    private Runnable SocketListen = new Runnable() {
        @Override
        public void run() {
            Log.d("","Listener Runned");
            try {
                BufferedReader income = null;
                income = new BufferedReader(new InputStreamReader(socket.getInputStream())); //Reading text data
                DataInputStream dis = new DataInputStream(socket.getInputStream()); // Reading binary data
                String line = null;

                while ((line = income.readLine()) != null) {
                    Log.d("========Server Ansver=====", line);
                    if (line.matches("Sec-WebSocket-Protocol: websocket")) {onConnect(); break;}
                    if (!SocketConnected){disconnect();return;}
                }

                line=null;
                income=null;
                byte b = 0;
                byte[] frameHeader = new byte[10]; // Header processing 
                int byteposition=0; // Position package of byte for write
                int framebytes=0; // Frame length
                int startreadbytes=0; //Package to be reckoned with frames
                byte framelength = 0; //Payload length
                byte[] payload=null; //Payload data
                byte[] lenghtlen = null;
                //Read answer
                while (SocketConnected) {
                    b = dis.readByte();
                    if (framebytes==0){frameHeader[byteposition]=b;}
                    if (byteposition==0){ //Process a first byte
                        if (b==129){
                            //"Frame is text"
                            /* TODO */
                            //Ping-Pong, binary
                        }
                    } else if (byteposition==1) { //Process second byte
                        if (b<126){framebytes=0x00 << 24 | b & 0xff; payload=new byte[framebytes];byteposition++;continue;} //small package (<125 bytes)
                        if (b==126){framelength=4;} //Middle package, >= 126 <= 65535 bytes
                        if (b==127){framelength=10;} //Big package > 65535 bytes
                    }




                    if (framebytes==0){ //Process frame length

                        if (byteposition==framelength){


                            lenghtlen = new byte[10];

                            for (int i=2; i<byteposition; i++) {
                                framebytes = ( framebytes << 8 ) + (int) frameHeader[i];
                                lenghtlen[i-2]=frameHeader[i];
                            }
                            payload=new byte[framebytes];

                        }
                    }

                    if (framebytes>0){ //Write package at row

                        payload[startreadbytes]=b;
                        startreadbytes++;
                        if (startreadbytes==framebytes){// Package complete
                            String alltext=new String(payload);
                            Pattern pt = Pattern.compile("\\[\"message\",\"(.*)\"\\]");
                            Matcher mt = pt.matcher(alltext);
                            if (mt.find()) { //Message finding
                                if (mt.group().length()>1) {

                                    onMessage(mt.group(1).replaceAll("\\\\\"", "\""));


                                }
                            }

                            byteposition=0;
                            framebytes=0;
                            startreadbytes=0;
                            framelength=0;
                            payload=null;
                            frameHeader=new byte[10];
                            continue;

                        }
                    }



                    byteposition++;
                    if (!SocketConnected){disconnect();return;}
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    };

    //Hybi10 encoder (RFC6455 encoder)
    private byte[] Hybi10Encoder(String message) {
        byte[] rawData = new byte[0]; 
        try {
            rawData = message.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        int frameCount = 0; 
        byte[] frame = new byte[10]; 
        frame[0] = (byte) 129; 
        if (rawData.length <= 125) { 
            frame[1] = (byte) (rawData.length);
            frameCount = 2;

        } else if (rawData.length >= 126 && rawData.length <= 65535) { 
            frame[1] = (byte) 126;
            int len = rawData.length;
            frame[2] = (byte) ((len >> 8) & (byte) 255);
            frame[3] = (byte) (len & (byte) 255);
            frameCount = 4;

        } else { 
            frame[1] = (byte) 127;
            int len = rawData.length;
            frame[2] = (byte) ((len >> 56) & (byte) 255);
            frame[3] = (byte) ((len >> 48) & (byte) 255);
            frame[4] = (byte) ((len >> 40) & (byte) 255);
            frame[5] = (byte) ((len >> 32) & (byte) 255);
            frame[6] = (byte) ((len >> 24) & (byte) 255);
            frame[7] = (byte) ((len >> 16) & (byte) 255);
            frame[8] = (byte) ((len >> 8) & (byte) 255);
            frame[9] = (byte) (len & (byte) 255);
            frameCount = 10;

        }

        int bLength = frameCount + rawData.length;

        byte[] reply = new byte[bLength];

        int bLim = 0;
        for (int i = 0; i < frameCount; i++) {
            reply[bLim] = frame[i];
            bLim++;
        }

        byte[] mask = new byte[4]; // Маска
        Random rnd = new Random(255);

        for (int i = 0; i < rawData.length; i++) {
            //reply[bLim]=(byte)((rawData[i] ^ mask[i % 4]));
            reply[bLim] = (rawData[i]);
            bLim++;
        }
        return reply;
    }






    public void send(String payload) {
        try {
            DataOutputStream bos = new DataOutputStream(socket.getOutputStream());
            String s = "42[\"message\", \""+payload+"\"]";
            bos.write(Hybi10Encoder(s));
            bos.flush();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

}
