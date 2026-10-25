package com.admin.sshterminal;
import android.content.*;
import android.os.*;
import android.graphics.Color;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.regex.*;
public class TerminalActivity extends AppCompatActivity {
 private TextView screen;private EditText command;private ScrollView scroll;
 private SshService service;private boolean bound=false;
 private final SshService.Listener listener=s->append(s);
 private final ServiceConnection connection=new ServiceConnection(){
  public void onServiceConnected(ComponentName n,IBinder b){service=((SshService.LocalBinder)b).service();bound=true;screen.setText("");service.listen(listener);}
  public void onServiceDisconnected(ComponentName n){bound=false;service=null;}
 };
 @Override protected void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_terminal);
  screen=findViewById(R.id.tvTerminalOutput);command=findViewById(R.id.etCommandInput);scroll=findViewById(R.id.scrollView);
  findViewById(R.id.btnSend).setOnClickListener(v->{String cmd=command.getText().toString();if(!cmd.trim().isEmpty()){if(bound)service.send(cmd);else append("\n[Connecting…]\n");command.setText("");}});
  findViewById(R.id.btnPaste).setOnClickListener(v->{android.content.ClipboardManager m=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(m!=null&&m.hasPrimaryClip()){CharSequence t=m.getPrimaryClip().getItemAt(0).coerceToText(this);command.setText(t);command.setSelection(command.length());}});
  if(state==null&&getIntent().hasExtra("host")){
   Intent i=new Intent(this,SshService.class).setAction(SshService.START);i.putExtras(getIntent().getExtras());
   if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
  }
 }
 @Override protected void onStart(){super.onStart();bindService(new Intent(this,SshService.class),connection,0);}
 @Override protected void onStop(){if(bound){service.unlisten(listener);unbindService(connection);bound=false;}super.onStop();}
 private void append(String raw){
  // Hide common terminal control sequences including bracketed-paste markers.
  String clean=raw.replaceAll("\\u001B\\[[0-9;?]*[ -/]*[@-~]","").replace("\r","");
  if(clean.isEmpty())return;
  SpannableString styled=new SpannableString(clean);
  // Shell prompts have a separate purple color; commands prefixed with > are cyan.
  Matcher m=Pattern.compile("(?m)(?:^|\\n)(?:[^\\n]*@[^\\n]*:[^\\n]*[#$])").matcher(clean);
  while(m.find()){int start=m.start();if(clean.charAt(start)=='\n')start++;styled.setSpan(new ForegroundColorSpan(Color.rgb(195,120,255)),start,m.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
  Matcher commands=Pattern.compile("(?m)^>[^\\n]*").matcher(clean);
  while(commands.find())styled.setSpan(new ForegroundColorSpan(Color.rgb(75,210,255)),commands.start(),commands.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
  screen.append(styled);scroll.post(()->scroll.fullScroll(ScrollView.FOCUS_DOWN));
 }
}
