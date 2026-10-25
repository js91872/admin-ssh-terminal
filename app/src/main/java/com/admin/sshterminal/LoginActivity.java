package com.admin.sshterminal;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etHost, etPort, etUsername, etPassword;
    private Button btnLogin;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etHost = findViewById(R.id.etHost);
        etPort = findViewById(R.id.etPort);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        prefs = getSharedPreferences("VpsPrefs", MODE_PRIVATE);

        // Load saved credentials
        etHost.setText(prefs.getString("host", ""));
        etPort.setText(prefs.getString("port", "22"));
        etUsername.setText(prefs.getString("username", "root"));
        etPassword.setText(prefs.getString("password", ""));

        btnLogin.setOnClickListener(v -> {
            String host = etHost.getText().toString().trim();
            String port = etPort.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
                return;
            }

            // Save credentials permanently
            prefs.edit()
                    .putString("host", host)
                    .putString("port", port)
                    .putString("username", username)
                    .putString("password", password)
                    .apply();

            // Launch Terminal Screen
            Intent intent = new Intent(LoginActivity.this, TerminalActivity.class);
            intent.putExtra("host", host);
            intent.putExtra("port", Integer.parseInt(port.isEmpty() ? "22" : port));
            intent.putExtra("username", username);
            intent.putExtra("password", password);
            startActivity(intent);
        });
    }
}
