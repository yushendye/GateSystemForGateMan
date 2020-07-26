package com.smgdevs.chinmay.gatesystemforgateman;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

public class MessageChecker extends BroadcastReceiver {
    private static MessageListener mListener;
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle data = intent.getExtras();
        Object[] pdus = (Object[]) data.get("pdus");
        for(int i=0; i<pdus.length; i++){
            SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdus[i]);
            String message = smsMessage.getMessageBody();
            mListener.messageReceived(message);
        }
    }
    public static void bindListener(MessageListener listener){
        mListener = listener;
    }
}
