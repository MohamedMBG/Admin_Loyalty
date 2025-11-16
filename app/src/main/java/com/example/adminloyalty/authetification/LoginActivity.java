package com.example.adminloyalty.authetification;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.adminloyalty.MainActivity;
import com.example.adminloyalty.R;
import com.example.adminloyalty.cashier.CashierActivity;
import com.google.android.material.snackbar.Snackbar;

public class LoginActivity extends AppCompatActivity {

    EditText email, password;
    Button connect;
    View root;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        //seting up UI elements
        email = findViewById(R.id.emailInput);
        password = findViewById(R.id.passwordInput);
        connect = findViewById(R.id.loginButton);
        root = findViewById(R.id.login_root);

        connect.setOnClickListener(v -> {
            String mail = email.getText().toString().trim();
            String pwd = password.getText().toString().trim();

            if (mail.isEmpty() || pwd.isEmpty()) {
                Snackbar.make(root , "Please fill all the fields!" , Snackbar.LENGTH_SHORT).show();
            }

            if (mail.equals("admin@gmail.com") && pwd.equals("TheAdmin90@@")) {
                Toast.makeText(this , "invalid"  , Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this , MainActivity.class);
                startActivity(intent);
                finish();
            }
            if (mail.equals("cashier@gmail.com") && pwd.equals("cashier@90")) {
                Intent intent = new Intent(this , CashierActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}