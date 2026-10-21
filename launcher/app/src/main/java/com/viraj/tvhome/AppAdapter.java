package com.viraj.tvhome;

import android.content.res.Resources;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {
    private final List<AppEntry> apps = new ArrayList<>();
    private final Typeface labelFont;

    AppAdapter(Typeface labelFont) {
        this.labelFont = labelFont;
    }

    void submit(List<AppEntry> list) {
        apps.clear();
        apps.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tile, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        bind(holder.itemView, apps.get(position), labelFont);
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static void bind(View tile, AppEntry app, Typeface labelFont) {
        ImageView card = tile.findViewById(R.id.card);
        TextView label = tile.findViewById(R.id.label);
        tile.animate().cancel();
        label.animate().cancel();
        tile.setScaleX(1f);
        tile.setScaleY(1f);
        tile.setAlpha(1f);
        Resources res = tile.getResources();
        card.setImageDrawable(new CardDrawable(app, res.getDimension(R.dimen.tile_radius),
                MainActivity.FOCUS_SCALE, res.getDimensionPixelSize(R.dimen.tile_icon_padding)));
        label.setTypeface(labelFont);
        label.setText(app.label);
        label.setAlpha(0f);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        Holder(View itemView) {
            super(itemView);
        }
    }
}
