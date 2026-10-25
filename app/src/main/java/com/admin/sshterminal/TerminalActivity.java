package com.admin.sshterminal;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerminalActivity extends AppCompatActivity {

    private TextView tvTerminalOutput;
    private EditText etCommandInput;
    private Button btnSend, btnPaste;
    private ScrollView scrollView;

    private Session session;
    private ChannelShell channelShell;
    private OutputStream sshOutputStream;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService writerExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        tvTerminalOutput = findViewById(R.id.tvTerminalOutput);
        etCommandInput = findViewById(R.id.etCommandInput);
        btnSend = findViewById(R.id.btnSend);
        btnPaste = findViewById(R.id.btnPaste);
        scrollView = findViewById(R.id.scrollView);

        String host = getIntent().getStringExtra("host");
        int port = getIntent().getIntExtra("port", 22);
        String username = getIntent().getStringExtra("username");
        String password = getIntent().getStringExtra("password");

        btnPaste.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                if (item != null && item.getText() != null) {
                    etCommandInput.setText(item.getText().toString());
                    etCommandInput.setSelection(etCommandInput.getText().length());
                }
            }
        });

        btnSend.setOnClickListener(v -> {
            String cmd = etCommandInput.getText().toString();
            if (!cmd.isEmpty()) {
                sendCommand(cmd + "\n");
                etCommandInput.setText("");
            }
        });

        connectSsh(host, port, username, password);
    }

    private void connectSsh(String host, int port, String username, String password) {
        appendOutput("Connecting to " + host + ":" + port + "...\n");
        executor.execute(() -> {
            try {
                JSch jsch = new JSch();
                session = jsch.getSession(username, host, port);
                session.setPassword(password);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect(10000); // 10 second timeout

                channelShell = (ChannelShell) session.openChannel("shell");
                InputStream inputStream = channelShell.getInputStream();
                sshOutputStream = channelShell.getOutputStream();

                channelShell.connect(10000);
                appendOutput("Connected successfully to VPS Root!\n\n");

                byte[] buffer = new byte[1024];
                int i;
                while ((i = inputStream.read(buffer)) != -1) {
                    String output = new String(buffer, 0, i);
                    mainHandler.post(() -> appendOutput(output));
                }

            } catch (Exception e) {
                mainHandler.post(() -> appendOutput("\n[Connection Error]: " + e.getMessage() + "\n"));
            }
        });
    }

    private void sendCommand(String command) {
        if (sshOutputStream != null) {
            writerExecutor.execute(() -> {
                try {
                    sshOutputStream.write(command.getBytes());
                    sshOutputStream.flush();
                } catch (Exception e) {
                    mainHandler.post(() -> appendOutput("\n[Write Error]: " + e.getMessage() + "\n"));
                }
            });
        }
    }

    private void appendOutput(String text) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> appendOutput(text));
            return;
        }
        if (isFinishing() || isDestroyed()) return;
        tvTerminalOutput.append(text);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        writerExecutor.shutdownNow();
        try {
            if (channelShell != null) channelShell.disconnect();
            if (session != null) session.disconnect();
        } catch (Exception ignored) {}
    }
}
