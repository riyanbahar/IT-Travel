package com.projects.accounts;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.anthonycr.grant.PermissionsManager;
import com.anthonycr.grant.PermissionsResultAction;
import com.application.StoreFinderApplication;
import com.config.Config;
import com.config.UIConfig;
import com.facebook.login.LoginManager;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.libraries.asynctask.MGAsyncTask;
import com.libraries.asynctask.MGAsyncTask.OnMGAsyncTaskListener;
import com.libraries.dataparser.DataParser;
import com.libraries.imageview.MGImageView;
import com.libraries.usersession.UserAccessSession;
import com.libraries.usersession.UserSession;
import com.libraries.utilities.MGUtilities;
import com.models.DataResponse;
import com.models.Status;
import com.models.User;
import com.apps.storefinder.R;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterSession;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

public class ProfileActivity extends AppCompatActivity implements OnClickListener {

	private int REGISTER_IMAGE_PICKER_SELECT_THUMB = 997;
	private int REGISTER_IMAGE_PICKER_SELECT_COVER = 998;
	private String pathImgThumb = null;
	private String pathImgCover = null;
	UserSession user;
	String fullName = null;
	String password = null;
	MGAsyncTask task;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
		setContentView(R.layout.view_profile);
		setTitle(R.string.update_profile);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			
		ImageView imgViewCover = (ImageView) this.findViewById(R.id.imgViewCover);
		imgViewCover.setOnClickListener(this);

		MGImageView imgViewThumb = (MGImageView) findViewById(R.id.imgViewThumb);
		imgViewThumb.setCornerRadius(0.0f);
		imgViewThumb.setBorderWidth(UIConfig.BORDER_WIDTH);
		imgViewThumb.setBorderColor(getResources().getColor(UIConfig.THEME_BLACK_COLOR));
		imgViewThumb.setOnClickListener(this);
		
		EditText txtFullName = (EditText) this.findViewById(R.id.txtFullName);
		txtFullName.setOnClickListener(this);

		EditText txtPassword = (EditText) this.findViewById(R.id.txtPassword);
		txtPassword.setOnClickListener(this);
		
		Button btnRegister = (Button) this.findViewById(R.id.btnRegister);
		btnRegister.setOnClickListener(this);
		
		Button btnLogout = (Button) this.findViewById(R.id.btnLogout);
		btnLogout.setOnClickListener(this);

        TextView tvDeleteAccount = (TextView) this.findViewById(R.id.tvDeleteAccount);
        tvDeleteAccount.setOnClickListener(this);
        tvDeleteAccount.setVisibility(View.GONE);
        if(Config.ALLOW_ACCOUNT_DELETION) {
            tvDeleteAccount.setVisibility(View.VISIBLE);
        }

		
		UserAccessSession accessSession = UserAccessSession.getInstance(ProfileActivity.this);
		user = accessSession.getUserSession();
		String fullName = user.getFull_name();
		if(fullName == null) {
			fullName = String.format("%s-%s",
					MGUtilities.getStringFromResource(this, R.string.user),
					user.getUser_id());
		}
		
		else if(fullName != null && fullName.contains("null")) {
			fullName = String.format("%s-%s",
					MGUtilities.getStringFromResource(this, R.string.user),
					user.getUser_id());
		}
		
		txtFullName.setText( fullName );
		txtPassword.setText( "" );
		if( (user.getFacebook_id() != null && user.getFacebook_id().length() > 0) ||
				(user.getTwitter_id() != null && user.getTwitter_id().length() > 0) ) {
			txtPassword.setVisibility(View.INVISIBLE);
		}

		if(user.getPhoto_url() != null && user.getPhoto_url().length() > 0) {
			StoreFinderApplication.getImageLoaderInstance(this)
					.displayImage(
							user.getPhoto_url(),
							imgViewCover,
							StoreFinderApplication.getDisplayImageOptionsInstance());
		}
		if(user.getThumb_url() != null && user.getThumb_url().length() > 0) {
			StoreFinderApplication.getImageLoaderInstance(this)
					.displayImage(
							user.getThumb_url(),
							imgViewThumb,
							StoreFinderApplication.getDisplayImageOptionsThumbInstance());
		}
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch (v.getId()) {
			case R.id.imgViewCover:
				checkPermissionStorage(REGISTER_IMAGE_PICKER_SELECT_COVER);
				break;
			case R.id.imgViewThumb:
				checkPermissionStorage(REGISTER_IMAGE_PICKER_SELECT_THUMB);
				break;
			case R.id.btnRegister:
				updateUser();
				break;
			case R.id.btnLogout:
				showLogoutAlertDialog();
				break;
            case R.id.tvDeleteAccount:
                showAlertDialogDeleteAccount();
                break;
			default:
				break;
		}
	}
	
	private void getPicture(int selector) {
		Intent i = new Intent(
                Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
         
        startActivityForResult(i, selector);
	}

	public void updateUser() {
		if(!MGUtilities.hasConnection(ProfileActivity.this)) {
			MGUtilities.showAlertView(
					ProfileActivity.this,
					R.string.network_error,
					R.string.no_network_connection);
			return;
		}
		
		EditText txtFullName = (EditText) this.findViewById(R.id.txtFullName);
		EditText txtPassword = (EditText) this.findViewById(R.id.txtPassword);
		fullName = txtFullName.getText().toString();
		password = txtPassword.getText().toString();
		if(fullName.length() == 0) {
			MGUtilities.showAlertView(
					ProfileActivity.this,
					R.string.field_error,
					R.string.some_fields_are_missing);
			return;
		}
		if(!UserAccessSession.getInstance(this).isLoggedInFromSocial()) {
			if(password.length() == 0) {
				MGUtilities.showAlertView(
						ProfileActivity.this,
						R.string.field_error,
						R.string.password_length_error);
				return;
			}
		}
		
        task = new MGAsyncTask(ProfileActivity.this);
        task.setMGAsyncTaskListener(new OnMGAsyncTaskListener() {
			
        	DataResponse response;
        	DataResponse photoResponse;
        	
			@Override
			public void onAsyncTaskProgressUpdate(MGAsyncTask asyncTask) { }
			
			@Override
			public void onAsyncTaskPreExecute(MGAsyncTask asyncTask) { }
			
			@Override
			public void onAsyncTaskPostExecute(MGAsyncTask asyncTask) {
				// TODO Auto-generated method stub
				updateRegistration(response, photoResponse);
			}
			
			@Override
			public void onAsyncTaskDoInBackground(MGAsyncTask asyncTask) {
				// TODO Auto-generated method stub
				ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
				params.add(new BasicNameValuePair("full_name", fullName ) );
				params.add(new BasicNameValuePair("password", password ));
				params.add(new BasicNameValuePair("user_id", String.valueOf(user.getUser_id()) ));
				params.add(new BasicNameValuePair("login_hash", user.getLogin_hash() ));
				response = DataParser.getJSONFromUrlWithPostRequest(Config.UPDATE_USER_PROFILE_URL, params);
				if(response != null) {
					User user = response.getUser_info();
					if(user != null) {
						photoResponse = uploadPhoto(
								Config.USER_PHOTO_UPLOAD_URL,
								String.valueOf(user.getUser_id()),
								user.getLogin_hash());
					}
				}
			}
		});
        task.execute();
	}
	
	public void updateRegistration(DataResponse response, DataResponse photoResponse) {
		Status status = response.getStatus();
        if(response != null && status != null) {
        	if(status.getStatus_code() == -1 && response.getUser_info() != null ) {
        		User user = response.getUser_info();
        		UserAccessSession session = UserAccessSession.getInstance(ProfileActivity.this);
        		UserSession userSession = new UserSession();
        		userSession.setEmail(user.getEmail());
        		userSession.setFacebook_id(user.getFacebook_id());
        		userSession.setFull_name(user.getFull_name());
        		userSession.setLogin_hash(user.getLogin_hash());
        		userSession.setPhoto_url(user.getPhoto_url());
        		userSession.setThumb_url(user.getThumb_url());
        		userSession.setTwitter_id(user.getTwitter_id());
        		userSession.setUser_id(user.getUser_id());
        		userSession.setUsername(user.getUsername());

        		if(photoResponse != null && photoResponse.getPhoto_user_info() != null) {
        			User userPhoto = photoResponse.getPhoto_user_info();
        			userSession.setPhoto_url(userPhoto.getPhoto_url());
            		userSession.setThumb_url(userPhoto.getThumb_url());
        		}
        		session.storeUserSession(userSession);
        		finish();
        	}
        	else if(status.getStatus_code() == -1 && response.getPhoto_user_info() != null ) {
        		User user = response.getPhoto_user_info();
        		UserAccessSession session = UserAccessSession.getInstance(ProfileActivity.this);
        		UserSession userSession = session.getUserSession();
        		userSession.setPhoto_url(user.getPhoto_url());
        		userSession.setThumb_url(user.getThumb_url());
        		session.storeUserSession(userSession);
        	}
        	else {
        		MGUtilities.showAlertView(ProfileActivity.this, R.string.network_error, status.getStatus_text());
        	}
        }
	}

	@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.e("requestCode", ""+requestCode);
		Log.e("resultCode", ""+resultCode);
		if(requestCode == REGISTER_IMAGE_PICKER_SELECT_THUMB && resultCode == Activity.RESULT_OK ) {
			pathImgThumb = getPathFromCameraData(data, this);
			try {
				Uri uri = data.getData();
				Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
				ImageView imgViewThumb = (ImageView) this.findViewById(R.id.imgViewThumb);
				imgViewThumb.setImageBitmap(bitmap);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if(requestCode == REGISTER_IMAGE_PICKER_SELECT_COVER && resultCode == Activity.RESULT_OK ) {
			pathImgCover = getPathFromCameraData(data, this);
			try {
				Uri uri = data.getData();
				Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
				ImageView imgViewCover = (ImageView) this.findViewById(R.id.imgViewCover);
				imgViewCover.setImageBitmap(bitmap);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
    }

	private DataResponse uploadPhoto(String url, String userId, String loginHash) {
		try {
			HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(url);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            /* example for setting a HttpMultipartMode */
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            /* example for adding an image part */
            builder.addTextBody("user_id", userId);
            builder.addTextBody("login_hash", loginHash);
            if(pathImgThumb == null)
            	builder.addTextBody("thumb_url", user.getThumb_url());
			
			if(pathImgCover == null)
				builder.addTextBody("photo_url", user.getPhoto_url());

            if(pathImgCover != null) {
            	FileBody fileBody = new FileBody( new File(pathImgCover) ); //image should be a String
                builder.addPart("photo_file", fileBody);
            }
            
            if(pathImgThumb != null) {
            	FileBody fileBody = new FileBody( new File(pathImgThumb) ); //image should be a String
                builder.addPart("thumb_file", fileBody);
            }
            
            HttpEntity entity = builder.build();
            httpPost.setEntity(entity);
            HttpResponse httpResponse = httpClient.execute(httpPost);
            final int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                Log.w("Status Code", "Error " + statusCode + " for URL " + url);
                return null;
             }
            
            HttpEntity getResponseEntity = httpResponse.getEntity();
            InputStream source = getResponseEntity.getContent();
            ObjectMapper mapper = new ObjectMapper();
            DataResponse data = new DataResponse();
    		try  {
    			data = mapper.readValue(source, DataResponse.class);
    			return data;
    		} 
    		catch (JsonParseException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} 
    		catch (JsonMappingException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} 
    		catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
        }
		catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } 
		catch (ClientProtocolException e) {
            e.printStackTrace();
        } 
		catch (IOException e) {
            e.printStackTrace();
        }
		return null;
	}

	public static Bitmap getBitmapFromCameraData(Intent data, Context context) {
		String picturePath = getPathFromCameraData(data, context);
		return BitmapFactory.decodeFile(picturePath);
	}

	public static String getPathFromCameraData(Intent data, Context context) {
		Uri selectedImage = data.getData();
		String[] filePathColumn = { MediaStore.Images.Media.DATA };
		Cursor cursor = context.getContentResolver().query(
				selectedImage,filePathColumn, null, null, null); 
		
		cursor.moveToFirst(); 
		int columnIndex = cursor.getColumnIndex(filePathColumn[0]); 
		String picturePath = cursor.getString(columnIndex);
		cursor.close();
		return picturePath;
	}

	private void showLogoutAlertDialog() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
	    alert.setTitle(this.getResources().getString(R.string.alert_logout_user_title));
	    alert.setMessage(this.getResources().getString(R.string.alert_logout_user_title_details));
	    alert.setPositiveButton(this.getResources().getString(R.string.ok),
	    		new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO Auto-generated method stub
				logoutUser();
			}
	    });
	    alert.setNegativeButton(this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO Auto-generated method stub
				dialog.dismiss();
			}
		});
	    alert.create();
	    alert.show();
	}
	
	private void logoutUser() {
		UserAccessSession accessSession = UserAccessSession.getInstance(this);
		if(accessSession != null)
			accessSession.clearUserSession();

		LoginManager.getInstance().logOut();
		logoutTwitter();
        finish();
	}

	public void logoutTwitter() {
		TwitterSession twitterSession = TwitterCore.getInstance().getSessionManager().getActiveSession();
		if (twitterSession != null) {
			ClearCookies(getApplicationContext());
			TwitterCore.getInstance().getSessionManager().clearActiveSession();

		}
	}

	public static void ClearCookies(Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
			CookieManager.getInstance().removeAllCookies(null);
			CookieManager.getInstance().flush();
		} else {
			CookieSyncManager cookieSyncMngr = CookieSyncManager.createInstance(context);
			cookieSyncMngr.startSync();
			CookieManager cookieManager = CookieManager.getInstance();
			cookieManager.removeAllCookie();
			cookieManager.removeSessionCookie();
			cookieSyncMngr.stopSync();
			cookieSyncMngr.sync();
		}
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // toggle nav drawer on selecting action bar app icon/title
        // Handle action bar actions click
        switch (item.getItemId()) {
	        default:
	        	finish();	
	            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_default, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        // if nav drawer is opened, hide the action items
        return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public void onDestroy()  {
        super.onDestroy();
        if(task != null)
        	task.cancel(true);
	}

	private void showAlertDialogDeleteAccount() {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle(this.getResources().getString(R.string.alert_delete_user_title));
		alert.setMessage(this.getResources().getString(R.string.alert_delete_user_title_details));
		alert.setPositiveButton(this.getResources().getString(R.string.proceed),
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						// TODO Auto-generated method stub
						deleteUser();
					}
				});
		alert.setNegativeButton(this.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				// TODO Auto-generated method stub
				dialog.dismiss();
			}
		});
		alert.create();
		alert.show();
	}

	public void deleteUser() {
		if(!MGUtilities.hasConnection(ProfileActivity.this)) {
			MGUtilities.showAlertView(
					ProfileActivity.this,
					R.string.network_error,
					R.string.no_network_connection);
			return;
		}

		task = new MGAsyncTask(ProfileActivity.this);
		task.setMGAsyncTaskListener(new OnMGAsyncTaskListener() {

			DataResponse response;

			@Override
			public void onAsyncTaskProgressUpdate(MGAsyncTask asyncTask) { }

			@Override
			public void onAsyncTaskPreExecute(MGAsyncTask asyncTask) { }

			@Override
			public void onAsyncTaskPostExecute(MGAsyncTask asyncTask) {
				// TODO Auto-generated method stub
				checkResponseDelete(response);
			}

			@Override
			public void onAsyncTaskDoInBackground(MGAsyncTask asyncTask) {
				// TODO Auto-generated method stub
				ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
				params.add(new BasicNameValuePair("full_name", fullName ) );
				params.add(new BasicNameValuePair("password", password ));
				params.add(new BasicNameValuePair("user_id", String.valueOf(user.getUser_id()) ));
				params.add(new BasicNameValuePair("login_hash", user.getLogin_hash() ));
				response = DataParser.getJSONFromUrlWithPostRequest(Config.DELETE_ACCOUNT_URL, params);
			}
		});
		task.execute();
	}

	private void checkResponseDelete(DataResponse response) {
		if(response != null && response.getStatus() != null) {
			Status status = response.getStatus();
			if(status.getStatus_code() == -1) {
				logoutUser();
			}
			else {
				MGUtilities.showAlertView(ProfileActivity.this, R.string.network_error, status.getStatus_text());
			}
		}
		else {
			MGUtilities.showAlertView(ProfileActivity.this, R.string.network_error, R.string.login_error_undetermined);
		}
	}

    private void checkPermissionStorage(final int selector) {
        PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, new PermissionsResultAction() {

                    @Override
                    public void onGranted() {
                        getPicture(selector);
                    }

                    @Override
                    public void onDenied(String permission) {
                        MGUtilities.showAlertView(ProfileActivity.this, R.string.permission_error, R.string.grant_permission_storage);
                    }
                });
    }
}
