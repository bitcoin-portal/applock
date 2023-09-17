package com.bitcoin.applock.services;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import com.bitcoin.applock.AppLock;
import com.bitcoin.applock.R;

import java.util.concurrent.Executor;

public class BiometricsLockService extends LockService {

    private static final String PREF_ENROLLMENT_ALLOWED = "biometric_enrollment_allowed";
    private BiometricPrompt biometricPrompt;
    private AuthenticationDelegate savedDelegate;

    @Override
    public boolean isEnrollmentEligible(Context context) {
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return keyguard.isDeviceSecure() && isBiometricCompatible(context);
    }

    boolean isBiometricCompatible(Context context) {
        BiometricManager biometricManager = BiometricManager.from(context);
        switch (biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK |
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                return true;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
            case BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED:
            case BiometricManager.BIOMETRIC_STATUS_UNKNOWN:
                break;
        }
        return false;
    }

    public void enroll(Context context, final AuthenticationDelegate delegate) {
        //authenticate(context, false, delegate);
        notifyEnrolled(context);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                delegate.onCancel();
            }
        }, 1000);

    }

    public void authenticate(Context context, AuthenticationDelegate delegate) {
        authenticate(context, true, delegate);
    }

    protected void authenticate(Context context, boolean localEnrollmentRequired, AuthenticationDelegate delegate) {

        showBiometricPrompt(context, delegate);
    }


    private void showBiometricPrompt(final Context context, final AuthenticationDelegate delegate) {
        Executor executor;
        BiometricPrompt.PromptInfo promptInfo;

        executor = new Executor() {
            @Override
            public void execute(Runnable command) {
                new Handler(Looper.getMainLooper()).post(command);
            }
        };


        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.applock__dialog_title))
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK |
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        savedDelegate = delegate;

        biometricPrompt = new BiometricPrompt((FragmentActivity) context,
                executor, new BiometricPrompt.AuthenticationCallback() {

            @Override
            public void onAuthenticationError(int errorCode,
                                              @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode == 10 || errorCode == 13) {
                    savedDelegate.onCancel();
                } else if (errorCode == 11) {
                    savedDelegate.onAuthenticationFailed(context.getString(R.string.applock__biometric_error_not_enrolled));
                } else {
                    savedDelegate.onAuthenticationFailed(context.getString(R.string.applock__fingerprint_error_unrecognized));
                }
            }

            @Override
            public void onAuthenticationSucceeded(
                    @NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                notifyEnrolled(context);
                final @NonNull BiometricPrompt.AuthenticationResult myResult = result;
                savedDelegate.onAuthenticationSuccess(myResult);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                savedDelegate.onAuthenticationFailed(context.getString(R.string.applock__biometric_error));
            }
        });
        try {
            biometricPrompt.authenticate(promptInfo);
        } catch (Exception e) {
            savedDelegate.onAuthenticationFailed(context.getString(R.string.applock__biometrics_prompt_failure));
        }
    }


    @Override
    public boolean isEnrolled(Context context) {
        return AppLock.getInstance(context)
                .getPreferences()
                .getBoolean(PREF_ENROLLMENT_ALLOWED, false);
    }

    protected void notifyEnrolled(Context context) {
        AppLock.getInstance(context)
                .getPreferences()
                .edit()
                .putBoolean(PREF_ENROLLMENT_ALLOWED, true)
                .commit();
    }

    @Override
    public void invalidateEnrollments(Context context) {
        AppLock.getInstance(context)
                .getPreferences()
                .edit()
                .putBoolean(PREF_ENROLLMENT_ALLOWED, false)
                .commit();
    }

    @Override
    public void cancelPendingAuthentications(Context context) {
        if (biometricPrompt != null) {
            this.biometricPrompt = null;
        }
    }

    public interface AuthenticationDelegate {
        void onResolutionRequired(int errorCode);

        void onCancel();

        void onAuthenticationSuccess(BiometricPrompt.AuthenticationResult result);

        void onAuthenticationFailed(String message);
    }
}
