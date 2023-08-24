package com.bitcoin.applock.views;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.bitcoin.applock.AppLock;
import com.bitcoin.applock.R;
import com.bitcoin.applock.pin.PINInputController;
import com.bitcoin.applock.services.BiometricsLockService;
import com.bitcoin.applock.services.FingerprintLockService;

import java.lang.ref.WeakReference;

public class UnlockViewController extends AppLockViewController implements AppLock.UnlockDelegate, PINInputController.InputEventListener {

    protected DisplayVariant displayVariant = DisplayVariant.PIN_UNLOCK;
    protected WeakReference<Delegate> delegate;

    public UnlockViewController(Activity activity, View parent) {
        super(activity, parent);
    }

    public UnlockViewController setDelegate(Delegate delegate) {
        this.delegate = new WeakReference<Delegate>(delegate);

        return this;
    }

    @Override
    public void setupRootFlow() {
        View parent = this.parent.get();

        if (parent == null)
            return;

        BiometricsLockService biometricsLockService = AppLock.getInstance(parent.getContext())
                .getLockService(BiometricsLockService.class);

        if (biometricsLockService.isEnrolled(parent.getContext())) {
            setupBiometricUnlock();
            return;
        }


        FingerprintLockService fingerprintService = AppLock.getInstance(parent.getContext())
                .getLockService(FingerprintLockService.class);

        if (biometricsLockService.isEnrollmentEligible(parent.getContext())){
            setupBiometricUnlock();
            return;
        }

        if (fingerprintService.isEnrolled(parent.getContext())) {
            setupFingerprintUnlock();
        } else {
            setupPINUnlock();
        }
    }

    protected void setupPINUnlock() {
        this.displayVariant = DisplayVariant.PIN_UNLOCK;

        hide(fingerprintAuthImageView);
        hide(actionSettings);
        hide(actionFallback);
        hide(biometricsImageView);
        show(pinInputView);

        setDescription(R.string.applock__description_unlock_pin);

        pinInputController.ensureKeyboardVisible();
        pinInputController.setInputEventListener(this);
    }

    @Override
    public void onInputEntered(String input) {
        if (!pinInputController.matchesRequiredPINLength(input)) {
            setDescription(R.string.applock__unlock_error_insufficient_selection);

            return;
        }

        attemptPINUnlock(input);
    }

    protected void attemptPINUnlock(String input) {
        Activity activity = this.activity.get();

        if (activity == null)
            return;

        AppLock.getInstance(activity)
                .attemptPINUnlock(input, this);
    }

    protected void setupBiometricUnlock() {
        this.displayVariant = DisplayVariant.BIOMETRIC_AUTHENTICATION;
        setDescription(R.string.applock__description_biometric);
        hide(pinInputView);
        hide(actionSettings);
        hide(fingerprintAuthImageView);
        hide(actionFallback);
        show(biometricsImageView);

        View bioMetricsImage = this.biometricsImageView.get();
        bioMetricsImage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                attemptBiometricAuthentication();
            }
        });

        attemptBiometricAuthentication();
    }

    protected void setupFingerprintUnlock() {
        this.displayVariant = DisplayVariant.FINGERPRINT_AUTHENTICATION;
        View actionFallback = this.actionFallback.get();

        hide(pinInputView);
        hide(actionSettings);
        hide(biometricsImageView);
        show(fingerprintAuthImageView);
        actionFallback.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                setupPINUnlock();
            }
        });


        setDescription(R.string.applock__description_unlock_fingerprint);

        if (autoAuthorizationEnabled)
            attemptFingerprintAuthentication();
    }

    protected void attemptFingerprintAuthentication() {
        Activity activity = this.activity.get();

        if (activity == null)
            return;

        AppLock.getInstance(activity)
                .attemptFingerprintUnlock(this);
    }

    protected void attemptBiometricAuthentication() {
        Activity activity = this.activity.get();

        if (activity == null)
            return;

        AppLock.getInstance(activity)
                .attemptBiometricUnlock(activity, this);
    }

    @Override
    public void onUnlockSuccessful() {
        this.displayVariant = DisplayVariant.NONE;

        Delegate delegate = this.delegate.get();

        if (delegate != null)
            delegate.onUnlockSuccessful();
    }

    @Override
    public void onCancel() {
        this.displayVariant = DisplayVariant.NONE;

        Delegate delegate = this.delegate.get();

        if (delegate != null)
            delegate.onCancel();

    }

    @Override
    public void onResolutionRequired(int errorCode) {
        setDescription(getDescriptionResIdForError(errorCode));
        updateActionSettings(errorCode);
        handleInitialErrorPrompt(errorCode);
    }

    @Override
    public void onAuthenticationHelp(int code, String message) {
        Activity activity = this.activity.get();

        if (activity == null)
            return;

        String unformattedHelpMessage = activity.getString(R.string.applock__description_unlock_fingerprint_help);
        String formatted = String.format(unformattedHelpMessage, message);

        setDescription(formatted);
    }

    @Override
    public void onFailureLimitExceeded(String message) {
        setDescription(message);
    }

    @Override
    public void onActivityPaused() {
        Activity activity = this.activity.get();

        if (activity == null)
            return;

        AppLock.getInstance(activity)
                .cancelPendingAuthentications();

        if (displayVariant == DisplayVariant.FINGERPRINT_AUTHENTICATION)
            setDescription(R.string.applock__description_create_fingerprint_paused);
    }

    @Override
    public void onActivityResumed() {
        Activity activity = this.activity.get();

        if (activity == null || displayVariant != DisplayVariant.FINGERPRINT_AUTHENTICATION)
            return;

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
            setDescription(R.string.applock__fingerprint_error_permission_multiple);
            updateActionSettings(AppLock.ERROR_CODE_FINGERPRINTS_PERMISSION_REQUIRED);
            return;
        }

        setDescription(R.string.applock__description_unlock_fingerprint);
        hide(actionSettings);

        attemptFingerprintAuthentication();
    }

    @Override
    protected void handleActionSettingsClicked(int errorCode) {
        Activity activity = this.activity.get();
        Intent intent = getSettingsIntent(errorCode);

        if (activity == null || intent == null)
            return;

        activity.startActivity(intent);
    }

    public enum DisplayVariant {
        NONE,
        PIN_UNLOCK,
        FINGERPRINT_AUTHENTICATION,
        BIOMETRIC_AUTHENTICATION
    }

    public interface Delegate {
        void onUnlockSuccessful();

        void onCancel();
    }
}
