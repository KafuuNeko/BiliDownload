package cc.kafuu.bilidownload.adapter;

import android.content.Context;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import cc.kafuu.bilidownload.R;

public class OperatorListAdapter extends RecyclerView.Adapter<OperatorListAdapter.InnerHolder> {
    private final Context mContext;
    private final List<Pair<CharSequence, View.OnClickListener>> mOperators;

    public OperatorListAdapter(Context context) {
        mOperators = new ArrayList<>();
        mContext = context;
    }

    public void addItem(CharSequence name, View.OnClickListener listener) {
        mOperators.add(new Pair<>(name, listener));
    }

    public void addItem(int resId, View.OnClickListener listener) {
        mOperators.add(new Pair<>(mContext.getString(resId), listener));
    }

    @NonNull
    @Override
    public OperatorListAdapter.InnerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new InnerHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_operator, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull OperatorListAdapter.InnerHolder holder, int position) {
        holder.bind(mOperators.get(position));
    }

    @Override
    public int getItemCount() {
        return mOperators.size();
    }

    public static class InnerHolder extends RecyclerView.ViewHolder {
        Pair<CharSequence, View.OnClickListener> mItem;

        private final CardView mOperatorItem;
        private final TextView mOperatorName;

        public InnerHolder(@NonNull View itemView) {
            super(itemView);
            mOperatorName = itemView.findViewById(R.id.operatorName);
            mOperatorItem = itemView.findViewById(R.id.operatorItem);
        }

        public void bind(Pair<CharSequence, View.OnClickListener> item) {
            mItem = item;

            mOperatorName.setText(item.first);
            mOperatorItem.setOnClickListener(item.second);
        }
    }
}
