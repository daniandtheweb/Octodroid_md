/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.activities;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.gh4a.BasePagerActivity;
import com.gh4a.R;
import com.gh4a.fragment.CommitFragment;
import com.gh4a.fragment.CommitNoteFragment;
import com.gh4a.loader.CommitCommentListLoader;
import com.gh4a.loader.CommitLoader;
import com.gh4a.loader.LoaderCallbacks;
import com.gh4a.loader.LoaderResult;
import com.gh4a.utils.IntentUtils;

import org.eclipse.egit.github.core.CommitComment;
import org.eclipse.egit.github.core.RepositoryCommit;

import java.util.List;

public class CommitActivity extends BasePagerActivity implements
        CommitNoteFragment.CommentUpdateListener {
    public static Intent makeIntent(Context context, String repoOwner, String repoName, String sha) {
        return makeIntent(context, repoOwner, repoName, sha, -1);
    }

    public static Intent makeIntent(Context context, String repoOwner, String repoName,
            String sha, long initialCommentId) {
        return new Intent(context, CommitActivity.class)
                .putExtra("owner", repoOwner)
                .putExtra("repo", repoName)
                .putExtra("sha", sha)
                .putExtra("initial_comment", initialCommentId);
    }

    private String mRepoOwner;
    private String mRepoName;
    private String mObjectSha;

    private RepositoryCommit mCommit;
    private List<CommitComment> mComments;
    private long mInitialCommentId;

    private static final int[] TITLES = new int[] {
        R.string.commit, R.string.issue_comments
    };

    private final LoaderCallbacks<RepositoryCommit> mCommitCallback =
            new LoaderCallbacks<RepositoryCommit>(this) {
        @Override
        protected Loader<LoaderResult<RepositoryCommit>> onCreateLoader() {
            return new CommitLoader(CommitActivity.this, mRepoOwner, mRepoName, mObjectSha);
        }

        @Override
        protected void onResultReady(RepositoryCommit result) {
            mCommit = result;
            showContentIfReady();
        }
    };

    private final LoaderCallbacks<List<CommitComment>> mCommentCallback =
            new LoaderCallbacks<List<CommitComment>>(this) {
        @Override
        protected Loader<LoaderResult<List<CommitComment>>> onCreateLoader() {
            return new CommitCommentListLoader(CommitActivity.this, mRepoOwner, mRepoName,
                    mObjectSha, true, true);
        }

        @Override
        protected void onResultReady(List<CommitComment> result) {
            mComments = result;
            boolean foundComment = false;
            if (mInitialCommentId >= 0) {
                for (CommitComment comment : result) {
                    if (comment.getId() == mInitialCommentId) {
                        foundComment = comment.getPosition() < 0;
                        break;
                    }
                }
                if (!foundComment) {
                    mInitialCommentId = -1;
                }
            }
            showContentIfReady();
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(getString(R.string.commit_title, mObjectSha.substring(0, 7)));
        actionBar.setSubtitle(mRepoOwner + "/" + mRepoName);
        actionBar.setDisplayHomeAsUpEnabled(true);

        setContentShown(false);

        getSupportLoaderManager().initLoader(0, null, mCommitCallback);
        getSupportLoaderManager().initLoader(1, null, mCommentCallback);
    }

    @Override
    protected void onInitExtras(Bundle extras) {
        super.onInitExtras(extras);
        mRepoOwner = extras.getString("owner");
        mRepoName = extras.getString("repo");
        mObjectSha = extras.getString("sha");
        mInitialCommentId = extras.getLong("initial_comment", -1);
    }

    @Override
    protected int[] getTabTitleResIds() {
        return mCommit != null && mComments != null ? TITLES : null;
    }

    @Override
    public void onRefresh() {
        mCommit = null;
        mComments = null;
        setContentShown(false);
        invalidateTabs();
        forceLoaderReload(0, 1);
        super.onRefresh();
    }

    @Override
    protected Fragment getFragment(int position) {
        if (position == 1) {
            Fragment f = CommitNoteFragment.newInstance(mRepoOwner, mRepoName, mObjectSha,
                    mCommit, mComments, mInitialCommentId);
            mInitialCommentId = -1;
            return f;
        } else {
            return CommitFragment.newInstance(mRepoOwner, mRepoName, mObjectSha,
                    mCommit, mComments);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.commit_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected Intent navigateUp() {
        return RepositoryActivity.makeIntent(this, mRepoOwner, mRepoName);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String diffUrl = "https://github.com/" + mRepoOwner + "/" + mRepoName + "/commit/" + mObjectSha;
        switch (item.getItemId()) {
            case R.id.browser:
                IntentUtils.launchBrowser(this, Uri.parse(diffUrl));
                return true;
            case R.id.share:
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_commit_subject,
                        mObjectSha.substring(0, 7), mRepoOwner + "/" + mRepoName));
                shareIntent.putExtra(Intent.EXTRA_TEXT, diffUrl);
                shareIntent = Intent.createChooser(shareIntent, getString(R.string.share_title));
                startActivity(shareIntent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCommentsUpdated() {
        mComments = null;
        setContentShown(false);
        invalidateTabs();
        forceLoaderReload(1);
    }

    private void showContentIfReady() {
        if (mCommit != null && mComments != null) {
            setContentShown(true);
            invalidateTabs();
            if (mInitialCommentId != -1) {
                getPager().setCurrentItem(1);
            }
        }
    }
}