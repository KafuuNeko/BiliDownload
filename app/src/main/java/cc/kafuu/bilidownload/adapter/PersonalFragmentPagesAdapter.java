package cc.kafuu.bilidownload.adapter;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.List;


public class PersonalFragmentPagesAdapter extends FragmentStateAdapter {
    private final List<Pair<CharSequence, Fragment>> mFragments;

    public PersonalFragmentPagesAdapter(@NonNull FragmentActivity fragmentActivity, List<Pair<CharSequence, Fragment>> fragments) {
        super(fragmentActivity);
        mFragments = fragments;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return mFragments.get(position).second;
    }

    @Override
    public int getItemCount() {
        return mFragments.size();
    }
}
