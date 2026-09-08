package com.limelight.ui.gamemenu;

import android.view.View;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

/** Same side-page navigation and sizing as bitrate/resolution settings. */
public class GameDisplayScaleFragment extends BaseGameMenuDialog {
    private View boundView;
    @Override public int getLayoutRes() { return R.layout.dialog_game_menu_display_scale; }
    @Override public String getFragmentTag() { return "game_display_scale"; }
    @Override public void bindView(View view) {
        super.bindView(view);
        boundView = view;
        view.findViewById(R.id.ibtn_back).setOnClickListener(v -> dismiss());
        if (getActivity() instanceof Game) ((Game)getActivity()).bindHostScalePage(view);
    }
    @Override public void onDestroyView() {
        if (getActivity() instanceof Game) ((Game)getActivity()).unbindHostScalePage(boundView);
        boundView = null;
        super.onDestroyView();
    }
}
