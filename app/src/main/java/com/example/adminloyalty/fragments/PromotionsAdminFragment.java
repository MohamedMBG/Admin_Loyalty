package com.example.adminloyalty.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adminloyalty.R;
import com.example.adminloyalty.models.Promotion;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PromotionsAdminFragment extends Fragment {

    private RecyclerView rvPromos;
    private ExtendedFloatingActionButton fabAdd;
    private FirebaseFirestore db;
    private PromoAdapter adapter;
    private MaterialToolbar toolbar;
    private LinearLayout layoutEmptyState;

    private final String[] CRITERIA_OPTIONS = {
            "All Clients", "Age Under (Years)", "Gender (Male/Female)",
            "Inactive for (Days)", "Location (Contains)", "Points Under"
    };
    private final String[] CRITERIA_KEYS = {
            "ALL", "AGE_UNDER", "GENDER", "NO_VISIT_DAYS", "LOCATION_CONTAINS", "POINTS_UNDER"
    };

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_promotions_admin, container, false);

        rvPromos = v.findViewById(R.id.rvPromos);
        fabAdd = v.findViewById(R.id.fabAddPromo);
        toolbar = v.findViewById(R.id.toolbar);
        layoutEmptyState = v.findViewById(R.id.layoutEmptyState);

        toolbar.setNavigationOnClickListener(view -> requireActivity().onBackPressed());

        db = FirebaseFirestore.getInstance();
        setupRecyclerView();
        loadPromotions();

        fabAdd.setOnClickListener(view -> showAddDialog());

        return v;
    }

    private void setupRecyclerView() {
        adapter = new PromoAdapter();
        rvPromos.setLayoutManager(new LinearLayoutManager(getContext()));
        rvPromos.setAdapter(adapter);
    }

    private void loadPromotions() {
        // Load and Sort by Priority (Descending)
        db.collection("promotions")
                .orderBy("priority", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    List<Promotion> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots) {
                        Promotion p = doc.toObject(Promotion.class);
                        if (p != null) {
                            p.setId(doc.getId());
                            list.add(p);
                        }
                    }
                    adapter.setItems(list);

                    if (list.isEmpty()) {
                        layoutEmptyState.setVisibility(View.VISIBLE);
                        rvPromos.setVisibility(View.GONE);
                    } else {
                        layoutEmptyState.setVisibility(View.GONE);
                        rvPromos.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void showAddDialog() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_add_promo, null);
        sheet.setContentView(view);

        TextInputEditText etTitle = view.findViewById(R.id.inputPromoTitle);
        TextInputEditText etValue = view.findViewById(R.id.inputPromoValue);
        TextInputEditText etPriority = view.findViewById(R.id.inputPriority);

        // Date Inputs
        TextInputEditText etStartDate = view.findViewById(R.id.inputStartDate);
        TextInputEditText etEndDate = view.findViewById(R.id.inputEndDate);

        TextInputLayout layoutValue = view.findViewById(R.id.layoutValue);
        AutoCompleteTextView dropCriteria = view.findViewById(R.id.inputCriteria);
        MaterialButton btnSave = view.findViewById(R.id.btnSavePromo);

        // Variables to hold the selected timestamps
        final Timestamp[] startTimestamp = {null};
        final Timestamp[] endTimestamp = {null};
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        // --- Date Picker Logic ---
        etStartDate.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Start Date")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                Date date = new Date(selection);
                etStartDate.setText(dateFormat.format(date));
                startTimestamp[0] = new Timestamp(date);
            });
            datePicker.show(getParentFragmentManager(), "START_DATE");
        });

        etEndDate.setOnClickListener(v -> {
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select End Date")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                Date date = new Date(selection);
                // Set end date to end of day (23:59:59) ideally, or just use selected time
                etEndDate.setText(dateFormat.format(date));
                endTimestamp[0] = new Timestamp(date);
            });
            datePicker.show(getParentFragmentManager(), "END_DATE");
        });
        // -------------------------

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, CRITERIA_OPTIONS);
        dropCriteria.setAdapter(adapter);
        dropCriteria.setText(CRITERIA_OPTIONS[0], false);

        dropCriteria.setOnItemClickListener((parent, view1, position, id) -> {
            String selected = CRITERIA_OPTIONS[position];
            if (selected.startsWith("All")) {
                layoutValue.setVisibility(View.GONE);
            } else {
                layoutValue.setVisibility(View.VISIBLE);
                if (selected.startsWith("Age") || selected.startsWith("Inactive") || selected.startsWith("Points")) {
                    layoutValue.setHint("Enter Number");
                    etValue.setInputType(InputType.TYPE_CLASS_NUMBER);
                } else {
                    layoutValue.setHint("Value");
                    etValue.setInputType(InputType.TYPE_CLASS_TEXT);
                }
            }
        });

        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String uiSelection = dropCriteria.getText().toString();
            String value = etValue.getText().toString().trim();
            String priorityStr = etPriority.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(getContext(), "Title required", Toast.LENGTH_SHORT).show();
                return;
            }

            int priority = 1;
            if (!priorityStr.isEmpty()) {
                priority = Integer.parseInt(priorityStr);
            }

            String dbKey = "ALL";
            for (int i = 0; i < CRITERIA_OPTIONS.length; i++) {
                if (CRITERIA_OPTIONS[i].equals(uiSelection)) {
                    dbKey = CRITERIA_KEYS[i];
                    break;
                }
            }

            if (!dbKey.equals("ALL") && value.isEmpty()) {
                Toast.makeText(getContext(), "Value required", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save with Priority and Date Range
            Promotion p = new Promotion(title, dbKey, value, true, priority, startTimestamp[0], endTimestamp[0]);
            db.collection("promotions").add(p);
            sheet.dismiss();
            Toast.makeText(getContext(), "Promo Live!", Toast.LENGTH_SHORT).show();
        });

        sheet.show();
    }

    class PromoAdapter extends RecyclerView.Adapter<PromoAdapter.ViewHolder> {
        List<Promotion> list = new ArrayList<>();

        void setItems(List<Promotion> list) {
            this.list = list;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_promo_admin, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Promotion p = list.get(position);
            holder.tvTitle.setText(p.getTitle());

            String ruleText = "All Clients";
            String c = p.getCriteria();
            String v = p.getValue();

            if ("AGE_UNDER".equals(c)) ruleText = "Under " + v + " years";
            else if ("GENDER".equals(c)) ruleText = "Gender: " + v;
            else if ("NO_VISIT_DAYS".equals(c)) ruleText = "Absent > " + v + " days";
            else if ("LOCATION_CONTAINS".equals(c)) ruleText = "Loc: " + v;
            else if ("POINTS_UNDER".equals(c)) ruleText = "Points < " + v;

            // Append priority and Date info if available
            StringBuilder details = new StringBuilder(ruleText);
            details.append(" • P: ").append(p.getPriority());

            if (p.getStartDate() != null && p.getEndDate() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
                details.append(" • ").append(sdf.format(p.getStartDate().toDate()))
                        .append("-").append(sdf.format(p.getEndDate().toDate()));
            } else if (p.getEndDate() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
                details.append(" • Ends ").append(sdf.format(p.getEndDate().toDate()));
            }

            holder.tvRule.setText(details.toString());

            holder.swActive.setChecked(p.isActive());

            holder.swActive.setOnCheckedChangeListener((btn, isChecked) ->
                    db.collection("promotions").document(p.getId()).update("active", isChecked));

            holder.btnDelete.setOnClickListener(vClick ->
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Delete?")
                            .setPositiveButton("Yes", (d, w) -> db.collection("promotions").document(p.getId()).delete())
                            .setNegativeButton("No", null)
                            .show());
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvRule;
            SwitchMaterial swActive;
            MaterialButton btnDelete;
            ViewHolder(View v) {
                super(v);
                tvTitle = v.findViewById(R.id.tvPromoTitle);
                tvRule = v.findViewById(R.id.tvPromoRule);
                swActive = v.findViewById(R.id.switchActive);
                btnDelete = v.findViewById(R.id.btnDelete);
            }
        }
    }
}