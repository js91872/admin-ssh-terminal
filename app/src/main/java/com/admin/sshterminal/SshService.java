package com.admin.sshterminal;
import android.app.*;
import android.content.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import com.jcraft.jsch.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
public class SshService extends Service {
 public interface Listener { void output(String s); }
 public static final String START="start_ssh", STOP="stop_ssh";
 public class LocalBinder extends Binder { SshService service(){return SshService.this;} }
 private final IBinder binder=new LocalBinder();
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final ExecutorService read=Executors.newSingleThreadExecutor(),write=Executors.newSingleThreadExecutor();
 private final StringBuilder log=new StringBuilder();
 private Listener listener;
 private Session session; private ChannelShell shell; private volatile OutputStream stream;
 private volatile boolean connected=false,closing=false,starting=false;
 @Override public void onCreate(){super.onCreate();NotificationManager mgr=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(Build.VERSION.SDK_INT>=26)mgr.createNotificationChannel(new NotificationChannel("ssh-active","SSH active session",NotificationManager.IMPORTANCE_LOW));startForeground(9,notification("Connecting…"));}
 private Notification notification(String status){
  Intent open=new Intent(this,TerminalActivity.class);open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
  PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  Intent stop=new Intent(this,SshService.class).setAction(STOP);
  PendingIntent stopPending=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  return new NotificationCompat.Builder(this,"ssh-active").setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("VPS SSH Terminal").setContentText(status).setOngoing(true).setContentIntent(pending).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Disconnect",stopPending).build();
 }
 private void notifyStatus(String s){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(9,notification(s));}
 @Override public IBinder onBind(Intent intent){return binder;}
 @Override public int onStartCommand(Intent i,int flags,int id){
  if(i!=null&&STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
  if(i!=null&&START.equals(i.getAction())&&!starting){
   starting=true;String host=i.getStringExtra("host"),user=i.getStringExtra("username"),pass=i.getStringExtra("password");
   int port=i.getIntExtra("port",22);
   if(host==null||user==null||pass==null){append("[Missing connection details]\n");stopSelf();}else connect(host,port,user,pass);
  }
  return START_NOT_STICKY;
 }
 private void append(String s){synchronized(log){log.append(s);if(log.length()>100000)log.delete(0,log.length()-85000);}ui.post(()->{if(listener!=null)listener.output(s);});}
 public void listen(Listener l){ui.post(()->{listener=l;String s;synchronized(log){s=log.toString();}l.output(s);});}
 public void unlisten(Listener l){ui.post(()->{if(listener==l)listener=null;});}
 public boolean isConnected(){return connected;}
 private void connect(String host,int port,String user,String pass){
  append("Connecting to "+host+":"+port+"…\n");
  read.execute(()->{try{
   Session sess=new JSch().getSession(user,host,port);session=sess;sess.setPassword(pass);sess.setConfig("StrictHostKeyChecking","no");sess.setServerAliveInterval(15000);sess.setServerAliveCountMax(3);sess.connect(30000);
   ChannelShell ch=(ChannelShell)sess.openChannel("shell");shell=ch;ch.setPty(true);ch.setPtyType("xterm",100,35,800,600);
   InputStream input=ch.getInputStream();stream=ch.getOutputStream();ch.connect(15000);connected=true;
   append("Connected. Terminal ready.\n");notifyStatus("Connected to "+host);
   byte[] buf=new byte[4096];int n;while(!closing&&(n=input.read(buf))!=-1)if(n>0)append(new String(buf,0,n,StandardCharsets.UTF_8));
   if(!closing)append("\n[Session closed]\n");
  }catch(Exception e){if(!closing)append("\n[SSH error] "+e.getMessage()+"\n");}
  finally{connected=false;close();if(!closing)notifyStatus("Disconnected");}});
 }
 public void send(String command){
  if(!connected||stream==null){append("\n[Not connected]\n");return;}
  append("\n> "+command+"\n");
  write.execute(()->{try{stream.write((command+"\r").getBytes(StandardCharsets.UTF_8));stream.flush();}catch(Exception e){append("\n[Send error] "+e.getMessage()+"\n");}});
 }
 private void close(){try{if(shell!=null)shell.disconnect();}catch(Exception ignored){}try{if(session!=null)session.disconnect();}catch(Exception ignored){}}
 @Override public void onDestroy(){closing=true;connected=false;close();read.shutdownNow();write.shutdownNow();super.onDestroy();}
}
