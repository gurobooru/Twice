package sysproj.seonjoon.twice.view;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

import androidx.fragment.app.FragmentActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.preference.Preference;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.HttpsURLConnection;

import sysproj.seonjoon.twice.BuildConfig;
import sysproj.seonjoon.twice.DataLoadCompleteCallback;
import sysproj.seonjoon.twice.OnHashtagClickListener;
import sysproj.seonjoon.twice.R;
import sysproj.seonjoon.twice.entity.UserProfile;
import sysproj.seonjoon.twice.loader.DataLoader;
import sysproj.seonjoon.twice.loader.FacebookLoader;
import sysproj.seonjoon.twice.loader.PreferenceLoader;
import sysproj.seonjoon.twice.loader.TwitterLoader;
import sysproj.seonjoon.twice.manager.LoginManager;
import sysproj.seonjoon.twice.parser.FacebookParser;
import sysproj.seonjoon.twice.parser.TwitterParser;
import sysproj.seonjoon.twice.staticdata.StaticAppData;
import sysproj.seonjoon.twice.staticdata.UserSession;

public class MainActivity extends FragmentActivity implements NavigationView.OnNavigationItemSelectedListener, OnHashtagClickListener {

    private static final String TAG = "MAIN_ACTIVITY";
    private static final int HOME = 0;
    private static final int SEARCH = 1;

    private Context mContext;
    private FragmentManager fm;

    private DrawerLayout personDrawer;
    private NavigationView personNavigation;
    private ImageView openMenuButton;
    private TextView topHomeText;
    private EditText topEditText;
    private Fragment[] fragments = new Fragment[]{null, null};
    private String[] fragmentsTags = new String[]{"Home", "Search"};
    private BottomNavigationView bottomNavigationView;
    private ImageView createPostImage;

    private ImageView repreProfileImage;
    private TextView repreName;
    private TextView repreEmail;
    private ImageView facebookStatus;
    private ImageView instagramStatus;
    private ImageView twitterStatus;

    private UserProfile profile;

    private long parsedTime = 0;
    private static int showingFragment;
    private LoadProfileAsync loadProfileAsync;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        mContext = this;
        loadProfileAsync = new LoadProfileAsync();
        loadProfileAsync.execute();

        fm = getFragmentManager();

        setLayout();
        setListener();
        showHomeFragment();

        // (new LoadAccounts()).execute();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.drawer_link:
                gotoLinkingActivity();
                break;
            case R.id.drawer_logout:
                askLogout();
                break;

            case R.id.drawer_app_setting:
                gotoSettingActivity();
                break;
        }
        return false;
    }

    @Override
    public void onBackPressed() {

        if (personDrawer.isDrawerOpen(Gravity.LEFT)) {
            personDrawer.closeDrawer(Gravity.LEFT);
        } else {
            if (parsedTime == 0) {
                Toast.makeText(this, "한번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show();
                parsedTime = System.currentTimeMillis();
            } else {
                int seconds = (int) (System.currentTimeMillis() - parsedTime);
                if (seconds > 2000) {
                    Toast.makeText(this, "한번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show();
                    parsedTime = 0;
                } else {
                    super.onBackPressed();
                    System.runFinalization();
                    finishAffinity();
                }
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

    }

    @Override
    public void onClickHashTag(String hashTag) {
        showSearchFragment();
        ((FragmentSearch) getFragmentManager().findFragmentByTag(fragmentsTags[SEARCH])).startSearch(hashTag);
    }

    private void setLayout() {
        topHomeText = (TextView) findViewById(R.id.main_top_home_text);
        topEditText = (EditText) findViewById(R.id.main_top_search_edit);

        createPostImage = (ImageView) findViewById(R.id.main_top_post_edit);

        personDrawer = (DrawerLayout) findViewById(R.id.main_drawer_user);
        personNavigation = (NavigationView) findViewById(R.id.drawer_user_navigation);
        personDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        openMenuButton = (ImageView) findViewById(R.id.main_profile_setting);

        bottomNavigationView = (BottomNavigationView) findViewById(R.id.main_bottom);

        View headerView = personNavigation.getHeaderView(0);

        repreProfileImage = (ImageView) headerView.findViewById(R.id.drawer_profile_image);
        repreName = (TextView) headerView.findViewById(R.id.drawer_user_head_name);
        repreEmail = (TextView) headerView.findViewById(R.id.drawer_user_head_email);
        facebookStatus = (ImageView) headerView.findViewById(R.id.drawer_status_facebook_image);
        instagramStatus = (ImageView) headerView.findViewById(R.id.drawer_status_instagram_image);
        twitterStatus = (ImageView) headerView.findViewById(R.id.drawer_status_twitter_image);

    }

    private void changeTopViewVisibility(int showPos) {
        switch (showPos) {
            case HOME:
                topHomeText.setVisibility(View.VISIBLE);
                topEditText.setVisibility(View.GONE);
                break;
            case SEARCH:
                topHomeText.setVisibility(View.GONE);
                topEditText.setVisibility(View.VISIBLE);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "OnDestory called");
        LoginManager.getInstance().SignOut();
    }

    private void setListener() {

        createPostImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, PostingActivity.class);
                startActivity(intent);
            }
        });

        topEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {

                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    String searchTag = textView.getText().toString();
                    ((FragmentSearch) getFragmentManager().findFragmentByTag(fragmentsTags[1])).startSearch(searchTag);
                }

                return false;
            }
        });

        // Drawer Set
        openMenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.e(TAG, "Click Open Menu Button");
                if (!personDrawer.isDrawerOpen(GravityCompat.START))
                    personDrawer.openDrawer(GravityCompat.START);
            }
        });

        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.main_bottom_home:
                        Log.e(TAG, "Select Home Button");
                        showHomeFragment();
                        break;
                    case R.id.main_bottom_search:
                        Log.e(TAG, "Select Search Button");
                        showSearchFragment();
                        break;
                }
                return true;
            }
        });

        personNavigation.setNavigationItemSelectedListener(this);
    }

    private void askLogout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);

        builder.setMessage(R.string.main_dialog_logout_message)
                .setPositiveButton(R.string.main_dialog_logout_positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        FirebaseAuth.getInstance().signOut();
                        UserSession.FacebookProfile = null;
                        PreferenceLoader.removePreference(mContext, BuildConfig.IDPreferenceKey);
                        PreferenceLoader.removePreference(mContext, BuildConfig.PwdPreferenceKey);
                        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    }
                }).create().show();
    }

    private void gotoSettingActivity() {
        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
        startActivity(intent);
    }

    private void gotoLinkingActivity() {
        Intent intent = new Intent(MainActivity.this, SNSLinkingActivity.class);
        startActivity(intent);
    }

    private void showHomeFragment() {
        showingFragment = HOME;
        changeTopViewVisibility(showingFragment);

        if (fragments[HOME] == null) {
            fragments[HOME] = new FragmentHome();

            fm.beginTransaction().add(R.id.main_frame_layout, fragments[HOME], fragmentsTags[HOME]).commit();
        }

        fm.beginTransaction().show(fragments[HOME]).commit();
        if (fragments[SEARCH] != null) fm.beginTransaction().hide(fragments[SEARCH]).commit();
    }

    private void showSearchFragment() {
        showingFragment = SEARCH;
        changeTopViewVisibility(showingFragment);

        if (fragments[SEARCH] == null) {
            fragments[SEARCH] = new FragmentSearch();

            fm.beginTransaction().add(R.id.main_frame_layout, fragments[SEARCH], fragmentsTags[SEARCH]).commit();
        }

        fm.beginTransaction().hide(fragments[HOME]).commit();
        fm.beginTransaction().show(fragments[SEARCH]).commit();
    }

    public static int getShowingFragmentNumber() {
        return showingFragment;
    }

    private class LoadProfileAsync extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            if (UserSession.FacebookToken != null) {
                DataLoader dataLoader = new FacebookLoader(mContext);
                JSONObject userJSON = dataLoader.LoadUserProfileData();

                FacebookParser snsParser = new FacebookParser();
                UserSession.FacebookProfile = snsParser.parseUserProfile(userJSON);

                profile = UserSession.FacebookProfile;

            } else if (UserSession.TwitterToken != null) {
                final CountDownLatch countDownLatch = new CountDownLatch(1);
                DataLoader loader = new TwitterLoader(mContext);
                loader.LoadUserProfileData(new DataLoadCompleteCallback() {
                    @Override
                    public void Complete(boolean isSuccess, JSONObject result) {
                        if (isSuccess) {
                            try {
                                TwitterParser parser = new TwitterParser();
                                profile = parser.parseUserProfile(result);

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        countDownLatch.countDown();
                    }
                });

                try {
                    countDownLatch.await();
                } catch (InterruptedException ignored) {
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void avoid) {
            super.onPostExecute(avoid);

            if (profile != null) {
                repreName.setText(profile.getName());
                repreEmail.setText(profile.getEmail());
                Glide.with(MainActivity.this)
                        .load(profile.getProfileImage())
                        .apply(RequestOptions.circleCropTransform())
                        .into(repreProfileImage);

                if (UserSession.FacebookToken == null)
                    facebookStatus.setColorFilter(StaticAppData.Gray_filter);
                else
                    facebookStatus.clearColorFilter();

                if (UserSession.TwitterToken == null)
                    twitterStatus.setColorFilter(StaticAppData.Gray_filter);
                else
                    twitterStatus.clearColorFilter();

                instagramStatus.setColorFilter(StaticAppData.Gray_filter);
            } else {
                repreName.setText("대표 SNS를 선택해주세요");
                repreEmail.setText("");
                facebookStatus.setColorFilter(StaticAppData.Gray_filter);
                twitterStatus.setColorFilter(StaticAppData.Gray_filter);
                instagramStatus.setColorFilter(StaticAppData.Gray_filter);
            }

            loadProfileAsync = null;
        }
    }

    private class LoadAccounts extends AsyncTask<Void, Void, Void>{

        @Override
        protected Void doInBackground(Void... voids) {
            String urls = "https://graph.facebook.com/me/accounts?access_token=" + UserSession.FacebookToken.getToken();

            try{
                URL url = new URL(urls);

                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

                conn.setRequestMethod("GET");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                String line = null;
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK)
                    br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));

                Log.e(TAG, "Account Result : " + conn.getResponseCode());

                while ( (line = br.readLine()) != null)
                    Log.e(TAG, line);

                br.close();
                conn.disconnect();

            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }
    }
}
