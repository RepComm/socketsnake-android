package comm.rep.socketsnake.AsyncJsonSocket;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;

enum AsyncJsonSocketConnectionState {
  CONNECT_DESIRED,
  CONNECTING,
  CONNECTED,
  CLOSED
}

enum BracketType {
  SQUARE,
  CURLY
}

@SuppressWarnings("rawtypes")
public class AsyncJsonSocket {
  
  List<AsyncJsonSocketEventListener> listeners;
  private final ConcurrentLinkedDeque<AsyncJsonSocketEvent> unconsumedEvents;
  private final ReentrantLock listenerLock;
  
  public AsyncJsonSocketConnectionState state;
  
  Thread socketReadThread;
  Runnable socketReadTask;
  BufferedReader i;
  
  //contains fragment of text read from inside a packet
  char[] iBuffer;
  int iBufferWidth;
  boolean escapeNextChar;
  boolean doubleQuotesActive;
  boolean singleQuotesActive;
  //contains full string, possibly from multiple packets wide
  StringBuilder iBuilder;
  
  int openBracketCount;
  int closeBracketCount;
  BracketType firstBracketType;
  
  Thread socketWriteThread;
  Runnable socketWriteTask;
  OutputStream o;
  
  boolean networkThreadsShouldRun;
  
  InetSocketAddress socketAddress;
  Socket socket;
  
  int connectMaxAttempts;
  int connectAttempts;
  
  private String host;
  private int port;
  
  public AsyncJsonSocket () {
    this.listeners = new ArrayList();
    this.unconsumedEvents = new ConcurrentLinkedDeque();
    this.listenerLock = new ReentrantLock();
  
    this.state = AsyncJsonSocketConnectionState.CLOSED;
    this.networkThreadsShouldRun = true;
  
    this.connectMaxAttempts = 10;
    this.connectAttempts = 0;
  
    this.iBufferWidth = 2048;
    this.iBuffer = new char[this.iBufferWidth];
    this.doubleQuotesActive = false;
    this.singleQuotesActive = false;
    this.escapeNextChar = false;
    this.socket = null;
    this.openBracketCount = 0;
    this.closeBracketCount = 0;
    this.firstBracketType = null;
  
    this.iBuilder = new StringBuilder();
  
    this.socketReadTask = () -> {
  
      //kill switch
      while (this.networkThreadsShouldRun) {
  
        //if not connected
        switch (this.state) {
          case CONNECT_DESIRED:
            this.attemptConnect();
            break;
          case CONNECTING:
            //just wait
            break;
          case CONNECTED:
            this.processRead();
            break;
          case CLOSED:
            //wait until told to connect again
          default:
            break;
        }
      }
    };
  
    
    this.socketReadThread = new Thread(this.socketReadTask);
    this.socketReadThread.start();
  
    this.socketWriteTask = ()->{
  
      //kill switch
      while (this.networkThreadsShouldRun) {
    
        //if not connected
        switch (this.state) {
          case CONNECTED:
            this.processWrite();
            break;
          default:
            break;
        }
      }
    };
    this.socketWriteThread = new Thread(this.socketReadTask);
//    this.socketWriteThread.start();
  
  
  }
  
  private void attemptConnect () {
    //check max attempts
    if (this.connectAttempts > this.connectMaxAttempts) {
      this.state = AsyncJsonSocketConnectionState.CLOSED;
      return;
    }
    
    this.ensureDisconnected();
    this.state = AsyncJsonSocketConnectionState.CONNECTED;
    
    //try to connect
    this.connectAttempts++;
    this.socket = new Socket();
    this.socketAddress = new InetSocketAddress(this.host, this.port);
  
    try {
      this.socket.setSoTimeout(5000);
      this.socket.connect(this.socketAddress);
  
      this.socket.setSoTimeout(1);
      this.i = new BufferedReader(
          new InputStreamReader(
              this.socket.getInputStream()
          )
      );
  
      this.o = this.socket.getOutputStream();
  
    } catch (Exception e) {
      this.onError(e);
//      e.printStackTrace();
      this.state = AsyncJsonSocketConnectionState.CONNECT_DESIRED;
    }
    
    this.state = AsyncJsonSocketConnectionState.CONNECTED;
    
    this.onOpen();
    this.connectAttempts = 0;
  }
  private void processRead () {
    try {
      int count = this.i.read(this.iBuffer);
      char ch;
//      System.out.println("Read " + count + " chars: " + String.valueOf(this.iBuffer, 0, count));
  
      for (int i = 0; i < count; i++) {
        if (this.escapeNextChar) {
          this.escapeNextChar = false;
          continue;
        }
    
        ch = this.iBuffer[i];
    
        switch (ch) {
          case '\\':
            this.escapeNextChar = true;
            continue;
      
          case '"':
            if (this.singleQuotesActive) continue;
            this.doubleQuotesActive = !this.doubleQuotesActive;
            continue;
      
          case '\'':
            if (this.doubleQuotesActive) continue;
            this.singleQuotesActive = !this.singleQuotesActive;
            continue;
      
          case '{':
            if (this.doubleQuotesActive || this.singleQuotesActive) continue;
            if (this.openBracketCount < 1) {
              this.firstBracketType = BracketType.CURLY;
            }
            if (this.firstBracketType == BracketType.CURLY) this.openBracketCount++;
            continue;
            
          case '[':
            if (this.doubleQuotesActive || this.singleQuotesActive) continue;
            if (this.openBracketCount < 1) {
              this.firstBracketType = BracketType.SQUARE;
            }
            if (this.firstBracketType == BracketType.SQUARE) this.openBracketCount++;
            continue;
      
          case '}':
            if (this.doubleQuotesActive || this.singleQuotesActive) continue;
            if (this.firstBracketType == BracketType.CURLY) this.closeBracketCount++;
            break;
            
          case ']':
            if (this.doubleQuotesActive || this.singleQuotesActive) continue;
            if (this.firstBracketType == BracketType.SQUARE) this.closeBracketCount++;
            break;
      
          default:
            continue;
        }
    
//        System.out.println("Open Brackets:"  + this.openBracketCount + " Close Brackets: " + this.closeBracketCount);
        
        //if we've found matching brackets, flush the data as a message
        if (this.openBracketCount <= this.closeBracketCount) {
          this.iBuilder.append(this.iBuffer, 0, i+1);
  
          this.onMessage(this.iBuilder.toString());
          this.iBuilder.setLength(0);
          this.openBracketCount = 0;
          this.closeBracketCount = 0;
          this.escapeNextChar = false;
          this.singleQuotesActive = false;
          this.doubleQuotesActive = false;
          this.firstBracketType = null;
          
          if (i < count - 1) {
            int contIndex = i;
  
            int width = this.iBufferWidth - contIndex;
  
            //append whats left to the next JSON message for processing later
            this.iBuilder.append(this.iBuffer, contIndex, width);
          }
  
        } else {
          //otherwise, this JSON is probably spread across multiple packets
          //so keep appending
  
          this.iBuilder.append(
              this.iBuffer,
              0,
              this.iBufferWidth
          );
        }
      }
    } catch (SocketTimeoutException ex) {
      //read timeout is ok
    } catch (SocketException ex) {
      if (this.socket == null || this.socket.isClosed()) {
        this.ensureDisconnected();
      }
    } catch (EOFException ex) {
      this.onError(ex);
      this.ensureDisconnected();
      
    } catch (IOException e) {
      this.onError(e);
      e.printStackTrace();
    }
  }
  private void processWrite () {
  
  }
  
  public void connect (String host, int port) {
    if (this.state != AsyncJsonSocketConnectionState.CLOSED) {
      
      return;
    }
    this.host = host;
    this.port = port;
    this.connectAttempts = 0;
    
    this.state = AsyncJsonSocketConnectionState.CONNECT_DESIRED;
  }
  
  public void disconnect () {
    this.connectAttempts = 0;
    this.ensureDisconnected();
  }
  void ensureDisconnected () {
    this.state = AsyncJsonSocketConnectionState.CLOSED;
    
    boolean performedDisconnect = false;
    
    if (this.socket != null) {
      if (!this.socket.isClosed()) try {
        performedDisconnect = true; this.socket.close();
      } catch (Exception ex) {
        //We tried our best
      }
      
    }
    this.socket = null;
    
    //fire an event
    if (performedDisconnect) this.onClose();
  }
  
  public AsyncJsonSocket listen (AsyncJsonSocketEventListener listener) {
    this.listenerLock.lock();
    this.listeners.add(listener);
    this.listenerLock.unlock();
    return this;
  }
  public AsyncJsonSocket deafen (AsyncJsonSocketEventListener listener) {
    this.listenerLock.lock();
    this.listeners.remove(listener);
    this.listenerLock.unlock();
    return this;
  }
  
  public AsyncJsonSocketEvent createEvent (AsyncJsonSocketEventType type) {
    return new AsyncJsonSocketEvent(type);
  }
  
  /**Adds the event to unconsumedEvents, called internally
   */
  public AsyncJsonSocket pushEvent (AsyncJsonSocketEvent evt) {
    this.unconsumedEvents.addLast(evt);
    return this;
  }
  
  /**Calls every event listener, passing each the event
   *
   * Be sure to call this from whatever thread you intend your event listeners to be executed from
   *
   * @param evt
   * @return
   */
  public AsyncJsonSocket dispatchEvent (AsyncJsonSocketEvent evt) {
    this.listenerLock.lock();
    
    for (AsyncJsonSocketEventListener listener : this.listeners) {
      listener.onEvent(evt);
    }
    
    this.listenerLock.unlock();
    return this;
  }
  
  /**Loops through unconsumed events and calls dispatchEvent on each one, consuming it
   *
   * This implementation utilizes ConcurrentLinkedDeque, which is thread safe.
   * You may run this method from another thread, it will be the thread the WSEventListeners are executed in.
   * @return
   */
  public AsyncJsonSocket pollEvents () {
    AsyncJsonSocketEvent evt;
    
    while ( (evt = this.unconsumedEvents.pollFirst()) != null) {
      this.dispatchEvent(evt);
    }
    
    return this;
  }
  
  public void onOpen() {
    
    //----Here we create an event object and thread-safely push it onto a queue
    //----It will be consumed by event listeners during pollEvents()
    AsyncJsonSocketEvent evt = this.createEvent(AsyncJsonSocketEventType.CONNECTED);
    this.pushEvent(evt);
  }
  public void onClose() {
    
    //----Here we create an event object and thread-safely push it onto a queue
    //----It will be consumed by event listeners during pollEvents()
    AsyncJsonSocketEvent evt = this.createEvent(AsyncJsonSocketEventType.DISCONNECTED);
    this.pushEvent(evt);
  }
  public void onMessage(String message) {
    
    //----Here we create an event object and thread-safely push it onto a queue
    //----It will be consumed by event listeners during pollEvents()
    AsyncJsonSocketEvent evt = this.createEvent(AsyncJsonSocketEventType.MESSAGE);
    evt.messageString = message;
    this.pushEvent(evt);
  }
  public void onError(Exception ex) {
    
    //----Here we create an event object and thread-safely push it onto a queue
    //----It will be consumed by event listeners during pollEvents()
    AsyncJsonSocketEvent evt = this.createEvent(AsyncJsonSocketEventType.ERROR);
    evt.exception = ex;
    this.pushEvent(evt);
  }
}
