package com.example.downloaderdemo.fragment;


import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.SearchRecentSuggestions;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.downloaderdemo.R;
import com.example.downloaderdemo.event.OnListItemClickEvent;
import com.example.downloaderdemo.event.QueryEvent;
import com.example.downloaderdemo.event.ResultQueryEvent;
import com.example.downloaderdemo.model.Article;
import com.example.downloaderdemo.model.Journal;
import com.example.downloaderdemo.model.JournalInfo;
import com.example.downloaderdemo.model.KeywordList;
import com.example.downloaderdemo.ui.ChoiceCapableAdapter;
import com.example.downloaderdemo.ui.HorizontalDivider;
import com.example.downloaderdemo.ui.SingleChoiceMode;
import com.example.downloaderdemo.util.DatabaseHelper;
import com.example.downloaderdemo.util.QueryPreferences;
import com.example.downloaderdemo.util.QuerySuggestionProvider;
import com.example.downloaderdemo.util.Utils;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import timber.log.Timber;

/**
 * References:
 *
 * RecyclerView
 * [1] https://www.safaribooksonline.com/library/view/android-programming-the/9780134171517/ch25s02.html (SearchView setup)
 * [2] https://www.safaribooksonline.com/library/view/android-programming-the/9780134171517/ch25s03.html (Save query to shared prefs)
 *
 * SearchView
 * [3] https://developer.android.com/training/search/setup.html
 * [4] http://antonioleiva.com/actionbarcompat-action-views/
 * [5] http://developer.android.com/guide/topics/search/adding-recent-query-suggestions.html
 *
 * Endless scrolling on a RecyclerView
 * [4] http://stackoverflow.com/questions/26543131/how-to-implement-endless-list-with-recyclerview
 * [5] http://androhub.com/load-more-items-on-scroll-android/
 *
 * SingleChoiceMode
 * [6] The Busy Coder's Guide to Android Development (https://commonsware.com/Android) p1260
 *
 * SQLiteDatabase
 * [7] The Busy Coder's Guide to Android Development (https://commonsware.com/Android) p598-608
 *
 */

public class DownloadFragment extends BaseFragment{

    private static final String SAVED_CURRENT_PAGE = "current page";
    private static final String SAVED_LOADING = "loading";
    private static final String SAVED_QUERY = "query";

    private ArrayList<Article> mArticleItems = new ArrayList<>();
    private static SearchRecentSuggestions sRecentSuggestions;
    private SearchView mSearchView;
    private MenuItem mSearchMenuItem;
    private ChoiceCapableAdapter<?> mAdapter;
    private RecyclerView mRecyclerView;
    private TextView mEmptyView;
    private LinearLayoutManager mLayoutManager;
    private String mQuery;
    private boolean mFirstTimeIn;
    private View mView;
    private boolean mNewQuerySubmitted;
    private DatabaseHelper mHelper;
    private AsyncTask mTask;
    private String[] mProjection = {
            //"ROWID AS _id",
            DatabaseHelper.ROW_ID,
            DatabaseHelper.ARTICLE_ID,
            DatabaseHelper.ARTICLE_TITLE,
            DatabaseHelper.JOURNAL_TITLE,
            DatabaseHelper.AUTHOR_STRING,
            DatabaseHelper.PAGE_INFO,
            DatabaseHelper.ABSTRACT_TEXT,
            DatabaseHelper.KEYWORD_LIST,
            DatabaseHelper.VOLUME,
            DatabaseHelper.ISSUE,
            DatabaseHelper.YEAR_OF_PUBLICATION,
            DatabaseHelper.CITED
    };

    // endless scrolling variables
    private boolean mLoading = true;
    private int mPreviousTotal, mVisibleThreshold = 5, mFirstVisibleItem, mVisibleItemCount,
                    mTotalItemCount, mCurrentPage;

    public DownloadFragment() { }

    public static DownloadFragment newInstance() {
        return new DownloadFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if(mTask != null) {
            // cancel any tasks that have not completed
            mTask.cancel(false);
        }

        // close dbase connection
        mHelper.close();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if(savedInstanceState != null) {
            mAdapter.onRestoreInstanceState(savedInstanceState);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // RecyclerView recyclerView = (RecyclerView) inflater.inflate(R.layout.recycler_view, container, false);
        mView = inflater.inflate(R.layout.recycler_view, container, false);
        mEmptyView = (TextView) mView.findViewById(R.id.empty_view);
        mRecyclerView = (RecyclerView) mView.findViewById(R.id.recycler_view);

        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);
        Drawable itemDivider = ContextCompat.getDrawable(getActivity(), R.drawable.item_divider);
        mRecyclerView.addItemDecoration(new HorizontalDivider(itemDivider));

        mAdapter = new JournalAdapter(mRecyclerView);
        mRecyclerView.setAdapter(mAdapter);

        mHelper = DatabaseHelper.getInstance(getActivity());

        if(savedInstanceState == null) {
            mFirstTimeIn = true;
            mCurrentPage = 1;
            mLoading = false;
            // execute the last saved query
            // mQuery = QueryPreferences.getSavedQueryString(getActivity());
            // if(mQuery != null) {
                // getSearchResults(); // execute search in background thread

                // load results from database
            mTask = new QueryTask().execute();

            //} else {
             //   Utils.showSnackbar(getActivity().findViewById(R.id.coordinator_layout), "Enter a search query");
            //}
        } else {
            // re-set fragment state
            mCurrentPage = savedInstanceState.getInt(SAVED_CURRENT_PAGE);
            mLoading = savedInstanceState.getBoolean(SAVED_LOADING);
            mQuery = savedInstanceState.getString(SAVED_QUERY);
        }


        if(mArticleItems.size() == 0) {
            mEmptyView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
        }


        // implement endless scrolling
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                mVisibleItemCount = recyclerView.getChildCount();
                mTotalItemCount = mLayoutManager.getItemCount();
                mFirstVisibleItem = mLayoutManager.findFirstVisibleItemPosition();

                if(mLoading) {
                    if(mTotalItemCount > mPreviousTotal) {
                        mLoading = false;
                        mPreviousTotal = mTotalItemCount;
                    }
                }
                if(!mLoading && (mTotalItemCount - mVisibleItemCount)
                        <= (mFirstVisibleItem  + mVisibleThreshold)) {
                    // end of page has been reached, download more items
                    mLoading = true;
                    getSearchResults();
                }
            }
        });

        return mView;
    }


    private void getSearchResults() {
        if(Utils.isClientConnected(getActivity())) {
            postToAppBus(new QueryEvent(mQuery, mCurrentPage));
        } else {
            Utils.showSnackbar(getActivity().findViewById(R.id.coordinator_layout), "No network connection");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SAVED_CURRENT_PAGE, mCurrentPage);
        outState.putBoolean(SAVED_LOADING, mLoading);
        outState.putString(SAVED_QUERY, mQuery);

        mAdapter.onSaveInstanceState(outState);
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_search, menu);

        // configure search view
        SearchManager mgr = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        mSearchMenuItem = menu.findItem(R.id.search_view);
        mSearchView = (SearchView) mSearchMenuItem.getActionView();
        mSearchView.setSearchableInfo(mgr.getSearchableInfo(getActivity().getComponentName()));
        mSearchView.setSubmitButtonEnabled(true);
        mSearchView.setQueryRefinementEnabled(true);
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (mQuery != null && !mQuery.equals(query)) {
                    // new query has been submitted
                    mNewQuerySubmitted = true;
                }
                mQuery = query;

                // reset the page number
                mCurrentPage = 1;

                // hide the soft keyboard
                Utils.hideKeyboard(getActivity(), mSearchView.getWindowToken());

                // close the search view
                mSearchMenuItem.collapseActionView();

                // save the search query to the RecentSuggestionsProvider
                sRecentSuggestions = new SearchRecentSuggestions(getActivity(),
                        QuerySuggestionProvider.AUTHORITY, QuerySuggestionProvider.MODE);
                sRecentSuggestions.saveRecentQuery(mQuery, null);

                // post the query submitted by the user to the bus and save it to shared prefs
                //postToAppBus(new QueryEvent(mQuery, mCurrentPage));
                getSearchResults();
                QueryPreferences.setSavedQueryString(getActivity(), mQuery);

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // no-op
                return false;
            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.clear_search) {

            // clear user's search history & shared prefs
            if(sRecentSuggestions != null) {
                ConfirmationDialogFragment dialog = new ConfirmationDialogFragment();
                dialog.show(getFragmentManager(), "Clear history");
            } else {
                Utils.showSnackbar(mView, "History clear");
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Subscribe
    public void getResultsOfSearchQuery(ResultQueryEvent event) {

        // clear the arraylist if a new query has been submitted
        if(mNewQuerySubmitted) {
            mNewQuerySubmitted = false;
            mArticleItems.clear();
            mPreviousTotal = 0;

            // delete records from the database
            mTask =  new DeleteTask().execute();
        }

        List<Article> resultList = event.getResultQuery().getResultList().getResult();
        if(resultList.size() > 0) {

            // adding the results to the dbase - automatically executes a query which updates the UI
            Article[] list = resultList.toArray(new Article[resultList.size()]);
            mTask = new InsertTask().execute(list);

            // add new results to the adapter
            //mArticleItems.addAll(resultList); // ??
            //mAdapter.notifyDataSetChanged(); // ??

            mRecyclerView.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
            if(mCurrentPage > 1) {
                Utils.showSnackbar(mView, "Downloading more items");
            }
            ++mCurrentPage;

        } else {
            // no results returned
            mRecyclerView.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
            Utils.showSnackbar(mView, "No results found");

        }

    }


    // populate the arraylist on device rotation
    public void setModelDataSet(ArrayList<Article> list) {
        mArticleItems = list;
    }


    private class JournalAdapter extends ChoiceCapableAdapter<JournalViewHolder> {


        public JournalAdapter(RecyclerView recyclerView) {
            super(recyclerView, new SingleChoiceMode());
        }


        @Override
        public JournalViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

            // create the viewholder
            return (new JournalViewHolder(this, getActivity().getLayoutInflater()
                            .inflate(R.layout.list_item, parent, false)));
        }

        @Override
        public void onBindViewHolder(JournalViewHolder holder, int position) {
            // populate the viewholder
            holder.bindJournal(mArticleItems.get(position));
        }

        @Override
        public int getItemCount() {
            return mArticleItems.size();
        }

        public void clearAll() {
            mArticleItems.clear();
            notifyDataSetChanged();
        }

        public void addAll(List<Article> items) {
            mArticleItems.addAll(items);
            notifyDataSetChanged();
        }

    }


    public class JournalViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{

        private Article mArticle;
        private ChoiceCapableAdapter mAdapter;
        private View mView;

        TextView articleTitle = null;
        TextView journalTitle = null;
        TextView articleAuthors = null;
        TextView pageInformation = null;
        TextView journalIssue = null;
        TextView journalVolume = null;
        TextView yearOfPublication = null;
        //TextView cited = null;

        public JournalViewHolder(ChoiceCapableAdapter adapter, View view) {
            super(view);
            mAdapter = adapter;
            mView = view;

            articleTitle = (TextView) mView.findViewById(R.id.article_title);
            journalTitle = (TextView) mView.findViewById(R.id.journal_title);
            articleAuthors = (TextView) mView.findViewById(R.id.article_authors);
            pageInformation = (TextView) mView.findViewById(R.id.page_information);
            journalIssue = (TextView) mView.findViewById(R.id.journal_issue);
            journalVolume = (TextView) mView.findViewById(R.id.journal_volume);
            yearOfPublication = (TextView) mView.findViewById(R.id.publication_year);
            //cited = (TextView) view.findViewById(R.id.cited_times);

            mView.setOnClickListener(this);
        }


        public void bindJournal(Article article) {

            // article object read back from the database
            mArticle = article;

            articleTitle.setText(mArticle.getTitle());
            articleAuthors.setText(mArticle.getAuthorString());
            pageInformation.setText(mArticle.getPageInfo());

            if(mArticle.getJournalInfo().getYearOfPublication() == 0) {
                yearOfPublication.setText(getString(R.string.na_label));
            } else {
                yearOfPublication.setText(String.valueOf(mArticle.getJournalInfo().getYearOfPublication()));
            }

            journalVolume.setText(mArticle.getJournalInfo().getVolume());
            journalIssue.setText(mArticle.getJournalInfo().getIssue());
            journalTitle.setText(mArticle.getJournalInfo().getJournal().getTitle());

            setChecked(mAdapter.isChecked(getAdapterPosition()));
        }

        public void setChecked(boolean isChecked) {
            mView.setActivated(isChecked);
        }

        @Override
        public void onClick(View view) {
            // post on click event to the bus
            postToAppBus(new OnListItemClickEvent(mArticle));

            // handle checked status
            boolean isCheckedNow = mAdapter.isChecked(getAdapterPosition());
            mAdapter.onChecked(getAdapterPosition(), !isCheckedNow);
            mView.setActivated(!isCheckedNow);
        }


    }


    private String validateStringValue(String value) {
        if(value != null && !value.equals("")){
            return value;
        } else {
            return getString(R.string.na_label);
        }
    }


    private String validateLongValue(Long value) {
        if(value != null)
            return String.valueOf(value);
        else
            return getString(R.string.na_label);
    }


    public static class ConfirmationDialogFragment extends DialogFragment {

        public ConfirmationDialogFragment() {}

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.confirmation_dialog_message)
                    .setPositiveButton(R.string.confirmation_dialog_positive_button,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    sRecentSuggestions.clearHistory();
                                    sRecentSuggestions = null;
                                    Utils.showSnackbar(getActivity().findViewById(R.id.coordinator_layout), "Search history cleared");

                                    // clear the saved query from shared prefs
                                    QueryPreferences.setSavedQueryString(getActivity(), null);

                                    Timber.i("Cleared shared prefs & SearchView history");
                                }
                            })
                    .setNegativeButton(R.string.confirmation_dialog_negative_button,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    Utils.showSnackbar(getActivity().findViewById(R.id.coordinator_layout), "Action cancelled");
                                }
                            });


            return builder.create();
        }
    }


    // database helper methods
    private abstract class BaseTask<T> extends AsyncTask<T, Void, Cursor> {

        @Override
        protected void onPostExecute(Cursor cursor) {

            if(cursor.getCount() == 0) {
                Utils.showSnackbar(mView, "Enter a search query");
                return;
            }

            // TODO delete dbase after first time on subsequent searches, results added to previous
            // check if query=null and record count

            // convert the cursor to an arraylist of Article Pojos update the adapter
            Article article;
            JournalInfo journalInfo = new JournalInfo();
            Journal journal = new Journal();
            KeywordList keywordList = new KeywordList();
            mArticleItems.clear();
            while(cursor.moveToNext()) {
                article = new Article();
                // populate article fields
                article.setRowid(cursor.getInt(cursor.getColumnIndex(DatabaseHelper.ROW_ID)));
                article.setId(cursor.getString(cursor.getColumnIndex(DatabaseHelper.ARTICLE_ID)));
                article.setTitle(cursor.getString(cursor.getColumnIndex(DatabaseHelper.ARTICLE_TITLE)));
                article.setAuthorString(cursor.getString(cursor.getColumnIndex(DatabaseHelper.AUTHOR_STRING)));
                article.setPageInfo(cursor.getString(cursor.getColumnIndex(DatabaseHelper.PAGE_INFO)));
                article.setAbstractText(cursor.getString(cursor.getColumnIndex(DatabaseHelper.ABSTRACT_TEXT)));
                article.setCitedByCount(Long.valueOf(cursor.getString(cursor.getColumnIndex(DatabaseHelper.CITED))));

                // populate journalInfo fields
                journalInfo.setIssue(cursor.getString(cursor.getColumnIndex(DatabaseHelper.ISSUE)));
                journalInfo.setVolume(cursor.getString(cursor.getColumnIndex(DatabaseHelper.VOLUME)));
                journalInfo.setYearOfPublication(Long.valueOf(cursor.getString(cursor.getColumnIndex(DatabaseHelper.YEAR_OF_PUBLICATION))));

                // populate Journal field
                journal.setTitle(cursor.getString(cursor.getColumnIndex(DatabaseHelper.JOURNAL_TITLE)));

                // populate Keywords List field
                List<String> keywords = null;
                String str = cursor.getString(cursor.getColumnIndex(DatabaseHelper.KEYWORD_LIST));
                if(!str.equals(getString(R.string.na_label))) {
                    keywords = Arrays.asList(str.split("\\s*,\\s*"));
                }
                keywordList.setKeyword(keywords);

                // add the objects to article
                journalInfo.setJournal(journal);
                article.setJournalInfo(journalInfo);
                article.setKeywordList(keywordList);

                // add each article to the List of results
                mArticleItems.add(article);
            }

            // sort the arraylist into order by id
//            Collections.sort(mArticleItems, new Comparator<Article>() {
//                @Override
//                public int compare(Article lhs, Article rhs) {
//                    return lhs.getRowid().compareTo(rhs.getRowid());
//                }
//            });

            // update the UI & close the cursor
            mAdapter.notifyDataSetChanged();
            mRecyclerView.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);

            // FIXME
            if(mFirstTimeIn) {
                Utils.showSnackbar(mView, "Previously saved results");
                mFirstTimeIn = false;
            }
            cursor.close();

        }

        String sortOrder = DatabaseHelper.ROW_ID + " ASC";
        // execute query in doInBackground() of the insert/query/delete tasks
        // resulting cursor passed to onPostExecute()
        protected Cursor doQuery() {
            Cursor result = mHelper.getReadableDatabase()
                    .query(DatabaseHelper.TABLE_NAME,
                            mProjection,
                            null,
                            null,
                            null,
                            null,
                            sortOrder);
            result.getCount(); // ensure the query is executed
            Timber.i("Query returned %d rows", result.getCount());
            return result;
        }

    }


    // query the database, returning all records based on the projection
    private class QueryTask extends BaseTask<Void> {
        @Override
        protected Cursor doInBackground(Void... params) {
            Timber.i("Executing query task on background thread");
            return doQuery();
        }
    }


    // insert record into the database
    private class InsertTask extends BaseTask<Article> {
        @Override
        protected Cursor doInBackground(Article... params) {

            Timber.i("Executing insert task on background thread");

            // iterate through the array of articles, inserting each in turn
            ContentValues values = new ContentValues();
            SQLiteDatabase db = mHelper.getWritableDatabase();

            for (Article article : params) {
                values.clear();
                values.put(DatabaseHelper.ARTICLE_ID, validateStringValue(article.getId()));
                values.put(DatabaseHelper.ARTICLE_TITLE, validateStringValue(article.getTitle()));
                values.put(DatabaseHelper.AUTHOR_STRING, validateStringValue(article.getAuthorString()));
                values.put(DatabaseHelper.PAGE_INFO, validateStringValue(article.getPageInfo()));
                values.put(DatabaseHelper.ABSTRACT_TEXT, validateStringValue(article.getAbstractText()));

                if(article.getCitedByCount() != null) {
                    values.put(DatabaseHelper.CITED, article.getCitedByCount());
                } else {
                    values.put(DatabaseHelper.CITED, getString(R.string.no_number));
                }

                if(article.getJournalInfo() != null) {
                    values.put(DatabaseHelper.VOLUME, validateStringValue(article.getJournalInfo().getVolume()));
                    values.put(DatabaseHelper.ISSUE, validateStringValue(article.getJournalInfo().getIssue()));

                    if(article.getJournalInfo().getYearOfPublication() != null) {
                        values.put(DatabaseHelper.YEAR_OF_PUBLICATION, article.getJournalInfo().getYearOfPublication());
                    } else {
                        values.put(DatabaseHelper.YEAR_OF_PUBLICATION, getString(R.string.no_number));
                    }

                    if(article.getJournalInfo().getJournal() != null) {
                        values.put(DatabaseHelper.JOURNAL_TITLE, validateStringValue(article.getJournalInfo().getJournal().getTitle()));
                    } else {
                        values.put(DatabaseHelper.JOURNAL_TITLE, getString(R.string.app_name));
                    }
                } else {
                    values.put(DatabaseHelper.VOLUME, getString(R.string.na_label));
                    values.put(DatabaseHelper.ISSUE, getString(R.string.na_label));
                    values.put(DatabaseHelper.YEAR_OF_PUBLICATION, getString(R.string.no_number));
                    values.put(DatabaseHelper.JOURNAL_TITLE, getString(R.string.na_label));
                }

                if(article.getKeywordList() != null && article.getKeywordList().getKeyword() != null) {
                    values.put(DatabaseHelper.KEYWORD_LIST, validateStringValue(article.getKeywordList().getKeyword().toString()));
                } else {
                    values.put(DatabaseHelper.KEYWORD_LIST, getString(R.string.na_label));
                }

                db.insert(DatabaseHelper.TABLE_NAME, DatabaseHelper.ARTICLE_TITLE, values);
            }
            return doQuery(); // ensure view is refreshed
        }
    }


    // delete records from the database
    private class DeleteTask extends BaseTask<Void> {
        @Override
        protected Cursor doInBackground(Void... params) {
            Timber.i("Executing delete task on background thread");
            // delete the table
            mHelper.getWritableDatabase().delete(DatabaseHelper.TABLE_NAME, null, null);
            return doQuery();
        }
    }

}
