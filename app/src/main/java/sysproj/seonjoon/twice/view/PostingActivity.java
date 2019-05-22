package sysproj.seonjoon.twice.view;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import sysproj.seonjoon.twice.BuildConfig;
import sysproj.seonjoon.twice.DataLoadCompleteCallback;
import sysproj.seonjoon.twice.R;
import sysproj.seonjoon.twice.entity.FacebookPageVO;
import sysproj.seonjoon.twice.loader.FacebookLoader;
import sysproj.seonjoon.twice.parser.FacebookParser;
import sysproj.seonjoon.twice.view.custom.PostImageAdapter;
import sysproj.seonjoon.twice.view.custom.TwiceGallery.GalleryActivity;

public class PostingActivity extends AppCompatActivity {

    private static final int IMAGE_SELECT = 1000;
    private static final String TAG = "PostingActivity";

    private EditText postMessage;
    private ImageButton loadImage;
    private RadioButton postReserveRadio;
    private Context mContext;
    private ArrayList<String> selectedImage;
    private ViewPager postImagePager;
    private PostImageAdapter postImageAdapter;
    private SendQueryAsync async;

    private ArrayList<FacebookPageVO> pages;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        mContext = this;
        selectedImage = new ArrayList<>();

        FacebookLoader loader = new FacebookLoader(mContext);
        loader.LoadPagelist(new DataLoadCompleteCallback() {
            @Override
            public void Complete(boolean isSuccess, JSONObject result) {
                Log.e(TAG, isSuccess + "result");
                if (isSuccess) {
                    FacebookParser parser = new FacebookParser();
                    pages = parser.parsePageList(result);

                    for (int i = 0; i < pages.size(); i++){
                        Log.e(TAG, pages.get(i).getName() + " / " + pages.get(i).getPageId());
                    }
                } else
                    pages = null;
            }
        });

        initLayout();
        initListener();
    }

    private void initLayout() {
        postMessage = (EditText) findViewById(R.id.create_post_edit_text);
        loadImage = (ImageButton) findViewById(R.id.post_include_image);
        postReserveRadio = (RadioButton) findViewById(R.id.post_reserve_radio);
        postImagePager = (ViewPager) findViewById(R.id.create_post_image_pager);
        postImageAdapter = new PostImageAdapter(mContext, selectedImage);

        postImagePager.setAdapter(postImageAdapter);
        postImagePager.setPageMargin(20);
    }

    private void initListener() {
        loadImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                AlertDialog imageCountDialog = new AlertDialog.Builder(mContext)
                        .setMessage("Twitter는 최대 4개까지의 이미지만 적용됩니다.")
                        .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();

                                Intent gallery = new Intent(PostingActivity.this, GalleryActivity.class);
                                startActivityForResult(gallery, IMAGE_SELECT);
                            }
                        })
                        .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.cancel();
                            }
                        })
                        .create();

                imageCountDialog.show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.posting_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.posting_done) {
            if (async == null) {
                async = new SendQueryAsync();
                async.execute();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.e(TAG, requestCode + " get Result : " + resultCode);

        if (requestCode == IMAGE_SELECT) {
            if (resultCode == RESULT_OK) {
                ArrayList<String> selectedImageUri = data.getStringArrayListExtra("result");

                if (selectedImageUri != null && !selectedImageUri.isEmpty()) {
                    selectedImage.clear();
                    selectedImage.addAll(selectedImageUri);
                    //postImageAdapter.setUriList(selectedImage);
                    postImageAdapter.notifyDataSetChanged();
                } else
                    Log.e(TAG, "Selected Image is Null or empty");
            }
        }

    }

    private class SendQueryAsync extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = new ProgressDialog(mContext);
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog.setMessage("작성 중입니다.");
        }

        @Override
        protected Boolean doInBackground(Void... voids) {

            boolean res = true;
            String urls = BuildConfig.ServerIP + "facebook_post";

            Log.e(TAG, urls);

            try {
                URL url = new URL(urls);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(3000);
                connection.setDoOutput(true);

                OutputStreamWriter osw = new OutputStreamWriter(connection.getOutputStream());
                osw.write(makeJSON());
                osw.flush();
                osw.close();

                Log.e(TAG, "Response : " + connection.getResponseCode());

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                    res = false;

            } catch (IOException e) {
                e.printStackTrace();
            }

            return res;
        }

        @Override
        protected void onPostExecute(Boolean res) {
            super.onPostExecute(res);

            dialog.dismiss();
            dialog = null;

            if (res) {
                Toast.makeText(mContext, "게시 완료 되었습니다.", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(mContext, "게시에 실패하였습니다.\n 잠시후에 다시 시도해보시기 바랍니다.", Toast.LENGTH_SHORT).show();
            }

            async = null;
        }

        private String makeJSON() {
            JSONObject sendObject = new JSONObject();
            JSONObject facebookObject = new JSONObject();
            JSONArray facebookData = new JSONArray();

            try {
                // Each Page
                if (pages != null) {
                    for (int pageNum = 0; pageNum < pages.size(); pageNum++) {
                        FacebookPageVO pageVO = pages.get(pageNum);

                        JSONObject item = new JSONObject();
                        JSONArray imageArray = new JSONArray();

                        item.put("token", pageVO.getAccessToken());
                        item.put("message", postMessage.getText().toString());
                        item.put("page_id", pageVO.getPageId());

                        for (int i = 0; i < selectedImage.size(); i++) {
                            String filePath = selectedImage.get(i);
                            String endcoded = Base64.encodeToString(readFile(filePath), Base64.NO_WRAP | Base64.URL_SAFE);
                            imageArray.put(endcoded);
                        }

                        item.put("images", imageArray);
                        facebookData.put(item);
                    }
                }
                facebookObject.put("data", facebookData);
                sendObject.put("facebook", facebookObject);

                Log.e(TAG, sendObject.toString(2));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return sendObject.toString();
        }

        private byte[] readFile(String filePath) {

            File file = new File(filePath);
            byte[] ret = null;
            try (FileInputStream fis = new FileInputStream(file)) {

                ret = new byte[(int) fis.getChannel().size()];
                fis.read(ret);

            } catch (IOException e) {
                Log.e(TAG, "File Not Found");
            }

            return ret;
        }
    }
}