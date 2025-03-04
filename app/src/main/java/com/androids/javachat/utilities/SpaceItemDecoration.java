package com.androids.javachat.utilities;

import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public class SpaceItemDecoration extends RecyclerView.ItemDecoration {
    private final int spacing; // Khoảng cách giữa các item (pixel)

    public SpaceItemDecoration(int spacing) {
        this.spacing = spacing;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        outRect.bottom = spacing; // Khoảng cách phía dưới mỗi item
        outRect.top = parent.getChildAdapterPosition(view) == 0 ? spacing : 0; // Khoảng cách trên chỉ cho item đầu
    }
}
