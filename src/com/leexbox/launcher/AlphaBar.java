package com.leexbox.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

final class AlphaBar extends View {
    interface Listener {
        void onLetter(char letter);

        void onRelease();
    }

    private static final char[] LETTERS = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int dim;
    private int highlight;
    private int active = -1;
    private Listener listener;

    AlphaBar(Context c) {
        super(c);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    void setColors(int dim, int highlight) {
        this.dim = dim;
        this.highlight = highlight;
        invalidate();
    }

    void setListener(Listener l) {
        listener = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float step = (float) getHeight() / LETTERS.length;
        paint.setTextSize(Math.min(Ui.dp(getContext(), 11), step * 0.8f));
        for (int i = 0; i < LETTERS.length; i++) {
            paint.setColor(i == active ? highlight : dim);
            float y = step * i + step / 2f - (paint.descent() + paint.ascent()) / 2f;
            canvas.drawText(String.valueOf(LETTERS[i]), getWidth() / 2f, y, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                int i = (int) (e.getY() / Math.max(1, getHeight()) * LETTERS.length);
                i = Math.max(0, Math.min(LETTERS.length - 1, i));
                if (i != active) {
                    active = i;
                    invalidate();
                    if (listener != null) listener.onLetter(LETTERS[i]);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                active = -1;
                invalidate();
                if (listener != null) listener.onRelease();
                return true;
            default:
                return true;
        }
    }
}
