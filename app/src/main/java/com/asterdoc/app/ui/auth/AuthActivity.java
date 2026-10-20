package com.asterdoc.app.ui.auth;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.autofill.AutofillManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.asterdoc.app.MainActivity;
import com.asterdoc.app.R;
import com.asterdoc.app.auth.AuthManager;
import com.asterdoc.app.ui.welcome.WelcomeSlidesActivity;
import com.asterdoc.app.util.BrandedToast;
import com.asterdoc.app.util.LocaleHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputLayout;

public class AuthActivity extends AppCompatActivity {

    private boolean isSignUpMode = false;

    private ImageView ivAuthLogo;
    private TextView tvAuthTitle;
    private TextView tvAuthSubtitle;
    private MaterialButtonToggleGroup toggleAuthMode;
    private TextInputLayout tilName;
    private TextInputLayout tilEmail;
    private TextInputLayout tilPassword;
    private TextInputLayout tilConfirmPassword;
    private EditText etName;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private MaterialButton btnSubmitAuth;
    private TextView tvForgotPassword;

    private AuthManager authManager;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authManager = AuthManager.getInstance(this);
        if (authManager.isLoggedIn() && !authManager.isSessionLocked()) {
            navigateAfterAuth();
            return;
        }

        setContentView(R.layout.activity_auth);

        ivAuthLogo = findViewById(R.id.ivAuthLogo);
        tvAuthTitle = findViewById(R.id.tvAuthTitle);
        tvAuthSubtitle = findViewById(R.id.tvAuthSubtitle);
        toggleAuthMode = findViewById(R.id.toggleAuthMode);
        tilName = findViewById(R.id.tilName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSubmitAuth = findViewById(R.id.btnSubmitAuth);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        runEntranceAnimations();

        toggleAuthMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnTabSignUp) {
                    switchToSignUp();
                } else if (checkedId == R.id.btnTabLogin) {
                    switchToLogin();
                }
            }
        });

        btnSubmitAuth.setOnClickListener(v -> handleSubmit());
        tvForgotPassword.setOnClickListener(v -> handleForgotPassword());

        switchToLogin();
        setupAutofill();
    }

    private void setupAutofill() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillManager afm = getSystemService(AutofillManager.class);
            if (afm != null && afm.isAutofillSupported()) {
                View.OnFocusChangeListener listener = (v, hasFocus) -> {
                    if (hasFocus) {
                        afm.requestAutofill(v);
                    }
                };
                if (etEmail != null) etEmail.setOnFocusChangeListener(listener);
                if (etPassword != null) etPassword.setOnFocusChangeListener(listener);
            }
        }
    }

    private void runEntranceAnimations() {
        if (ivAuthLogo != null) {
            ivAuthLogo.setAlpha(0f);
            ivAuthLogo.setScaleX(0.7f);
            ivAuthLogo.setScaleY(0.7f);
            ivAuthLogo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(450).setInterpolator(new OvershootInterpolator()).start();
        }

        tvAuthTitle.setAlpha(0f);
        tvAuthTitle.setTranslationY(-30f);
        tvAuthSubtitle.setAlpha(0f);
        tvAuthSubtitle.setTranslationY(-20f);
        toggleAuthMode.setAlpha(0f);
        toggleAuthMode.setTranslationY(30f);

        tvAuthTitle.animate().alpha(1f).translationY(0f).setDuration(400).start();
        tvAuthSubtitle.animate().alpha(1f).translationY(0f).setDuration(450).setStartDelay(100).start();
        toggleAuthMode.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(150).start();
    }

    private void switchToLogin() {
        isSignUpMode = false;
        tvAuthTitle.setText(R.string.auth_title_welcome);
        tvAuthSubtitle.setText(R.string.auth_subtitle);
        tilEmail.setHint(getString(R.string.username));
        tilEmail.setHelperText(getString(R.string.username_hint_helper));

        tilName.animate().alpha(0f).translationY(-20f).setDuration(200).withEndAction(() -> {
            tilName.setVisibility(View.GONE);
        }).start();

        tilConfirmPassword.animate().alpha(0f).translationY(-20f).setDuration(200).withEndAction(() -> {
            tilConfirmPassword.setVisibility(View.GONE);
        }).start();

        tvForgotPassword.setVisibility(View.VISIBLE);
        tvForgotPassword.setAlpha(0f);
        tvForgotPassword.animate().alpha(1f).setDuration(250).start();

        btnSubmitAuth.setText(R.string.sign_in);
    }

    private void switchToSignUp() {
        isSignUpMode = true;
        tvAuthTitle.setText(R.string.create_account);
        tvAuthSubtitle.setText(R.string.auth_subtitle_signup);
        tilEmail.setHint(getString(R.string.email_address));
        tilEmail.setHelperText(getString(R.string.email_privacy_helper));

        tilName.setVisibility(View.VISIBLE);
        tilName.setAlpha(0f);
        tilName.setTranslationY(-20f);
        tilName.animate().alpha(1f).translationY(0f).setDuration(250).start();

        tilConfirmPassword.setVisibility(View.VISIBLE);
        tilConfirmPassword.setAlpha(0f);
        tilConfirmPassword.setTranslationY(-20f);
        tilConfirmPassword.animate().alpha(1f).translationY(0f).setDuration(250).start();

        tvForgotPassword.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            tvForgotPassword.setVisibility(View.GONE);
        }).start();

        btnSubmitAuth.setText(R.string.create_account);
    }

    private boolean isGoodPassword(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    private void handleSubmit() {
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (isSignUpMode) {
            String username = etName.getText() != null ? etName.getText().toString().trim() : "";
            String rawEmail = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            String email = rawEmail.toLowerCase();
            String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";

            if (username.isEmpty()) {
                tilName.setError(getString(R.string.err_enter_username));
                return;
            } else {
                tilName.setError(null);
            }

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.setError(getString(R.string.err_enter_username));
                return;
            } else {
                tilEmail.setError(null);
            }

            if (!isGoodPassword(password)) {
                tilPassword.setError(getString(R.string.err_password_complex));
                return;
            } else {
                tilPassword.setError(null);
            }

            if (!password.equals(confirmPassword)) {
                tilConfirmPassword.setError(getString(R.string.err_passwords_mismatch));
                return;
            } else {
                tilConfirmPassword.setError(null);
            }

            boolean success = authManager.signUp(username, email, password);
            if (success) {
                BrandedToast.show(this, getString(R.string.msg_account_created) + username);
                navigateAfterAuth();
            } else {
                BrandedToast.show(this, getString(R.string.err_create_account_failed));
            }
        } else {
            // Login Mode: uses username
            String username = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

            if (username.isEmpty()) {
                tilEmail.setError(getString(R.string.err_enter_username));
                return;
            } else {
                tilEmail.setError(null);
            }

            if (password.length() < 4) {
                tilPassword.setError(getString(R.string.err_password_length));
                return;
            } else {
                tilPassword.setError(null);
            }

            boolean success = authManager.login(username, password);
            if (success) {
                BrandedToast.show(this, getString(R.string.msg_logged_in_success));
                navigateAfterAuth();
            } else {
                BrandedToast.show(this, getString(R.string.err_invalid_credentials));
            }
        }
    }

    private final Handler checkHandler = new Handler(Looper.getMainLooper());
    private Runnable checkRunnable = null;
    private long currentCheckToken = 0;

    private void handleForgotPassword() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reset_password, null);
        TextInputLayout tilResetEmail = dialogView.findViewById(R.id.tilResetEmail);
        TextInputLayout tilResetNewPassword = dialogView.findViewById(R.id.tilResetNewPassword);
        TextInputLayout tilResetConfirmPassword = dialogView.findViewById(R.id.tilResetConfirmPassword);

        EditText etResetEmail = dialogView.findViewById(R.id.etResetEmail);
        EditText etResetNewPassword = dialogView.findViewById(R.id.etResetNewPassword);
        EditText etResetConfirmPassword = dialogView.findViewById(R.id.etResetConfirmPassword);
        TextView tvForgotAccountHelp = dialogView.findViewById(R.id.tvForgotAccountHelp);

        tilResetEmail.setHelperText(getString(R.string.msg_enter_registered_id));
        tilResetEmail.setError(null);
        tilResetNewPassword.setVisibility(View.GONE);
        tilResetConfirmPassword.setVisibility(View.GONE);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.reset_pwd_title)
                .setIcon(R.mipmap.ic_launcher)
                .setView(dialogView)
                .setPositiveButton(R.string.reset_pwd_title, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String resetInput = etResetEmail.getText() != null ? etResetEmail.getText().toString().trim() : "";
                    String newPassword = etResetNewPassword.getText() != null ? etResetNewPassword.getText().toString().trim() : "";
                    String confirmPassword = etResetConfirmPassword.getText() != null ? etResetConfirmPassword.getText().toString().trim() : "";

                    if (!authManager.isAccountRegistered(resetInput)) {
                        tilResetEmail.setError(getString(R.string.err_no_account_found));
                        return;
                    }

                    if (!isGoodPassword(newPassword)) {
                        tilResetNewPassword.setError(getString(R.string.err_password_complex));
                        return;
                    } else {
                        tilResetNewPassword.setError(null);
                    }

                    if (!newPassword.equals(confirmPassword)) {
                        tilResetConfirmPassword.setError(getString(R.string.err_passwords_mismatch));
                        return;
                    } else {
                        tilResetConfirmPassword.setError(null);
                    }

                    boolean updated = authManager.resetPassword(resetInput, newPassword);
                    if (updated) {
                        BrandedToast.show(AuthActivity.this, getString(R.string.msg_password_updated));
                        etEmail.setText(resetInput);
                        etPassword.setText(newPassword);
                        dialog.dismiss();
                    } else {
                        tilResetEmail.setError(getString(R.string.err_no_account_found));
                    }
                });
            }
        });

        if (tvForgotAccountHelp != null) {
            tvForgotAccountHelp.setOnClickListener(v -> showForgotAccountHelpDialog(dialog));
        }

        TextWatcher emailWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (checkRunnable != null) {
                    checkHandler.removeCallbacks(checkRunnable);
                }

                final String input = s != null ? s.toString().trim() : "";
                final long token = ++currentCheckToken;

                if (input.isEmpty()) {
                    tilResetEmail.setError(null);
                    tilResetEmail.setHelperText(getString(R.string.msg_enter_registered_id));
                    tilResetNewPassword.setVisibility(View.GONE);
                    tilResetConfirmPassword.setVisibility(View.GONE);
                    if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    }
                    return;
                }

                if (input.contains("@") && !Patterns.EMAIL_ADDRESS.matcher(input).matches()) {
                    tilResetEmail.setError(null);
                    tilResetEmail.setHelperText(getString(R.string.msg_invalid_email_format));
                    tilResetNewPassword.setVisibility(View.GONE);
                    tilResetConfirmPassword.setVisibility(View.GONE);
                    if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    }
                    return;
                }

                tilResetEmail.setError(null);
                tilResetEmail.setHelperText(getString(R.string.msg_checking_account));

                checkRunnable = () -> {
                    if (token != currentCheckToken) return;

                    boolean isRegistered = authManager.isAccountRegistered(input);

                    if (token != currentCheckToken) return;

                    if (isRegistered) {
                        tilResetEmail.setError(null);
                        tilResetEmail.setHelperText(getString(R.string.msg_account_found));
                        tilResetNewPassword.setVisibility(View.VISIBLE);
                        tilResetConfirmPassword.setVisibility(View.VISIBLE);
                        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        }
                    } else {
                        tilResetEmail.setError(getString(R.string.err_no_account_found));
                        tilResetEmail.setHelperText(null);
                        tilResetNewPassword.setVisibility(View.GONE);
                        tilResetConfirmPassword.setVisibility(View.GONE);
                        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                        }
                    }
                };

                checkHandler.postDelayed(checkRunnable, 350);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etResetEmail.addTextChangedListener(emailWatcher);

        if (etEmail.getText() != null && !etEmail.getText().toString().trim().isEmpty()) {
            etResetEmail.setText(etEmail.getText().toString().trim());
        }

        dialog.setOnDismissListener(d -> {
            if (checkRunnable != null) {
                checkHandler.removeCallbacks(checkRunnable);
            }
        });

        dialog.show();
    }

    private void showForgotAccountHelpDialog(AlertDialog parentResetDialog) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.help_account_recovery_title)
                .setMessage(R.string.help_account_recovery_msg)
                .setPositiveButton(R.string.btn_delete_and_reregister, (d, w) -> {
                    showConfirmDeleteAccountDialog(parentResetDialog);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showConfirmDeleteAccountDialog(AlertDialog parentResetDialog) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_account_deletion_title)
                .setMessage(R.string.confirm_account_deletion_msg)
                .setPositiveButton(R.string.delete_account_btn, (d, w) -> {
                    authManager.deleteAccount(AuthActivity.this);
                    BrandedToast.show(AuthActivity.this, getString(R.string.account_deleted_toast));
                    if (parentResetDialog != null && parentResetDialog.isShowing()) {
                        parentResetDialog.dismiss();
                    }
                    switchToSignUp();
                    toggleAuthMode.check(R.id.btnTabSignUp);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void navigateAfterAuth() {
        Intent intent;
        if (!authManager.hasCompletedWelcome()) {
            intent = new Intent(this, WelcomeSlidesActivity.class);
            intent.putExtra("USER_NAME", authManager.getUserName());
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        finish();
    }
}
