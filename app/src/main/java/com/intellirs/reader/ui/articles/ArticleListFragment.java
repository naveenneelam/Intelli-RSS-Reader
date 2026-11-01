package com.intellirs.reader.ui.articles;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.ArticleEntity;
import com.intellirs.reader.domain.model.ArticleFilter;
import com.intellirs.reader.ui.MainActivity;
import com.intellirs.reader.ui.archive.StorageActivity;
import com.intellirs.reader.ui.reader.ReaderActivity;
import com.intellirs.reader.ui.reader.ReaderContract;

public class ArticleListFragment extends Fragment implements ArticleAdapter.Listener {
    public static final String ARG_FILTER = "filter";
    public static final String ARG_TITLE = "title";

    private ArticleListViewModel viewModel;
    private ArticleAdapter adapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private View emptyState;
    private ProgressBar loadMoreProgress;
    private LinearLayoutManager layoutManager;
    private String title = "";
    private ArticleFilter filter = ArticleFilter.all();

    public static ArticleListFragment newInstance(ArticleFilter filter, String title) {
        ArticleListFragment fragment = new ArticleListFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_FILTER, filter);
        args.putString(ARG_TITLE, title);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (getArguments() != null) {
            Object arg = getArguments().getSerializable(ARG_FILTER);
            if (arg instanceof ArticleFilter) {
                filter = (ArticleFilter) arg;
            }
            title = getArguments().getString(ARG_TITLE, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_article_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.article_list);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        emptyState = view.findViewById(R.id.empty_state);
        loadMoreProgress = view.findViewById(R.id.load_more_progress);
        adapter = new ArticleAdapter(this);
        layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(ArticleListViewModel.class);
        viewModel.setFilter(filter);
        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.loadInitialIfNeeded();

        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(() -> viewModel.loadInitial());
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) {
                    return;
                }
                int total = layoutManager.getItemCount();
                int last = layoutManager.findLastVisibleItemPosition();
                if (last >= total - 6) {
                    viewModel.loadMore();
                }
            }
        });
        configureAdapter();
        viewModel.container().settingsRepository().observeRevision()
                .observe(getViewLifecycleOwner(), revision -> configureAdapter());
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setScreenTitle(title);
            ((MainActivity) getActivity()).setAddFeedVisible(false);
        }
    }

    private void configureAdapter() {
        if (adapter == null) {
            return;
        }
        adapter.configure(
                viewModel.container().settingsRepository().getListDensity(),
                viewModel.container().settingsRepository().showThumbnails(),
                viewModel.container().settingsRepository().showSummaries(),
                viewModel.container().settingsRepository().isAllowHttp(),
                filter.mode != ArticleFilter.Mode.FEED);
    }

    private void render(ArticleListState state) {
        if (state == null) {
            return;
        }
        swipeRefresh.setRefreshing(false);
        loadMoreProgress.setVisibility(state.loadingMore ? View.VISIBLE : View.GONE);
        adapter.submitList(state.articles);
        boolean empty = !state.loading && state.articles.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (state.error != null && isAdded()) {
            Snackbar.make(requireView(),
                    getString(R.string.articles_load_error, state.error),
                    Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null && viewModel.consumeReloadRequest()) {
            viewModel.loadInitial();
        }
        if (adapter != null) {
            configureAdapter();
        }
    }

    @Override
    public void onArticleClick(ArticleEntity article) {
        viewModel.markRead(article.id, true);
        viewModel.reloadOnResume();
        Intent intent = new Intent(requireContext(), ReaderActivity.class);
        intent.putExtra(ReaderContract.EXTRA_ARTICLE_ID, article.id);
        intent.putExtra(ReaderContract.EXTRA_FILTER, filter);
        startActivity(intent);
    }

    @Override
    public void onBookmarkClick(ArticleEntity article) {
        viewModel.toggleBookmark(article.id);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.article_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_list_density) {
            showDensityDialog();
            return true;
        }
        if (id == R.id.action_mark_all_read) {
            confirmMarkAllRead();
            return true;
        }
        if (id == R.id.action_archive_storage) {
            startActivity(new Intent(requireContext(), StorageActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDensityDialog() {
        String[] values = new String[]{"compact", "comfortable", "image_rich"};
        String[] labels = new String[]{
                getString(R.string.density_compact),
                getString(R.string.density_comfortable),
                getString(R.string.density_image_rich)
        };
        String current = viewModel.container().settingsRepository().getListDensity();
        int selected = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                selected = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.list_density)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    viewModel.container().settingsRepository().setListDensity(values[which]);
                    configureAdapter();
                    dialog.dismiss();
                    Snackbar.make(requireView(),
                            getString(R.string.density_changed, labels[which]),
                            Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmMarkAllRead() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.action_mark_all_read)
                .setMessage(R.string.articles_mark_all_read_confirm)
                .setPositiveButton(R.string.action_mark_all_read, (dialog, which) -> {
                    viewModel.markAllRead();
                    Snackbar.make(requireView(), R.string.articles_marked_read,
                            Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
