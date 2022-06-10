package comm.rep.socketsnake.AsyncJsonSocket;

public class AsyncJsonSocketEvent {
  public AsyncJsonSocketEventType type;
  public String messageString;
  public Exception exception;
  public String exceptionString;
  
  public AsyncJsonSocketEvent (AsyncJsonSocketEventType type) {
    this.type = type;
  }
  
}
