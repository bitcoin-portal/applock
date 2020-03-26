package com.bitcoin.applock.activities;

import android.app.Activity;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.bitcoin.applock.R;
import com.bitcoin.applock.views.LockCreationViewController;

public class LockCreationActivity extends AppCompatActivity implements LockCreationViewController.Delegate {

    protected LockCreationViewController viewController;

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        setContentView(R.layout.applock__activity_lock_creation);

        this.viewController = new LockCreationViewController(this, findViewById(R.id.pin__container));
        this.viewController.setDelegate(this);
        this.viewController.setupRootFlow();
    }

    @Override
    public void onLockCreated() {
        setResult(Activity.RESULT_OK);
        finish();
    }

    @Override
    public void onBiometricCancelled() {
        this.viewController.setupRootFlow();
    }
}
