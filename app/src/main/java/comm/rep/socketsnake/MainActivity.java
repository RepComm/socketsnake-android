package comm.rep.socketsnake;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.google.gson.Gson;

import comm.rep.socketsnake.AsyncJsonSocket.AsyncJsonSocket;
import comm.rep.socketsnake.AsyncJsonSocket.AsyncJsonSocketEvent;

class JsonMessage {
  public String msg;
  public JsonMessage () {}
}

public class MainActivity extends AppCompatActivity {
  
  public static Handler MAIN_LOOPER_HANDLER_SINGLETON = null;
  
  public static Handler getMainLooperHandler () {
    if (MAIN_LOOPER_HANDLER_SINGLETON == null) MAIN_LOOPER_HANDLER_SINGLETON = new Handler(Looper.getMainLooper());
    return MAIN_LOOPER_HANDLER_SINGLETON;
    
//    handler.postDelayed(new Runnable() {
//      @Override
//      public void run() {
//        //Do something after 100ms
//      }
//    }, 100);
  }
  
  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
  
    TextView inputPort = (TextView) findViewById(R.id.inputPort);
    inputPort.setFilters(new InputFilter[]{ new InputFilterMinMax("1", "65535")});
    
    TextView inputIp = (TextView) findViewById(R.id.inputIp);
    
    AsyncJsonSocket bridgeSocket = new AsyncJsonSocket();
    
    Switch bridgeSwitch = (Switch) findViewById(R.id.bridgeSwitch);
    bridgeSwitch.setOnCheckedChangeListener((CompoundButton bv, boolean isChecked) -> {
    
      if (isChecked) {
        String host = inputIp.getText().toString();
        int port = 10209;
        try {
          port = Integer.parseInt( inputPort.getText().toString() );
        } catch (Exception ex) {
          port = 10209;
        }
        
        if (host == null || host.isEmpty()) {
          host = "localhost";
          inputIp.setText(host);
        }
        if (port < 0 || port > 65535) {
          port = 10209;
          inputPort.setText(Integer.toString( port ));
        }
        
        bridgeSocket.connect(host, port);
      } else {
        bridgeSocket.disconnect();
      }
    });
    
    boolean processEvents = true;
    
    Gson gson = new Gson();
    
    Thread evtThread = new Thread(()->{
      bridgeSocket.listen((AsyncJsonSocketEvent evt)->{
        switch(evt.type) {
          case DISCONNECTED:
            System.out.println("Bridge disconnected");
            
            getMainLooperHandler().postDelayed(()->{
              bridgeSwitch.setChecked(false);
            }, 250);
            
            break;
          case CONNECTED:
            System.out.println("Bridge connected");
            break;
          case MESSAGE:
//            System.out.println("Bridge msg: " + evt.messageString);
            JsonMessage json = gson.fromJson(evt.messageString, JsonMessage.class);
            
            System.out.println("Msg:" + json.msg);
            break;
          case ERROR:
            System.out.println("Bridge error: " + evt.exception.toString());
            if (evt.exceptionString != null) System.out.println("... " + evt.exceptionString);
            break;
          default:
            break;
        }
      });
      
      while (processEvents) {
        bridgeSocket.pollEvents();
      }
      
    });
    
    evtThread.start();
    
  }
}