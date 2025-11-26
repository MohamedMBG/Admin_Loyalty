package com.example.adminloyalty.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.adminloyalty.R;
import com.example.adminloyalty.models.Member;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MembersManagementFragment extends Fragment {

    // Firestore
    private FirebaseFirestore db;
    private ListenerRegistration membersListener;

    // UI
    private TextInputEditText etSearch;
    private Chip chipActivityActive;
    private TextView tvMaleCount;    // used as Verified %
    private TextView tvFemaleCount;  // used as Unverified %
    private TextView tvMemberCount;
    private RecyclerView rvTopCustomers;
    private RecyclerView rvMembers;
    private LinearLayout emptyState;

    // Data
    private final List<Member> allMembers = new ArrayList<>();
    private final List<Member> filteredMembers = new ArrayList<>();
    private final List<Member> topCustomers = new ArrayList<>();

    // Adapters
    private MembersAdapter membersAdapter;
    private MembersAdapter topCustomersAdapter;

    // Filters
    private boolean filterVerifiedOnly = false;
    private String currentQuery = "";

    // Age buckets
    private int age_18_24 = 0;
    private int age_25_34 = 0;
    private int age_35_44 = 0;
    private int age_45_plus = 0;

    public MembersManagementFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_members_management, container, false);

        db = FirebaseFirestore.getInstance();

        initViews(view);
        setupRecyclerViews();
        setupSearch();
        setupFilters();
        listenForMembers();

        return view;
    }

    // ================== INIT ==================

    private void initViews(View view) {
        etSearch = view.findViewById(R.id.et_search);
        chipActivityActive = view.findViewById(R.id.chip_activity_active);

        tvMaleCount = view.findViewById(R.id.tv_male_count);
        tvFemaleCount = view.findViewById(R.id.tv_female_count);
        tvMemberCount = view.findViewById(R.id.tv_member_count);

        rvTopCustomers = view.findViewById(R.id.rv_top_customers);
        rvMembers = view.findViewById(R.id.rv_members);

        emptyState = view.findViewById(R.id.empty_state);

        chipActivityActive.setChecked(false);
    }

    private void setupRecyclerViews() {
        rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTopCustomers.setLayoutManager(new LinearLayoutManager(requireContext()));

        membersAdapter = new MembersAdapter(filteredMembers);
        topCustomersAdapter = new MembersAdapter(topCustomers);

        rvMembers.setAdapter(membersAdapter);
        rvTopCustomers.setAdapter(topCustomersAdapter);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = (s != null) ? s.toString() : "";
                applyFilters();
            }

            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void setupFilters() {
        chipActivityActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // treat "Activity Active" as "Verified users"
            filterVerifiedOnly = isChecked;
            applyFilters();
        });
    }

    // ================== FIRESTORE LISTENER ==================

    private void listenForMembers() {
        membersListener = db.collection("users")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot snapshots,
                                        @Nullable FirebaseFirestoreException e) {
                        if (!isAdded()) return;
                        if (e != null || snapshots == null) {
                            return;
                        }

                        allMembers.clear();
                        resetAgeBuckets();

                        for (QueryDocumentSnapshot doc : snapshots) {
                            Member m = doc.toObject(Member.class);
                            m.setId(doc.getId());

                            int age = getAgeFromBirthday(m.getBirthday());
                            if (age >= 18 && age <= 24) age_18_24++;
                            else if (age >= 25 && age <= 34) age_25_34++;
                            else if (age >= 35 && age <= 44) age_35_44++;
                            else if (age >= 45) age_45_plus++;

                            allMembers.add(m);
                        }

                        updateStatisticsUI();
                        buildTopCustomers();
                        applyFilters();
                        updateAgeChart();
                    }
                });
    }

    private void resetAgeBuckets() {
        age_18_24 = 0;
        age_25_34 = 0;
        age_35_44 = 0;
        age_45_plus = 0;
    }

    // ================== STATS ==================

    private void updateStatisticsUI() {
        int total = allMembers.size();
        tvMemberCount.setText(String.valueOf(total));

        int verified = 0;
        int unverified = 0;

        for (Member m : allMembers) {
            android.util.Log.d("MembersDebug",
                    m.getEmail() + " -> isVerified=" + m.isVerified());
            if (m.isVerified()){
                verified++;
            }
            else if(!m.isVerified()) {unverified++;}
        }


        if (total == 0) {
            tvMaleCount.setText("Verified: 0%");
            tvFemaleCount.setText("Unverified: 0%");
            updateGenderChart(0, 0);
            return;
        }

        int verifiedPercent = Math.round((verified * 100f) / total);
        int unverifiedPercent = Math.round((unverified * 100f) / total);

        tvMaleCount.setText("Verified: " + verifiedPercent + "%");
        tvFemaleCount.setText("Unverified: " + unverifiedPercent + "%");

        updateGenderChart(verifiedPercent, unverifiedPercent);
    }

    private void buildTopCustomers() {
        topCustomers.clear();

        List<Member> sorted = new ArrayList<>(allMembers);
        Collections.sort(sorted, new Comparator<Member>() {
            @Override
            public int compare(Member o1, Member o2) {
                return Long.compare(o2.getPoints(), o1.getPoints()); // desc by points
            }
        });

        int limit = Math.min(10, sorted.size());
        for (int i = 0; i < limit; i++) {
            topCustomers.add(sorted.get(i));
        }

        topCustomersAdapter.notifyDataSetChanged();
    }

    private void applyFilters() {
        filteredMembers.clear();
        String q = currentQuery.toLowerCase().trim();

        for (Member m : allMembers) {

            if (filterVerifiedOnly && !m.isVerified()) {
                continue;
            }

            if (!TextUtils.isEmpty(q)) {
                String name = m.getFullName().toLowerCase();
                String email = m.getEmail().toLowerCase();

                if (!name.contains(q) && !email.contains(q)) {
                    continue;
                }
            }

            filteredMembers.add(m);
        }

        membersAdapter.notifyDataSetChanged();
        toggleEmptyState();
    }

    private void toggleEmptyState() {
        if (filteredMembers.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            rvMembers.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvMembers.setVisibility(View.VISIBLE);
        }
    }

    // ================== CHARTS ==================

    private void updateGenderChart(int verifiedPercent, int unverifiedPercent) {
        if (!isAdded()) return;
        View root = getView();
        if (root == null) return;

        FrameLayout container = root.findViewById(R.id.chart_gender);
        container.removeAllViews();

        PieChart pieChart = new PieChart(requireContext());
        container.addView(pieChart,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));

        stylePieChart(pieChart);

        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(verifiedPercent, "Verified"));
        entries.add(new PieEntry(unverifiedPercent, "Unverified"));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setSliceSpace(3f);
        dataSet.setSelectionShift(6f);
        dataSet.setColors(
                Color.parseColor("#4CAF50"),  // verified
                Color.parseColor("#FF5722")   // unverified
        );
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(11f);

        PieData data = new PieData(dataSet);
        pieChart.setUsePercentValues(true);
        data.setValueFormatter(new PercentFormatter(pieChart));

        pieChart.setData(data);
        pieChart.highlightValues(null);
        pieChart.invalidate();
    }

    private void stylePieChart(PieChart chart) {
        chart.setDrawHoleEnabled(true);
        chart.setHoleRadius(65f);
        chart.setTransparentCircleRadius(70f);
        chart.setHoleColor(Color.TRANSPARENT);
        chart.setTransparentCircleColor(Color.WHITE);
        chart.setTransparentCircleAlpha(80);

        chart.setRotationEnabled(false);
        chart.setDrawEntryLabels(false);

        Description desc = new Description();
        desc.setText("");
        chart.setDescription(desc);

        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setXEntrySpace(12f);
        legend.setYEntrySpace(4f);
        legend.setTextSize(11f);
    }

    private void updateAgeChart() {
        if (!isAdded()) return;
        View root = getView();
        if (root == null) return;

        FrameLayout container = root.findViewById(R.id.chart_age);
        container.removeAllViews();

        BarChart barChart = new BarChart(requireContext());
        container.addView(barChart,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));

        styleBarChart(barChart);

        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, age_18_24));
        entries.add(new BarEntry(1, age_25_34));
        entries.add(new BarEntry(2, age_35_44));
        entries.add(new BarEntry(3, age_45_plus));

        BarDataSet dataSet = new BarDataSet(entries, "Age groups");
        dataSet.setColor(Color.parseColor("#3F51B5"));
        dataSet.setValueTextSize(11f);
        dataSet.setValueTextColor(Color.DKGRAY);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);

        barChart.setData(data);
        barChart.invalidate();
    }

    private void styleBarChart(BarChart chart) {
        chart.setDrawGridBackground(false);
        chart.setDrawBarShadow(false);
        chart.setDrawBorders(false);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);

        Description desc = new Description();
        desc.setText("");
        chart.setDescription(desc);

        chart.getAxisRight().setEnabled(false);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextSize(11f);
        leftAxis.setTextColor(Color.DKGRAY);
        leftAxis.setGridColor(Color.parseColor("#EEEEEE"));

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(11f);
        xAxis.setTextColor(Color.DKGRAY);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(
                new String[]{"18–24", "25–34", "35–44", "45+"}
        ));

        // move content a bit from edges (so labels on right are visible)
        chart.setExtraRightOffset(16f);
        chart.setExtraLeftOffset(8f);

        Legend legend = chart.getLegend();
        legend.setEnabled(false);
    }

    // ================== UTILS ==================

    private int getAgeFromBirthday(String birthday) {
        if (TextUtils.isEmpty(birthday)) return -1;

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date birthDate = sdf.parse(birthday);
            if (birthDate == null) return -1;

            Calendar dob = Calendar.getInstance();
            dob.setTime(birthDate);

            Calendar today = Calendar.getInstance();

            int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
                age--;
            }
            return age;
        } catch (ParseException e) {
            return -1;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (membersListener != null) {
            membersListener.remove();
        }
    }

    // ================== ADAPTER ==================

    private static class MembersAdapter extends RecyclerView.Adapter<MembersAdapter.MemberViewHolder> {

        private final List<Member> data;

        MembersAdapter(List<Member> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_member, parent, false);
            return new MemberViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
            Member m = data.get(position);

            holder.tvName.setText(m.getFullName());
            holder.tvEmail.setText(m.getEmail());
            holder.tvPoints.setText(m.getPoints()+ "");
            holder.tvVisits.setText("" + m.getVisits());
            holder.tvStatus.setText(m.isVerified() ? "Verified" : "Not verified");
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class MemberViewHolder extends RecyclerView.ViewHolder {

            TextView tvName;
            TextView tvEmail;
            TextView tvPoints;
            TextView tvVisits;
            TextView tvStatus;

            MemberViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_member_name);
                tvEmail = itemView.findViewById(R.id.tv_member_email);
                tvPoints = itemView.findViewById(R.id.tv_member_points);
                tvVisits = itemView.findViewById(R.id.tv_member_visits);
                tvStatus = itemView.findViewById(R.id.tv_member_status);
            }
        }
    }
}
