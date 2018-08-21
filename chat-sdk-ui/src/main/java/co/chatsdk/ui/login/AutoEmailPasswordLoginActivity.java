package co.chatsdk.ui.login;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import co.chatsdk.core.session.ChatSDK;
import co.chatsdk.core.session.InterfaceManager;
import co.chatsdk.core.types.AccountDetails;
import co.chatsdk.ui.R;
import co.chatsdk.ui.main.BaseActivity;
import co.chatsdk.ui.utils.Constants;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import java.util.HashMap;
import org.apache.commons.lang3.StringUtils;

public class AutoEmailPasswordLoginActivity extends BaseActivity implements View.OnClickListener {

	protected boolean authenticating = false;
	// This is a list of extras that are passed to the login view
	protected HashMap<String, Object> extras = new HashMap<>();

	private String emailId, password;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//        PermissionRequestHandler.shared().requestReadExternalStorage(this).subscribe(new CrashReportingCompletableObserver());
		updateExtras(getIntent().getExtras());
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		updateExtras(intent.getExtras());
	}

	@Override
	protected void onResume() {
		super.onResume();

		// If the logged out flag isn't set...
		if (getIntent() == null
			|| getIntent().getExtras() == null
			|| getIntent().getExtras().get(InterfaceManager.ATTEMPT_CACHED_LOGIN) == null
			|| (boolean) getIntent().getExtras().get(InterfaceManager.ATTEMPT_CACHED_LOGIN)) {

			showProgressDialog(getString(R.string.authenticating));

			ChatSDK.auth()
				.authenticateWithCachedToken()
				.observeOn(AndroidSchedulers.mainThread())
				.doFinally(this::dismissProgressDialog)
				.subscribe(this::afterLogin, throwable -> {
					//                        ChatSDK.logError(throwable);

					dismissProgressDialog();
				});
		}
	}

	protected void updateExtras(Bundle bundle) {
		if (bundle != null) {
			for (String s : bundle.keySet()) {
				extras.put(s, bundle.get(s));
			}
		}
		final int contentViewId;

		if (bundle != null) {
			contentViewId = bundle.getInt(Constants.CONTENT_VIEW_ID, -1);

			emailId = bundle.getString(Constants.EMAIL, null);
			password = bundle.getString(Constants.PASSWORD, null);
		} else {
			contentViewId = -1;
			emailId = null;
			password = null;
		}

		if (TextUtils.isEmpty(emailId) || TextUtils.isEmpty(password)) {
			showToast(getString(R.string.error_no_login_data));
			finish();
			return;
		}
		if (contentViewId != -1) {
			setContentView(contentViewId);
		}

		if (!isNetworkAvailable()) {
			showToast(getString(R.string.error_no_internet));
			finish();
		} else {
			final AccountDetails details = AccountDetails.username(emailId, password);
			authenticateWithDetails(details);
		}
	}

	public void authenticateWithDetails(AccountDetails details) {

		if (authenticating) {
			return;
		}
		authenticating = true;

		showProgressDialog(getString(R.string.connecting));

		ChatSDK.auth().authenticate(details).observeOn(AndroidSchedulers.mainThread()).doFinally(() -> {
			authenticating = false;
			dismissProgressDialog();
		}).subscribe(this::afterLogin, e -> {
			toastErrorMessage(e, false);
			ChatSDK.logError(e);
		});
	}

	public void toastErrorMessage(Throwable error, boolean login) {
		String errorMessage = "";

		if (StringUtils.isNotBlank(error.getMessage())) {
			errorMessage = error.getMessage();
		} else if (login) {
			errorMessage = getString(R.string.login_activity_failed_to_login_toast);
		} else {
			errorMessage = getString(R.string.login_activity_failed_to_register_toast);
		}

		showToast(errorMessage);
	}

	protected boolean isNetworkAvailable() {
		ConnectivityManager connectivityManager =
			(ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}

	@Override
	public void onClick(View v) {
		Consumer<Throwable> error = throwable -> {
			ChatSDK.logError(throwable);
			Toast.makeText(AutoEmailPasswordLoginActivity.this, throwable.getLocalizedMessage(),
				Toast.LENGTH_LONG).show();
		};
	}

	/* Dismiss dialog and open main context.*/
	protected void afterLogin() {
		// We pass the extras in case this activity was laucned by a push. In that case
		// we can load up the thread the message belongs to
		ChatSDK.ui().startMainActivity(this, extras);
	}

	public void register() {
		AccountDetails details = new AccountDetails();
		details.type = AccountDetails.Type.Register;
		details.username = emailId;
		details.password = password;

		authenticateWithDetails(details);
	}

	public void anonymousLogin() {

		AccountDetails details = new AccountDetails();
		details.type = AccountDetails.Type.Anonymous;
		authenticateWithDetails(details);
	}
}
