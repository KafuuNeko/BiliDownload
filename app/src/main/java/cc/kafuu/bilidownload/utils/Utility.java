package cc.kafuu.bilidownload.utils;

import android.annotation.SuppressLint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.recyclerview.widget.RecyclerView;

import java.io.File;

public class Utility {
    public static void setListViewHeightBasedOnChildren(ListView listView) {
        //获取ListView对应的Adapter
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null) {
            // pre-condition
            return;
        }

        int totalHeight = 0;
        for (int i = 0, len = listAdapter.getCount(); i < len; i++) {   //listAdapter.getCount()返回数据项的数目
            View listItem = listAdapter.getView(i, null, listView);
            listItem.measure(0, 0);  //计算子项View 的宽高
            totalHeight += listItem.getMeasuredHeight();  //统计所有子项的总高度
        }

        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
        //listView.getDividerHeight()获取子项间分隔符占用的高度
        //params.height最后得到整个ListView完整显示需要的高度
        listView.setLayoutParams(params);
    }

    public static void setRecyclerViewNested(RecyclerView recyclerView) {
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.setHasFixedSize(true);
        recyclerView.setFocusable(false);
    }

    @SuppressLint("DefaultLocale")
    public static String getFileSizeString(long size) {
        double value = size;
        if (Math.floor(value / 1024) == 0) {
            return size + " b";
        }

        value /= 1024;
        if (Math.floor(value / 1024) == 0) {
            return String.format("%.2f KB", value);
        }

        value /= 1024;
        if (Math.floor(value / 1024) == 0) {
            return String.format("%.2f MB", value);
        }

        value /= 1024;
        return String.format("%.2f GB", value);
    }

}
