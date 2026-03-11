package com.studymind.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.studymind.app.data.NoteRepository;
import com.studymind.app.data.StudyNote;

import java.util.ArrayList;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private View emptyText;
    private NoteAdapter adapter;
    private NoteRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recycler = findViewById(R.id.recycler);
        emptyText = findViewById(R.id.emptyText);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });

        TextInputEditText searchInput = findViewById(R.id.searchInput);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchNotes(s != null ? s.toString().trim() : "");
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NoteAdapter(new ArrayList<>(), note -> {
            Intent i = new Intent(this, NoteDetailActivity.class);
            i.putExtra("id", note.id);
            startActivity(i);
        });
        recycler.setAdapter(adapter);
        repository = new NoteRepository(this);

        SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(() -> {
            loadNotes();
            swipeRefresh.setRefreshing(false);
        });

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder target) { return false; }
            @Override
            public void onSwiped(RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || pos < 0) return;
                StudyNote n = adapter.getNoteAt(pos);
                if (n != null) {
                    new AlertDialog.Builder(HistoryActivity.this)
                            .setTitle("Delete note")
                            .setMessage("Delete \"" + n.title + "\"?")
                            .setPositiveButton("Delete", (d, w) -> {
                                repository.deleteById(n.id, () -> runOnUiThread(() -> {
                                    adapter.removeAt(pos);
                                    if (adapter.getItemCount() == 0) emptyText.setVisibility(View.VISIBLE);
                                }));
                            })
                            .setNegativeButton("Cancel", (d, w) -> adapter.notifyItemChanged(pos))
                            .setOnCancelListener(d -> adapter.notifyItemChanged(pos))
                            .show();
                } else {
                    adapter.notifyItemChanged(pos);
                }
            }
        });
        helper.attachToRecyclerView(recycler);

        loadNotes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotes();
    }

    private void loadNotes() {
        TextInputEditText searchInput = findViewById(R.id.searchInput);
        String q = searchInput != null && searchInput.getText() != null ? searchInput.getText().toString().trim() : "";
        searchNotes(q);
    }

    private void searchNotes(String query) {
        if (query.isEmpty()) {
            repository.getAll(notes -> runOnUiThread(() -> {
                if (isFinishing()) return;
                adapter.setNotes(notes);
                if (emptyText != null) emptyText.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
            }));
        } else {
            repository.search(query, notes -> runOnUiThread(() -> {
                if (isFinishing()) return;
                adapter.setNotes(notes);
                if (emptyText != null) emptyText.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
            }));
        }
    }
}
