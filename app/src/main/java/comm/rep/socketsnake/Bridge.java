package comm.rep.socketsnake;

import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Future;

@RequiresApi(api = Build.VERSION_CODES.O)
public class Bridge {
  static AsynchronousSocketChannel socket;
  
  static void setTryStartStop (boolean start) {
    if (start) {
      Bridge.tryStart();
    } else {
      Bridge.tryStop();
    }
  }
  
  static boolean canStart () {
    return Bridge.socket == null;
  }
  
  static void tryStop () {
    if (Bridge.socket == null) return;
    
    try {
      Bridge.socket.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    Bridge.socket = null;
  }
  
  static void tryStart () {
    if (Bridge.canStart()) Bridge.start();
  }
  
  private static void start () {
    System.out.println("Trying to connect");
    try {
      Bridge.socket = AsynchronousSocketChannel.open();
    } catch (IOException e) {
      e.printStackTrace();
    }
    Bridge.socket.connect(new InetSocketAddress(10209), null,
        new CompletionHandler() {
          @Override
          public void completed(Object channel, Object o) {
            System.out.println("ASYNC: Connected");
          }
          @Override
          public void failed(Throwable throwable, Object o) {
            System.out.println("ASYNC: Could not connect");
          }
        }
    );
  }
}
