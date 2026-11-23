package br.com.falai;

import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class SwipeToReply extends RecyclerView.SimpleOnItemTouchListener {
    private final MessageAdapter adapter;
    private final Predicate<DocumentSnapshot> canReply;
    private final Consumer<DocumentSnapshot> reply;
    private final int slop, threshold, maxOffset;
    private MessageAdapter.Holder holder;
    private String messageId;
    private float startX, startY;
    private boolean crossedThreshold;

    SwipeToReply(RecyclerView list, MessageAdapter adapter, Predicate<DocumentSnapshot> canReply,
            Consumer<DocumentSnapshot> reply) {
        this.adapter = adapter;
        this.canReply = canReply;
        this.reply = reply;
        slop = ViewConfiguration.get(list.getContext()).getScaledTouchSlop();
        threshold = Ui.dp(list.getContext(), 56);
        maxOffset = Ui.dp(list.getContext(), 76);
    }

    @Override public boolean onInterceptTouchEvent(RecyclerView list, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                reset();
                View row = list.findChildViewUnder(event.getX(), event.getY());
                if (row == null) return false;
                MessageAdapter.Holder candidate = (MessageAdapter.Holder) list.getChildViewHolder(row);
                DocumentSnapshot message = adapter.at(candidate.getBindingAdapterPosition());
                if (message == null || !canReply.test(message)) return false;
                holder = candidate;
                messageId = message.getId();
                startX = event.getX();
                startY = event.getY();
                return false;
            case MotionEvent.ACTION_MOVE:
                if (holder == null) return false;
                float dx = event.getX() - startX;
                float dy = Math.abs(event.getY() - startY);
                if (dx < -slop || (dy > slop && dy >= dx)) {
                    reset();
                    return false;
                }
                if (dx > slop && dx > dy) {
                    updateOffset(list, dx);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                reset();
                return false;
            default:
                return false;
        }
    }

    @Override public void onTouchEvent(RecyclerView list, MotionEvent event) {
        if (holder == null) return;
        float dx = event.getX() - startX;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                updateOffset(list, dx);
                break;
            case MotionEvent.ACTION_UP:
                DocumentSnapshot message = adapter.at(holder.getBindingAdapterPosition());
                boolean selected = dx >= threshold && message != null
                        && messageId.equals(message.getId()) && canReply.test(message);
                reset();
                if (selected) reply.accept(message);
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_DOWN:
                reset();
                break;
            default:
                break;
        }
    }

    @Override public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) reset();
    }

    private void updateOffset(RecyclerView list, float dx) {
        DocumentSnapshot message = adapter.at(holder.getBindingAdapterPosition());
        if (message == null || !messageId.equals(message.getId()) || !canReply.test(message)) {
            reset();
            return;
        }
        holder.setReplyOffset(Math.max(0, Math.min(dx, maxOffset)), false);
        if (!crossedThreshold && dx >= threshold) {
            list.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            crossedThreshold = true;
        }
    }

    private void reset() {
        if (holder != null) holder.setReplyOffset(0, true);
        holder = null;
        messageId = null;
        crossedThreshold = false;
    }
}
