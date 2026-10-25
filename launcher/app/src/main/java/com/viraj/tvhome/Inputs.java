package com.viraj.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class Inputs {
    static final class Source {
        final String label;
        final boolean connected;
        final Intent intent;

        Source(String label, boolean connected, Intent intent) {
            this.label = label;
            this.connected = connected;
            this.intent = intent;
        }
    }

    private Inputs() {
    }

    static List<Source> load(Context context) {
        TvInputManager tim = context.getSystemService(TvInputManager.class);
        String tvApp = tvApp(context);
        List<Source> hdmi = new ArrayList<>();
        List<Source> av = new ArrayList<>();
        Source liveTv = null;
        for (TvInputInfo info : tim.getTvInputList()) {
            // The framework gives inputs backed by real hardware ids ending in "/HW<n>"; app-provided
            // inputs (Google TV, VLC, Zee5 previews) also report TYPE_TUNER and must be skipped.
            if (!info.getId().contains("/HW") || info.getParentId() != null || info.isHidden(context)) {
                continue;
            }
            boolean connected = tim.getInputState(info.getId()) != TvInputManager.INPUT_STATE_DISCONNECTED;
            if (info.getType() == TvInputInfo.TYPE_TUNER) {
                if (liveTv == null) {
                    Uri channels = TvContract.buildChannelsUriForInput(info.getId());
                    liveTv = new Source(context.getString(R.string.live_tv), true,
                            view(channels, TvContract.Channels.CONTENT_TYPE, tvApp));
                }
            } else {
                Uri uri = TvContract.buildChannelUriForPassthroughInput(info.getId());
                Source source = new Source(label(context, info), connected,
                        view(uri, TvContract.Channels.CONTENT_ITEM_TYPE, tvApp));
                (info.getType() == TvInputInfo.TYPE_HDMI ? hdmi : av).add(source);
            }
        }
        Comparator<Source> byLabel = Comparator.comparing(s -> s.label);
        hdmi.sort(byLabel);
        av.sort(byLabel);
        List<Source> all = new ArrayList<>(hdmi);
        all.addAll(av);
        if (liveTv != null) {
            all.add(liveTv);
        }
        return all;
    }

    private static String label(Context context, TvInputInfo info) {
        CharSequence custom = info.loadCustomLabel(context);
        return (custom != null && custom.length() > 0 ? custom : info.loadLabel(context)).toString();
    }

    private static String tvApp(Context context) {
        for (String pkg : context.getResources().getStringArray(R.array.tv_app_packages)) {
            try {
                context.getPackageManager().getApplicationInfo(pkg, 0);
                return pkg;
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return null;
    }

    // Some apps (RetroPal on the BRAVIA) claim every VIEW intent, so input switches name the TV app.
    private static Intent view(Uri uri, String type, String tvApp) {
        return new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).setPackage(tvApp);
    }
}
