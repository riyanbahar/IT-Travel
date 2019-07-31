package com.projects.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.application.StoreFinderApplication;
import com.config.Config;
import com.config.UIConfig;
import com.db.Queries;
import com.libraries.adapters.MGRecyclerAdapter;
import com.libraries.asynctask.MGAsyncTask;
import com.libraries.asynctask.MGAsyncTaskNoDialog;
import com.libraries.dataparser.DataParser;
import com.libraries.helpers.DateTimeHelper;
import com.libraries.imageview.RoundedImageView;
import com.libraries.usersession.UserAccessSession;
import com.libraries.usersession.UserSession;
import com.libraries.utilities.MGUtilities;
import com.models.DataResponse;
import com.models.ResponseReview;
import com.models.Review;
import com.models.Status;
import com.models.Store;
import com.apps.storefinder.R;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;

public class ReviewActivity extends AppCompatActivity implements OnItemClickListener {

	private Store store;
	private int reviewCount;
	private ResponseReview response;
	private int NEW_REVIEW_REQUEST_CODE = 9901;
	MGAsyncTaskNoDialog task;
    MGAsyncTask task1;
	SwipeRefreshLayout swipeRefresh;
	RecyclerView mRecyclerView;
	RecyclerView.LayoutManager mLayoutManager;
	Queries q;
	UserSession userSession;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
		setContentView(R.layout.fragment_list_swipe);
		setTitle(R.string.store_reviews);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		store = (Store) this.getIntent().getSerializableExtra("store");
		reviewCount = Config.MAX_REVIEW_COUNT_PER_LISTING;
		response = (ResponseReview) this.getIntent().getSerializableExtra("response");
		q = StoreFinderApplication.getQueriesInstance(this);

		mRecyclerView = (RecyclerView) findViewById(R.id.recyclerView);
		mRecyclerView.setHasFixedSize(true);

		mLayoutManager = new LinearLayoutManager(this);
		mRecyclerView.setLayoutManager(mLayoutManager);

		swipeRefresh = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh);
		swipeRefresh.setClickable(false);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			swipeRefresh.setProgressViewOffset(false, 0,100);
		}

		userSession = UserAccessSession.getInstance(ReviewActivity.this).getUserSession();

		swipeRefresh.setColorSchemeResources(
				android.R.color.holo_blue_bright,
				android.R.color.holo_green_light,
				android.R.color.holo_orange_light,
				android.R.color.holo_red_light);

		Handler h = new Handler();
		h.postDelayed(new Runnable() {
			
			@Override
			public void run() {
				// TODO Auto-generated method stub
				if(response == null) {
					getReviews();
				}
				else {
					showList();
				}
			}
		}, Config.DELAY_SHOW_ANIMATION);
		showRefresh(true);
	}

	public void showRefresh(boolean show) {
		swipeRefresh.setRefreshing(show);
		swipeRefresh.setEnabled(show);
	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		if(task != null)
			task.cancel(true);
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // toggle nav drawer on selecting action bar app icon/title
        // Handle action bar actions click
        switch (item.getItemId()) {
	        case R.id.menuNewReview:
	        	newReview();
	            return true;
	        default:
	        	finish();	
	            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_reviews, menu);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        // if nav drawer is opened, hide the action items
        return super.onPrepareOptionsMenu(menu);
    }
    
    private void newReview() {
    	UserAccessSession userAccess = UserAccessSession.getInstance(ReviewActivity.this);
		UserSession userSession = userAccess.getUserSession();
		if(userSession == null) {
			MGUtilities.showAlertView(ReviewActivity.this, R.string.login_error, R.string.login_error_review);
			return;
		}
		Intent i = new Intent(this, NewReviewActivity.class);
		i.putExtra("store", store);
		startActivityForResult(i, NEW_REVIEW_REQUEST_CODE);
    }
	
	public void getReviews() {
        showRefresh(true);
		if(!MGUtilities.hasConnection(this)) {
			MGUtilities.showAlertView(
					this, 
					R.string.network_error,
					R.string.no_network_connection);
			showRefresh(false);
			return;
		}
		
        task = new MGAsyncTaskNoDialog(ReviewActivity.this);
        task.setMGAsyncTaskListener(new MGAsyncTaskNoDialog.OnMGAsyncTaskListenerNoDialog() {
			
			@Override
			public void onAsyncTaskProgressUpdate(MGAsyncTaskNoDialog asyncTask) { }
			
			@Override
			public void onAsyncTaskPreExecute(MGAsyncTaskNoDialog asyncTask) { }
			
			@Override
			public void onAsyncTaskPostExecute(MGAsyncTaskNoDialog asyncTask) {
				// TODO Auto-generated method stub
				showList();
				showRefresh(false);
			}
			
			@Override
			public void onAsyncTaskDoInBackground(MGAsyncTaskNoDialog asyncTask) {
				// TODO Auto-generated method stub
				parseReviews();
			}
		});
        task.execute();
	}
	
	@SuppressLint("DefaultLocale")
	public void parseReviews() {
		String reviewUrl = String.format("%s?count=%d&store_id=%s", Config.REVIEWS_URL, reviewCount, store.getStore_id());
        response = DataParser.getJSONFromUrlReview(reviewUrl, null);
        if(response != null) {
        	if(response.getReturn_count() < response.getTotal_row_count()) {
                if(response.getReviews() != null) {
                	Review review = new Review();
                	review.setReview_id(-1);
                	response.getReviews().add(0, review);
                }
            }
        }
	}

	private void showList() {
		if(response.getReviews() == null)
			return;

		MGRecyclerAdapter adapter = new MGRecyclerAdapter(response.getReviews().size(), R.layout.review_entry);
		adapter.setOnMGRecyclerAdapterListener(new MGRecyclerAdapter.OnMGRecyclerAdapterListener() {

			@Override
			public void onMGRecyclerAdapterCreated(MGRecyclerAdapter adapter, MGRecyclerAdapter.ViewHolder v, final int position) {
				final Review review = response.getReviews().get(position);
				LinearLayout linearLoadMore = (LinearLayout) v.view.findViewById(R.id.linearLoadMore);
				LinearLayout linearMain = (LinearLayout) v.view.findViewById(R.id.linearMain);
				linearLoadMore.setVisibility(View.VISIBLE);
				linearMain.setVisibility(View.VISIBLE);
				if(review.getReview_id() > 0) {
					linearLoadMore.setVisibility(View.GONE);
//					String reviewString = URLDecoder.decode(details2.toString());
					TextView tvTitle = (TextView) v.view.findViewById(R.id.tvTitle);
					tvTitle.setText(MGUtilities.formatHTML(review.getFull_name()));

					TextView tvDetails = (TextView) v.view.findViewById(R.id.tvDetails);
					tvDetails.setText(MGUtilities.formatHTML(review.getReview()));

					RoundedImageView imgViewPhoto = (RoundedImageView) v.view.findViewById(R.id.imgViewThumb);
					imgViewPhoto.setCornerRadius(R.dimen.corner_radius_review);
					imgViewPhoto.setBorderWidth(UIConfig.BORDER_WIDTH);
					imgViewPhoto.setBorderColor(getResources().getColor(UIConfig.THEME_BLACK_COLOR));

					if(review.getThumb_url() != null) {
						StoreFinderApplication.getImageLoaderInstance(ReviewActivity.this).displayImage(
								review.getThumb_url(),
								imgViewPhoto,
								StoreFinderApplication.getDisplayImageOptionsThumbInstance());
					}

					String date = DateTimeHelper.getStringDateFromTimeStamp(review.getCreated_at(), "MM/dd/yyyy hh:mm a");
					TextView tvDatePosted = (TextView) v.view.findViewById(R.id.tvDatePosted);
					tvDatePosted.setText(date);

					ImageView imgDelete = (ImageView) v.view.findViewById(R.id.imgDelete);
					imgDelete.setVisibility(View.INVISIBLE);

					if(Config.ALLOW_COMMENT_DELETION) {
                        if(userSession != null && review.getReview_id() > 0 && review.getuser_id() ==  userSession.getUser_id()) {
                            imgDelete.setVisibility(View.VISIBLE);
                        }
                    }
				}
				else if(review.getReview_id() == -1) {
					linearMain.setVisibility(View.GONE);
					int remaining = response.getTotal_row_count() - response.getReturn_count();
					String str = String.format("%s %d %s",
							MGUtilities.getStringFromResource(ReviewActivity.this, R.string.view),
							remaining,
							MGUtilities.getStringFromResource(ReviewActivity.this, R.string.comments));

					TextView tvTitle = (TextView) v.view.findViewById(R.id.tvLoadMore);
					tvTitle.setText(str);
				}


				linearLoadMore.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						onItemClick(position);
					}
				});

				linearMain.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						onItemClick(position);
					}
				});
			}

		});
		mRecyclerView.setAdapter(adapter);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
	    if (requestCode == NEW_REVIEW_REQUEST_CODE) {
	        if(resultCode == Activity.RESULT_OK) {
	        	getReviews();
	        }
	        else if (resultCode == Activity.RESULT_CANCELED) {
	            //Write your code if there's no result
	        }
	    }
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View v, int pos, long resid) {
		// TODO Auto-generated method stub

	}

	public void onItemClick(int pos) {
		// TODO Auto-generated method stub
		Review review = response.getReviews().get(pos);
		if(review.getReview_id() == -1 ) {
			if(!MGUtilities.hasConnection(ReviewActivity.this)) {
				MGUtilities.showAlertView(
						ReviewActivity.this,
						R.string.network_error,
						R.string.no_network_connection);
				return;
			}
			reviewCount += Config.MAX_REVIEW_COUNT_PER_LISTING;
			getReviews();
		}
		else if(userSession != null && review.getReview_id() > 0 && review.getuser_id() ==  userSession.getUser_id()) {
			if(Config.ALLOW_COMMENT_DELETION) {
                showAlertDialogDeleteReview(review);
            }
		}
	}

    private void showAlertDialogDeleteReview(final Review review) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(this.getResources().getString(R.string.alert_delete_review_title));
        alert.setMessage(this.getResources().getString(R.string.alert_delete_review_title_details));
        alert.setPositiveButton(this.getResources().getString(R.string.yes),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO Auto-generated method stub
                        deleteReview(review);
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

    public void deleteReview(final Review review) {
        if(!MGUtilities.hasConnection(this)) {
            MGUtilities.showAlertView(
                    this,
                    R.string.network_error,
                    R.string.no_network_connection);
            showRefresh(false);
            return;
        }

        final UserSession userSession = UserAccessSession.getInstance(this).getUserSession();
        task1 = new MGAsyncTask(this);
        task1.setMGAsyncTaskListener(new MGAsyncTask.OnMGAsyncTaskListener() {

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
                params.add(new BasicNameValuePair("review_id", String.valueOf(review.getReview_id()) ) );
                params.add(new BasicNameValuePair("store_id", String.valueOf(review.getStore_id()) ));
                params.add(new BasicNameValuePair("user_id", String.valueOf(userSession.getUser_id()) ));
                params.add(new BasicNameValuePair("login_hash", userSession.getLogin_hash() ));
                response = DataParser.getJSONFromUrlWithPostRequest(Config.DELETE_REVIEW_URL, params);
            }
        });
        task1.execute();
    }

    private void checkResponseDelete(DataResponse response) {
        if(response != null && response.getStatus() != null) {
            Status status = response.getStatus();
            if(status.getStatus_code() == -1) {
                getReviews();
            }
            else {
                MGUtilities.showAlertView(this, R.string.network_error, status.getStatus_text());
            }
        }
        else {
            MGUtilities.showAlertView(this, R.string.network_error, R.string.login_error_undetermined);
        }
    }
}
