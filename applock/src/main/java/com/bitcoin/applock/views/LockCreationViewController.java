package com.bitcoin.applock.views;

import static androidx.core.content.ContextCompat.startActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.View;

import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.hardware.fingerprint.FingerprintManagerCompat;
import androidx.core.os.CancellationSignal;

import com.bitcoin.applock.AppLock;
import com.bitcoin.applock.R;
import com.bitcoin.applock.pin.PINInputController;
import com.bitcoin.applock.services.BiometricsLockService;
import com.bitcoin.applock.services.FingerprintLockService;
import com.bitcoin.applock.services.PINLockService;

import java.lang.ref.WeakReference;

public class LockCreationViewController extends AppLockViewController
        implements PINInputController.InputEventListener,
        FingerprintLockService.AuthenticationDelegate,
        BiometricsLockService.AuthenticationDelegate {

    protected DisplayVariant displayVariant = DisplayVariant.NONE;
    protected WeakReference<Delegate> delegate;
    protected WeakReference<View> chooserParent;
    protected String pinFirst;

    public LockCreationViewController(Activity activity, View parent) {
        super(activity, parent);

        this.chooserParent = new WeakReference(parent.findViewById(R.id.pin__create_chooser_items));
    }

    public LockCreationViewController setDelegate(Delegate delegate) {
        this.delegate = new WeakReference<Delegate>(delegate);

        return this;
    }

    @Override
    public void setupRootFlow() {
        Activity activity = this.activity.get();

        if (activity == null)
            return;

        BiometricsLockService biometricsLockService = AppLock.getInstance(activity)
                .getLockService(BiometricsLockService.class);

        if (biometricsLockService.isEnrollmentEligible(activity)) {
            setupBiometricCreation();
            return;
        }

       // setupPINCreation();
        setupCreationChooser();
    }

    protected void setupCreationChooser() {
        this.displayVariant = DisplayVariant.CHOOSER;

        hide(fingerprintAuthImageView);
        hide(pinInputView);
        hide(actionFallback);
        hide(biometricsImageView);
        hide(chooserParent);

        setDescription(R.string.applock__enable_screen_lock);

        final View parent = this.parent.get();

        if (parent == null)
            return;

        parent.findViewById(R.id.pin__action_settings).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                handleActionSettingsClicked(AppLock.ERROR_CODE_SCREEN_LOCK_DISABLED);
            }
        });

        parent.findViewById(R.id.pin__create_option_pin)
                .setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        setupPINCreation();
                    }
                });

        parent.findViewById(R.id.pin__create_option_fingerprint)
                .setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        setupFingerprintAuthentication();
                    }
                });
    }

    protected void setupBiometricCreation() {
        this.displayVariant = DisplayVariant.BIOMETRICS_AUTHENTICATION;

        hide(fingerprintAuthImageView);
        hide(chooserParent);
        hide(actionSettings);
        hide(actionFallback);
        show(biometricsImageView);

        hide(pinInputView);
        setDescription(R.string.applock__description_create_biometric);

        attemptBiometricAuthentication();

    }

    protected void setupPINCreation() {
        this.displayVariant = DisplayVariant.PIN_CREATION;

        hide(fingerprintAuthImageView);
        hide(chooserParent);
        hide(actionSettings);
        hide(actionFallback);
        hide(biometricsImageView);

        show(pinInputView);

        setDescription(R.string.applock__description_create_pin);

        pinInputController.ensureKeyboardVisible();
        pinInputController.setInputEventListener(this);
    }

    protected void setupPINConfirmation() {
        this.displayVariant = DisplayVariant.PIN_CONFIRMATION;

        hide(fingerprintAuthImageView);
        hide(chooserParent);
        hide(actionSettings);
        hide(actionFallback);

        show(pinInputView);

        setDescription(R.string.applock__description_confirm);

        pinInputController.ensureKeyboardVisible();
        pinInputController.setInputEventListener(this);
    }

    @Override
    public void onInputEntered(String input) {
        switch (displayVariant) {
            case PIN_CREATION:
                if (!pinInputController.matchesRequiredPINLength(input)) {
                    setDescription(R.string.applock__unlock_error_insufficient_selection);

                    return;
                }

                this.pinFirst = input;

                setupPINConfirmation();

                break;
            case PIN_CONFIRMATION:
                if (!pinInputController.matchesRequiredPINLength(input)) {
                    setDescription(R.string.applock__unlock_error_insufficient_selection);

                    return;
                }

                if (!input.equals(pinFirst)) {
                    this.pinFirst = null;

                    setupPINCreation();
                    setDescription(R.string.applock__description_create_pin_reattempt);

                    return;
                }

                createPINLock(input);

                break;
            default:
                break;
        }
    }

    protected void createPINLock(String input) {
        Activity activity = this.activity.get();

        if (activity == null)
            return;

        AppLock.getInstance(activity)
                .getLockService(PINLockService.class)
                .enroll(activity, input);

        handleLockCreated();
    }

    protected void setupFingerprintAuthentication() {
        this.displayVariant = DisplayVariant.FINGERPRINT_AUTHENTICATION;

        hide(pinInputView);
        hide(chooserParent);
        hide(actionSettings);
        hide(actionFallback);
        hide(biometricsImageView);

        show(fingerprintAuthImageView);

        setDescription(R.string.applock__description_create_fingerprint);

        if (autoAuthorizationEnabled)
            attemptFingerprintAuthentication();
    }

    protected void attemptFingerprintAuthentication() {
        Activity activity = this.activity.get();

        if (activity == null)
            return;

        AppLock.getInstance(activity)
                .getLockService(FingerprintLockService.class)
                .enroll(activity, this);
        handleLockCreated();
    }

    protected void attemptBiometricAuthentication() {
        Activity activity = this.activity.get();

        if (activity == null)
            return;

        AppLock.getInstance(activity)
                .getLockService(BiometricsLockService.class)
                .enroll(activity, this);

        handleLockCreated();
    }

    @Override
    public void onResolutionRequired(int errorCode) {
        setDescription(getDescriptionResIdForError(errorCode));
        updateActionSettings(errorCode);
        handleInitialErrorPrompt(errorCode);
    }

    @Override
    public void onCancel() {
        Delegate delegate = this.delegate.get();
        delegate.onBiometricCancelled();
    }

    @Override
    public void onAuthenticationSuccess(BiometricPrompt.AuthenticationResult result) {
        handleLockCreated();
    }

    @Override
    public void onAuthenticationHelp(int code, CharSequence message) {
        Activity activity = this.activity.get();

        if (activity == null)
            return;

        String unformattedHelpMessage = activity.getString(R.string.applock__description_unlock_fingerprint_help);
        String formatted = String.format(unformattedHelpMessage, message);

        setDescription(formatted);
    }

    @Override
    public void onAuthenticating(CancellationSignal cancellationSignal) {
        // Handled internally
    }

    @Override
    public void onAuthenticationSuccess(FingerprintManagerCompat.AuthenticationResult result) {
        //handleLockCreated();
        setupPINCreation();
        setDescription(R.string.applock__description_create_fallback);
    }

    @Override
    public void onAuthenticationFailed(String message) {
        setDescription(message);
    }

    protected void handleLockCreated() {
        this.displayVariant = DisplayVariant.NONE;

        Delegate delegate = this.delegate.get();

        if (delegate != null)
            delegate.onLockCreated();
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

        setDescription(R.string.applock__description_create_fingerprint);
        hide(actionSettings);
        hide(actionFallback);

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
        CHOOSER,
        PIN_CREATION,
        PIN_CONFIRMATION,
        FINGERPRINT_AUTHENTICATION,
        BIOMETRICS_AUTHENTICATION
    }

    public interface Delegate {
        void onLockCreated();

        void onBiometricCancelled();
    }
}
