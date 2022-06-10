package comm.rep.socketsnake;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;

import comm.rep.socketsnake.AsyncJsonSocket.AsyncJsonSocket;
import comm.rep.socketsnake.AsyncJsonSocket.AsyncJsonSocketEvent;

public class MainActivity extends AppCompatActivity {
  
  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    AsyncJsonSocket bridgeSocket = new AsyncJsonSocket();
    
    Switch bridgeSwitch = (Switch) findViewById(R.id.bridgeSwitch);
    bridgeSwitch.setOnCheckedChangeListener((CompoundButton bv, boolean isChecked) -> {
    
      if (isChecked) {
        bridgeSocket.connect("localhost", 10209);
      } else {
        bridgeSocket.disconnect();
      }
    });
    
    boolean processEvents = true;
    
    Thread evtThread = new Thread(()->{
      bridgeSocket.listen((AsyncJsonSocketEvent evt)->{
        switch(evt.type) {
          case DISCONNECTED:
            System.out.println("Bridge disconnected");
            break;
          case CONNECTED:
            System.out.println("Bridge connected");
            break;
          case MESSAGE:
            System.out.println("Bridge msg: " + evt.messageString);
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