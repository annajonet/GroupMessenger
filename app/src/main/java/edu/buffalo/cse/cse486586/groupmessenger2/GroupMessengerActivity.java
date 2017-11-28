package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Exchanger;

public class GroupMessengerActivity extends Activity {
    static ArrayList<String> REMOTE_PORT = new ArrayList<String>(Arrays.asList(Constants.PORT1, Constants.PORT2, Constants.PORT3 , Constants.PORT4, Constants.PORT5));
    HashMap<String,Integer> lastReceived = new HashMap<String, Integer>();//stores the last received msg number from all devices
    int lastSendNum = -1;   //stores the last send message number
    int proposed = -1;  //stores the last proposed value
    HashMap<Integer,String> portSendMsg = new HashMap<Integer,String>();
    HashMap<Integer,String> buffers = new HashMap<Integer,String>();
    TreeMap<Double, String> selectedSequence = new TreeMap<Double, String>();

    HashMap<String,ArrayList<String>> processMsgs = new HashMap<String,ArrayList<String>>();
    private final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try{

            ServerSocket serverSocket = new ServerSocket(Constants.SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        }catch (IOException e) {

            Log.e("error", e.getMessage());
            return;
        }
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        final EditText editText = (EditText) findViewById(R.id.editText1);

        findViewById(R.id.button4).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        String msg = editText.getText().toString();
                        editText.setText("");
                        tv.append("\t" + msg);
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                    }
                }
        );
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        private void agreedMethod(Message message){
            String strReceived = message.getMsg().trim();
            int agreedNum = message.getNumber();
            double portVal = ((((double) message.getProposedPort())/2) - 5554)/10;
            Double msgCount = agreedNum + portVal;
            selectedSequence.put(msgCount, strReceived);

            Set set = selectedSequence.entrySet();
            Iterator iter = set.iterator();
            int i = 0;
            while(iter.hasNext()){
                Map.Entry cur = (Map.Entry) iter.next();
                ContentValues cv = new ContentValues();
                cv.put("key" , i);
                cv.put("value" ,cur.getValue().toString());
                getContentResolver().insert(mUri, cv);
                i++;
            }

            if(agreedNum > proposed){
                proposed = agreedNum;
            }
            return;

        }

        private String proposeMethod(Message message){
            String proposedOut = "";
            int num = message.getNumber();
            int nextRecdNum = lastReceived.get(message.getSender()) == null?0:lastReceived.get(message.getSender())+1;
            if(num ==  nextRecdNum){
                proposedOut = num+"|"+(++proposed)+"|"+message.getMsg();
                lastReceived.put(message.getSender(),num);
            }else if(num > nextRecdNum){
                buffers.put(num,message.getMsg());
            }
            int lastVal = lastReceived.get(message.getSender()) == null?0:lastReceived.get(message.getSender());
            while(buffers != null && buffers.get(lastVal+1) != null){
                if(!proposedOut.isEmpty()){
                    proposedOut += "<";
                }
                proposedOut += (lastVal+1)+"|"+(++proposed)+"|"+buffers.get(lastVal+1);
                buffers.remove(lastVal+1);
                lastReceived.put(message.getSender(),lastVal+1);
                lastVal++;
            }
            return proposedOut;
        }

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            while(true){
                try{
                    Socket clSocket = serverSocket.accept();
                    ObjectInputStream in = new ObjectInputStream(clSocket.getInputStream());
                    PrintWriter pr = new PrintWriter(clSocket.getOutputStream());
                    Message message = (Message) in.readObject();
                    String proposedOut = "";
                    if(message != null){
                        if(message.isAgreed()){         //agreed value has come
                            agreedMethod(message);
                            pr.println("Gotcha!");
                        }
                        else{          //1st time request, so propose a value
                            proposedOut = proposeMethod(message);
                            pr.println(proposedOut);
                        }
                    }

                    pr.flush();
                    pr.close();
                    in.close();

                    clSocket.close();
                }
                catch (Exception e){
                    Log.e("error","on server"+e);
                }

            }

        }


        protected void onProgressUpdate(String...strings) {
            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        private String sendMessageToServer(Message msgToSend, String remotePort){
            String ack = "";
            try{
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(remotePort));
                socket.setSoTimeout(500);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out.writeObject(msgToSend);
                ack = br.readLine();
                out.close();
                br.close();
                socket.close();
            }
            catch (SocketTimeoutException ex){
                Log.e("error", "Exception : "+ ex.getMessage());
            }
            catch (SocketException ex){
                Log.e("error", "Exception : "+ ex.getMessage());
            }catch (IOException ex){
                Log.e("error", "Exception : "+ ex.getMessage());
            }
            return ack;
        }
        @Override
        protected Void doInBackground(String... msgs) {
            try {
                int nextValSend = lastSendNum+1;
                Iterator<String> iter = REMOTE_PORT.iterator();
                Message message = new Message(msgs[0],msgs[1],nextValSend,false);
                while(iter.hasNext()){
                    String remotePort = iter.next();
                    try{
                        String ack = sendMessageToServer(message, remotePort);
                        if(ack != ""){
                            String[] responses = ack.split("<");
                            for(String resp: responses){
                                String[] msgValues = resp.split("\\|");
                                ArrayList<String> curr = processMsgs.get(msgValues[0])==null?new ArrayList<String>():processMsgs.get(msgValues[0]);
                                curr.add(msgValues[1]+"|"+remotePort);  //save the proposed value and the proposed port
                                processMsgs.put(msgValues[0], curr); //key - the number send by current port, value - proposed value|proposed port
                            }
                        }
                    }
                    catch (NullPointerException ex){
                        iter.remove();
                    }

                }
                lastSendNum= nextValSend;
                portSendMsg.put(nextValSend, msgs[0]);
                Iterator<Map.Entry<String, ArrayList<String>>> iterator = processMsgs.entrySet().iterator();
                while(iterator.hasNext()){
                    Map.Entry<String, ArrayList<String>> hashentry = iterator.next();
                    ArrayList<String> sendItem = hashentry.getValue();
                    if(sendItem.size() == REMOTE_PORT.size()){       //it means all 5 devices proposed a value
                        int finalValue = -1;
                        int finalPort = -1;
                        for(String proposal: sendItem){
                            String[] propValues = proposal.split("\\|");
                            if(Integer.parseInt(propValues[0]) > finalValue){
                                finalValue = Integer.parseInt(propValues[0]);
                                finalPort = Integer.parseInt(propValues[1]);
                            }else if(Integer.parseInt(propValues[0]) == finalValue){    //give preference to the lesser port num
                                finalPort = Integer.parseInt(propValues[1]) < finalPort?Integer.parseInt(propValues[1]):finalPort;
                            }
                        }
                        //Now send the agreed value to all ports
                        Iterator<String> portIter = REMOTE_PORT.iterator();
                        while(portIter.hasNext()){
                            String remotePort = portIter.next();
                            Message sendMessage = new Message(portSendMsg.get(Integer.parseInt(hashentry.getKey())),msgs[1],true, finalValue,finalPort);
                            try{
                                sendMessageToServer(sendMessage, remotePort);
                            }
                            catch (NullPointerException ex){
                                portIter.remove();
                            }

                        }
                        iterator.remove();

                    }
                }
            }
            catch (Exception ex){
                Log.e("error","other exception: "+ ex);
            }

            return null;
        }

    }
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
