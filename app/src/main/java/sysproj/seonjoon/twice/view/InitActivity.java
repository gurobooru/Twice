package sysproj.seonjoon.twice.view;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.twitter.sdk.android.core.DefaultLogger;
import com.twitter.sdk.android.core.Twitter;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterConfig;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterSession;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import sysproj.seonjoon.twice.BuildConfig;
import sysproj.seonjoon.twice.DBLoadSuccessCallback;
import sysproj.seonjoon.twice.loader.PreferenceLoader;
import sysproj.seonjoon.twice.manager.LoginManager;
import sysproj.seonjoon.twice.parser.FacebookTokenParser;
import sysproj.seonjoon.twice.parser.InstagramTokenParser;
import sysproj.seonjoon.twice.parser.TokenParser;
import sysproj.seonjoon.twice.parser.TwitterTokenParser;
import sysproj.seonjoon.twice.staticdata.UserSession;

import static com.facebook.stetho.Stetho.initializeWithDefaults;

public class InitActivity extends AppCompatActivity {

    private final static String TAG = "InitActivity";
    private static final int PERMISSION_REQUEST = 1000;

    private Context mContext;
    private String uID;
    private String uPassword;
    private AutoLoginTask autoLoginTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Context N Key
        mContext = this;

        // Init stetho
        initializeWithDefaults(this);

        loadSharedPreference();
        twitterInitialize();
        facebookInitialize();
        checkPermissions();
    }

    @Override
    public void onBackPressed() {
        // To Prevent finish application by back-button
    }

    private void loadSharedPreference() {
        uID = PreferenceLoader.loadPreference(this, BuildConfig.IDPreferenceKey);
        uPassword = PreferenceLoader.loadPreference(this, BuildConfig.PwdPreferenceKey);
    }

    private void facebookInitialize() {
        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(this);
    }

    private void twitterInitialize() {
        TwitterConfig config = new TwitterConfig.Builder(this)
                .logger(new DefaultLogger(Log.DEBUG))
                .twitterAuthConfig(new TwitterAuthConfig(BuildConfig.TwitterAPI, BuildConfig.TwitterAPISecret))
                .debug(true)
                .build();
        Twitter.initialize(config);

    }

    private void goNextActivity() {
        // Check Auto Login
        if (!uID.isEmpty() && !uPassword.isEmpty()) {
            autoLoginTask = new AutoLoginTask(uID, uPassword);
            autoLoginTask.execute();
        } else {
            final Intent intent = new Intent(InitActivity.this, LoginActivity.class);

            if (!uID.isEmpty())
                intent.putExtra(UserSession.UserIDTag, uID);

            (new Handler()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    startActivity(intent);
                    finish();
                }
            }, 1000);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST) {
            for (int res : grantResults) {
                if (res == PackageManager.PERMISSION_DENIED) {
                    finish();
                }
            }

            goNextActivity();
        }

    }

    private void checkPermissions() {
        // Permission List
        String[] permissionList = {Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS};

        ActivityCompat.requestPermissions(this, permissionList, PERMISSION_REQUEST);

    }

    private class AutoLoginTask extends AsyncTask<Void, Void, Boolean> {

        private final String mID;
        private final String mPassword;
        private static final String TAG = "AUTO_ASYNC";
        private FirebaseUser user = null;
        private ProgressDialog progressDialog;

        AutoLoginTask(String id, String password) {
            mID = id;
            mPassword = password;

            progressDialog = new ProgressDialog(mContext);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setMessage("로그인 중입니다.");
            progressDialog.setCancelable(false);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(final Void... params) {
            // TODO: attempt authentication against a network service.
            boolean login = LoginManager.getInstance().TwiceLogin(InitActivity.this, mID, mPassword);

            if (login) {
                Log.e(TAG, "Login Success");
                user = FirebaseAuth.getInstance().getCurrentUser();

                if (user == null)
                    login = false;
                else {
                    final CountDownLatch countDownLatch = new CountDownLatch(3);

                    Log.e(TAG, user.getUid());

                    LoginManager.getInstance().FacebookLogin(user.getUid(), new DBLoadSuccessCallback() {
                        @Override
                        public void LoadDataCallback(boolean isSuccess, Map<String, Object> result) {
                            if (isSuccess) {
                                TokenParser tokenParser = new FacebookTokenParser();
                                UserSession.FacebookToken = (AccessToken) tokenParser.map2Token(result);
                            }
                            countDownLatch.countDown();
                        }
                    });

                    LoginManager.getInstance().TwitterLogin(user.getUid(), new DBLoadSuccessCallback() {
                        @Override
                        public void LoadDataCallback(boolean isSuccess, Map<String, Object> result) {
                            if (isSuccess) {
                                TokenParser tokenParser = new TwitterTokenParser();
                                UserSession.TwitterToken = (TwitterSession) tokenParser.map2Token(result);

                                TwitterCore.getInstance().getSessionManager().setActiveSession(UserSession.TwitterToken);
                            }
                            countDownLatch.countDown();
                        }
                    });

                    LoginManager.getInstance().InstagramLogin(user.getUid(), new DBLoadSuccessCallback() {
                        @Override
                        public void LoadDataCallback(boolean isSuccess, Map<String, Object> result) {
                            if (isSuccess) {
                                TokenParser tokenParser = new InstagramTokenParser();
                                UserSession.InstagramToken = (String) tokenParser.map2Token(result);
                            }

                            countDownLatch.countDown();
                        }
                    });

                    try {
                        countDownLatch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } else
                Log.e(TAG, "Failure Login");

            return login;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            autoLoginTask = null;

            if (progressDialog != null) {
                progressDialog.dismiss();
                progressDialog = null;
            }

            Intent nextActivity;

            if (success) {
                user = FirebaseAuth.getInstance().getCurrentUser();

                nextActivity = new Intent(InitActivity.this, MainActivity.class);
            } else {
                nextActivity = new Intent(InitActivity.this, LoginActivity.class);
                Toast.makeText(mContext, "자동 로그인에 실패했습니다.\n 다시 로그인해주세요", Toast.LENGTH_LONG).show();
            }

            startActivity(nextActivity);
            finish();
        }

        @Override
        protected void onCancelled() {
            progressDialog.dismiss();
            progressDialog = null;

            autoLoginTask = null;
        }
    }

}
