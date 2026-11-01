package com.intellirs.reader.ui.categories;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.intellirs.reader.R;
import com.intellirs.reader.data.entity.CategoryEntity;
import com.intellirs.reader.ui.BaseActivity;

import java.util.List;

public class CategoriesActivity extends BaseActivity {
    private CategoryAdapter adapter;
    private View emptyState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_categories);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(view -> finish());
        RecyclerView list = findViewById(R.id.category_list);
        emptyState = findViewById(R.id.empty_state);
        adapter = new CategoryAdapter(new CategoryAdapter.Listener() {
            @Override
            public void onEdit(CategoryEntity category) {
                showCategoryDialog(category);
            }

            @Override
            public void onMuteToggle(CategoryEntity category) {
                container.ioExecutor().execute(() ->
                        container.feedRepository().setCategoryMuted(category.id,
                                !category.muteNotifications));
            }

            @Override
            public void onDelete(CategoryEntity category) {
                confirmDelete(category);
            }
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        LiveData<List<CategoryEntity>> categories = container.feedRepository()
                .observeCategories();
        categories.observe(this, listItems -> {
            adapter.submitList(listItems);
            boolean empty = listItems == null || listItems.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            list.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
        findViewById(R.id.fab_add_category).setOnClickListener(view -> showCategoryDialog(null));
    }

    private void showCategoryDialog(@Nullable CategoryEntity editing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null);
        TextInputLayout layout = (TextInputLayout) view;
        layout.setHint(getString(R.string.category_name));
        TextInputEditText input = view.findViewById(R.id.dialog_input);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        if (editing != null) {
            input.setText(editing.name);
            input.setSelection(input.length());
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(editing == null ? R.string.category_new : R.string.category_rename)
                .setView(view)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    if (editing == null) {
                        container.ioExecutor().execute(() ->
                                container.feedRepository().createCategory(name, null));
                    } else {
                        container.ioExecutor().execute(() ->
                                container.feedRepository().renameCategory(editing.id, name));
                    }
                })
                .setNegativeButton(R.string.action_cancel, null);
        builder.show();
    }

    private void confirmDelete(CategoryEntity category) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.category_delete)
                .setMessage(R.string.category_delete_confirm)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        container.ioExecutor().execute(() ->
                                container.feedRepository().deleteCategory(category.id)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static class CategoryAdapter
            extends ListAdapter<CategoryEntity, CategoryAdapter.Holder> {
        interface Listener {
            void onEdit(CategoryEntity category);

            void onMuteToggle(CategoryEntity category);

            void onDelete(CategoryEntity category);
        }

        private static final DiffUtil.ItemCallback<CategoryEntity> DIFF =
                new DiffUtil.ItemCallback<CategoryEntity>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull CategoryEntity oldItem,
                                                   @NonNull CategoryEntity newItem) {
                        return oldItem.id == newItem.id;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull CategoryEntity oldItem,
                                                      @NonNull CategoryEntity newItem) {
                        return oldItem.id == newItem.id
                                && oldItem.name.equals(newItem.name)
                                && oldItem.muteNotifications == newItem.muteNotifications
                                && (oldItem.parentId == null ? newItem.parentId == null
                                : oldItem.parentId.equals(newItem.parentId));
                    }
                };

        private final Listener listener;

        CategoryAdapter(Listener listener) {
            super(DIFF);
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_category_manage, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(getItem(position), listener);
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView subtitle;
            final ImageButton mute;
            final ImageButton edit;
            final ImageButton delete;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.category_name);
                subtitle = itemView.findViewById(R.id.category_subtitle);
                mute = itemView.findViewById(R.id.category_mute);
                edit = itemView.findViewById(R.id.category_edit);
                delete = itemView.findViewById(R.id.category_delete);
            }

            void bind(CategoryEntity category, Listener listener) {
                name.setText(category.name);
                subtitle.setText(category.muteNotifications
                        ? R.string.category_mute_notifications : R.string.feed_notifications_on);
                mute.setImageResource(category.muteNotifications
                        ? R.drawable.ic_notifications_off : R.drawable.ic_notifications);
                mute.setAlpha(category.muteNotifications ? 0.55f : 1f);
                mute.setOnClickListener(view -> listener.onMuteToggle(category));
                edit.setOnClickListener(view -> listener.onEdit(category));
                delete.setOnClickListener(view -> listener.onDelete(category));
                itemView.setOnClickListener(view -> listener.onEdit(category));
            }
        }
    }
}
