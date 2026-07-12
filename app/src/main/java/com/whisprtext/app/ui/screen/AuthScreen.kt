package com.whisprtext.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whisprtext.app.ui.viewmodel.AuthState
import com.whisprtext.app.ui.viewmodel.AuthViewModel

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import android.util.Log

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit
) {
    var isLogin by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        Log.d("AuthScreen", "LaunchedEffect observed authState: $authState")
        if (authState is AuthState.Success) {
            Log.d("AuthScreen", "Auth success, calling onAuthSuccess and clearing state")
            onAuthSuccess()
            viewModel.clearState()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "WhisprText",
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.headlineLarge
            )

            Text(
                text = if (isLogin) "Welcome back" else "Create privacy-focused account",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (username.isNotBlank() && password.isNotBlank() && authState !is AuthState.Loading) {
                            Log.d("AuthScreen", "Done IME Action clicked: isLogin=$isLogin, username=$username")
                            if (isLogin) {
                                viewModel.login(username, password)
                            } else {
                                viewModel.signup(username, password)
                            }
                        } else {
                            Log.d("AuthScreen", "Done IME Action clicked but fields invalid or loading: usernameBlank=${username.isBlank()}, passwordBlank=${password.isBlank()}, authState=$authState")
                        }
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (authState is AuthState.Error) {
                Text(
                    text = (authState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }

            Button(
                onClick = {
                    Log.d("AuthScreen", "Submit Button clicked: isLogin=$isLogin, username=$username")
                    if (isLogin) {
                        viewModel.login(username, password)
                    } else {
                        viewModel.signup(username, password)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = username.isNotBlank() && password.isNotBlank() && authState !is AuthState.Loading
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text(if (isLogin) "Login" else "Sign Up")
                }
            }

            TextButton(
                onClick = { isLogin = !isLogin }
            ) {
                Text(if (isLogin) "Don't have an account? Sign up" else "Already have an account? Login")
            }
        }
    }
}
