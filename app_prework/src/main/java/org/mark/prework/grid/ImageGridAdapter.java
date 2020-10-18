package org.mark.prework.grid;


import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import org.mark.prework.R;
import org.mark.prework.TfFileUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


public class ImageGridAdapter extends RecyclerView.Adapter<ImageGridAdapter.ViewHolderImage> {

    public interface OnItemEvent {
        void onItemClick(TfFileUtils.ImageAcc record, RecyclerView.ViewHolder viewHolder);
    }

    private SparseBooleanArray mSelectedPositions = new SparseBooleanArray();

    private final List<TfFileUtils.ImageAcc> mData = new ArrayList<>();
    private final ImageGridAdapter.OnItemEvent mItemEvent;

    public ImageGridAdapter(OnItemEvent onItemEvent) {
        mItemEvent = onItemEvent;
    }

    @NonNull
    @Override
    public ViewHolderImage onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ImageGridAdapter.ViewHolderImage(
                LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grid, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolderImage holder, final int position) {
        final TfFileUtils.ImageAcc acc = getItem(position);

        holder.linearLayout.setVisibility(isItemChecked(position) ? View.VISIBLE : View.INVISIBLE);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mItemEvent.onItemClick(acc, holder);

                if (isItemChecked(position)) {
                    setItemChecked(position, false);
                } else {
                    setItemChecked(position, true);
                }


                notifyItemChanged(position);
            }
        });

        holder.textName.setText(acc.getInfo());

        Glide.with(holder.imageAvatar.getContext()).load(acc.getPath()).into(holder.imageAvatar);

    }

    private void setItemChecked(int position, boolean isChecked) {
        mSelectedPositions.put(position, isChecked);
    }

    private boolean isItemChecked(int position) {
        return mSelectedPositions.get(position);
    }


    public TfFileUtils.ImageAcc getItem(int position) {
        return mData.get(position);
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public void setData(List<TfFileUtils.ImageAcc> data) {
        mSelectedPositions.clear();
        mData.clear();
        mData.addAll(new ArrayList<>(data));
        notifyDataSetChanged();
    }


    public void selectAll() {
        for (int i = 0; i < mData.size(); i++) {
            setItemChecked(i, true);
        }
        notifyDataSetChanged();
    }

    public List<TfFileUtils.ImageAcc> getChoicePaths() {
        List<Integer> positionList = new ArrayList<>();

        for (int i = 0; i < mSelectedPositions.size(); i++) {
            int position = mSelectedPositions.keyAt(i);
            if (isItemChecked(position)) {
                setItemChecked(position, false);
                positionList.add(position);
            }
        }

        Collections.sort(positionList, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                if (o1 < o2) {
                    return 1;
                } else if (o1 > o2) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });


        List<TfFileUtils.ImageAcc> result = new ArrayList<>();

        for (Integer integer : positionList) {
            TfFileUtils.ImageAcc acc = mData.remove(integer.intValue());
            result.add(acc);
        }


        return result;
    }


    class ViewHolderImage extends RecyclerView.ViewHolder {
        protected final TextView textName;
        protected final ImageView imageAvatar;
        public View linearLayout;

        public ViewHolderImage(View itemView) {
            super(itemView);
            linearLayout = itemView.findViewById(R.id.layout);
            textName = itemView.findViewById(R.id.text);
            imageAvatar = itemView.findViewById(R.id.image);
        }
    }


}
