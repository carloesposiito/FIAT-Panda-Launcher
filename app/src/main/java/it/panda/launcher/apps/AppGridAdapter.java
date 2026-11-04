package it.panda.launcher.apps;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

import it.panda.launcher.R;

/** Riempie la griglia delle app a sinistra. */
public final class AppGridAdapter extends BaseAdapter {

    private final List<AppEntry> apps = new ArrayList<>();

    public void setApps(List<AppEntry> list) {
        apps.clear();
        apps.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public AppEntry getItem(int position) {
        return apps.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        }
        AppEntry app = apps.get(position);
        ((ImageView) v.findViewById(R.id.app_icon)).setImageDrawable(app.icon);
        v.setContentDescription(app.label);
        return v;
    }
}
