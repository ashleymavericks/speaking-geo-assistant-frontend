package in.jhalwabois.geogpassist.utils;
 
/** 
 * Created by Hitesh on 12-07-2016. 
 */ 
public interface ConversationCallback {
 
    public void onSuccess(String result);
 
    public void onCompletion(); 
 
    public void onErrorOccured(String errorMessage);
 
} 