package comm.rep.socketsnake;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;

public class MainActivity extends AppCompatActivity {
  
  @RequiresApi(api = Build.VERSION_CODES.O)
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    Switch bridgeSwitch = (Switch) findViewById(R.id.bridgeSwitch);
    bridgeSwitch.setOnCheckedChangeListener((CompoundButton bv, boolean isChecked) -> {
      System.out.println("Bridge on: " + isChecked);
      
      Bridge.setTryStartStop(isChecked);
    });
    
  }
}