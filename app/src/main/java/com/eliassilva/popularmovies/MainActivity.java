package com.eliassilva.popularmovies;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.eliassilva.popularmovies.data.FavoriteLoader;
import com.eliassilva.popularmovies.data.FavoritesContract;
import com.eliassilva.popularmovies.movies.MovieAdapter;
import com.eliassilva.popularmovies.movies.MovieLoader;
import com.eliassilva.popularmovies.movies.MoviePOJO;
import com.eliassilva.popularmovies.utilities.NetworkReceiver;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Shows a list of posters from movies, according to the sort by option selected
 */
public class MainActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<List<MoviePOJO>>, MovieAdapter.MovieAdapterOnClickHandler, NetworkReceiver.NetworkReceiverListener {
    private MovieAdapter mMovieAdapter;
    private static final String SORT_BY_POPULARITY = "popular";
    private static final String SORT_BY_HIGHEST_RATED = "top_rated";
    private static final String SORT_BY_FAVORITES = "favorites";
    private String mSortBySelected;
    private static final int ID_MOVIE_LOADER = 100;
    private static final int ID_FAVORITES_LOADER = 101;
    private LoaderManager mLoaderManager;
    private NetworkReceiver mReceiver;
    private static final String MOVIE_LIST_STATE_KEY = "view_state";
    GridLayoutManager mLayoutManager;
    private Parcelable mLayoutManagerSavedState;

    @BindView(R.id.movies_list_rv)
    RecyclerView mMoviesList;
    @BindView(R.id.pb_loading_indicator)
    ProgressBar mLoadingIndicator;
    @BindView(R.id.sort_by_spinner)
    Spinner mSpinner;
    @BindView(R.id.empty_view_tv)
    TextView mEmpty_view_tv;
    @BindView(R.id.sort_by_spinner_label)
    TextView mSortByLabel;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mLoaderManager = getSupportLoaderManager();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        mReceiver = new NetworkReceiver();
        this.registerReceiver(mReceiver, filter);
        mReceiver.setNetworkReceiverListener(this);

        mLayoutManager = new GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false);
        mMoviesList.setLayoutManager(mLayoutManager);
        mMoviesList.setHasFixedSize(true);
        mMovieAdapter = new MovieAdapter(this);
        mMoviesList.setAdapter(mMovieAdapter);

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this, R.array.sort_by_array, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinner.setAdapter(spinnerAdapter);

        if (savedInstanceState != null) {
            mLayoutManagerSavedState = savedInstanceState.getParcelable(MOVIE_LIST_STATE_KEY);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiver != null) {
            this.unregisterReceiver(mReceiver);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(MOVIE_LIST_STATE_KEY, mLayoutManager.onSaveInstanceState());
    }

    @Override
    public Loader<List<MoviePOJO>> onCreateLoader(int loaderId, Bundle args) {
        switch (loaderId) {
            case ID_MOVIE_LOADER:
                mLoadingIndicator.setVisibility(View.VISIBLE);
                return new MovieLoader(this, mSortBySelected);
            case ID_FAVORITES_LOADER:
                mLoadingIndicator.setVisibility(View.VISIBLE);
                return new FavoriteLoader(this, FavoritesContract.FavoriteEntry.CONTENT_URI);
            default:
                throw new RuntimeException("Loader Not Implemented: " + loaderId);
        }
    }

    @Override
    public void onLoadFinished(Loader<List<MoviePOJO>> loader, List<MoviePOJO> data) {
        mLoadingIndicator.setVisibility(View.INVISIBLE);
        mMoviesList.setVisibility(View.VISIBLE);
        mSpinner.setVisibility(View.VISIBLE);
        mSortByLabel.setVisibility(View.VISIBLE);
        if (data == null) {
            mEmpty_view_tv.setText(R.string.no_movies);
        }
        mMovieAdapter.setMovieData(data);
        if (mLayoutManagerSavedState != null) {
            mLayoutManager.onRestoreInstanceState(mLayoutManagerSavedState);
        }
    }

    @Override
    public void onLoaderReset(Loader<List<MoviePOJO>> loader) {
        mMovieAdapter.setMovieData(null);
    }

    @Override
    public void onClick(MoviePOJO movie) {
        MoviePOJO dataToSend = new MoviePOJO(movie.getMovieId(), movie.getPosterPath(), movie.getTitle(), movie.getReleaseDate(), movie.getUserRating(), movie.getSynopsis(), movie.getIsFavorite());
        Intent movieDetailIntent = new Intent(MainActivity.this, DetailActivity.class);
        Uri movieUri = FavoritesContract.FavoriteEntry.buildMovieUri(movie.getMovieId());
        Cursor cursor = getContentResolver().query(movieUri, null, null, null, null);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            dataToSend.setIsFavorite(true);
            dataToSend.setPosterPath(cursor.getString(cursor.getColumnIndex(FavoritesContract.FavoriteEntry.COLUMN_POSTER_PATH)));
            cursor.close();
        }
        movieDetailIntent.putExtra("movie_data", dataToSend);
        startActivity(movieDetailIntent);
    }

    @Override
    public void onConnectionChange(boolean wasTrueFlag) {
        if (wasTrueFlag) {
            mEmpty_view_tv.setVisibility(View.GONE);
            mLoaderManager.initLoader(ID_MOVIE_LOADER, null, MainActivity.this);
            mSpinner.setOnItemSelectedListener(new SpinnerActivity());
        } else {
            mMoviesList.setVisibility(View.GONE);
            mLoadingIndicator.setVisibility(View.GONE);
            mSpinner.setVisibility(View.GONE);
            mSortByLabel.setVisibility(View.GONE);
            mEmpty_view_tv.setVisibility(View.VISIBLE);
            mEmpty_view_tv.setText(R.string.no_connection);
            mLoaderManager.initLoader(ID_FAVORITES_LOADER, null, MainActivity.this);
        }
    }

    class SpinnerActivity extends Activity implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            switch (position) {
                case 0:
                    mSortBySelected = SORT_BY_POPULARITY;
                    break;
                case 1:
                    mSortBySelected = SORT_BY_HIGHEST_RATED;
                    break;
                case 2:
                    mSortBySelected = SORT_BY_FAVORITES;
                    break;
            }
            if (mSortBySelected.equals(SORT_BY_FAVORITES)) {
                mLoaderManager.restartLoader(ID_FAVORITES_LOADER, null, MainActivity.this);
            } else {
                mLoaderManager.restartLoader(ID_MOVIE_LOADER, null, MainActivity.this);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            mSortBySelected = SORT_BY_POPULARITY;
        }
    }
}